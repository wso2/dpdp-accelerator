/**
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 * <p>
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 *     http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.dpdp.accelerator.event.notifications.service.dispatch;

import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.common.util.DatabaseUtils;
import org.wso2.dpdp.accelerator.common.util.LogSanitizer;
import org.wso2.dpdp.accelerator.common.util.HTTPClientUtils;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.DeliveryStatus;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDelivery;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDeliveryDispatchContext;

import java.net.http.HttpClient;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Batch driver for the webhook dispatch loop. One tick:
 *
 * <ol>
 * <li>Reads up to {@code delivery_worker_batch_size} pending dispatch contexts
 * from the
 * DAO. Each context is a single-row join of {@code WEBHOOK_DELIVERY}, the
 * matching
 * {@code SUBSCRIPTION} (callback URL + shared secret), and the matching
 * {@code EVENT}
 * payload, so the worker can hand one object straight to
 * {@link WebhookDeliveryTask}
 * without further DAO calls.</li>
 * <li>Submits each context to the shared executor while it is still pending.</li>
 * <li>When the executor starts that unit of work, atomically flips the row to
 * {@code in_flight} via {@link DeliveryDAO#claimWebhookDelivery(String)}. Claimed
 * rows therefore represent running work, not work waiting in an executor queue.</li>
 * <li>Also drains stuck {@code in_flight} rows whose {@code UPDATED_AT} is
 * older than the
 * configured stuck threshold so a crashed worker does not permanently block
 * delivery.</li>
 * </ol>
 *
 * <p>
 * Owned by {@code DeliveryRecoveryService}; not an OSGi component itself
 * because its
 * lifecycle is tied to the same {@link Executor} that powers the pending
 * subscription recovery task.
 * </p>
 */
public class WebhookDeliveryWorker implements Runnable {

    public enum ManualRetrySubmissionResult {
        ACCEPTED,
        NOT_FOUND,
        NOT_ELIGIBLE
    }

    private static final Log LOG = LogFactory.getLog(WebhookDeliveryWorker.class);

    private final DeliveryDAO deliveryDAO;
    private final Executor executor;
    private final HttpClient httpClient;
    private final DPDPConfigurationService configurationService;

    public WebhookDeliveryWorker(DeliveryDAO deliveryDAO, Executor executor) {
        this(deliveryDAO, executor, defaultHttpClient(), null);
    }

    public WebhookDeliveryWorker(DeliveryDAO deliveryDAO, Executor executor, HttpClient httpClient) {
        this(deliveryDAO, executor, httpClient, null);
    }

    public WebhookDeliveryWorker(DeliveryDAO deliveryDAO, Executor executor,
            DPDPConfigurationService configurationService) {
        this(deliveryDAO, executor, defaultHttpClient(), configurationService);
    }

    public WebhookDeliveryWorker(DeliveryDAO deliveryDAO, Executor executor, HttpClient httpClient,
            DPDPConfigurationService configurationService) {
        this.deliveryDAO = deliveryDAO;
        this.executor = executor;
        this.httpClient = httpClient;
        this.configurationService = configurationService;
    }

    private static HttpClient defaultHttpClient() {
        return HTTPClientUtils.getHttpClient();
    }

    @Override
    public void run() {
        try {
            runTick();
        } catch (Exception e) {
            LOG.error("Webhook delivery worker tick failed: " + LogSanitizer.sanitize(e.getMessage()), e);
        }
    }

    /**
     * Visible for tests so they can drive the loop deterministically without
     * scheduling.
     * Returns {@code int[submitted, reclaimed]} so tests can verify both the first
     * pass and the stuck-recovery pass fired.
     */
    public int[] runTick() {
        int batchSize = getConfiguration().getEventNotificationDeliveryWorkerBatchSize();
        List<WebhookDeliveryDispatchContext> pending = fetch(batchSize, false);
        int submitted = submitBatch(pending, false, null);
        // Only reclaim stuck in-flight when we are below budget — if pending already filled
        // the batch there is enough work; stuck rows will be picked up on a later tick.
        int reclaimed = 0;
        int remaining = batchSize - submitted;
        if (remaining > 0) {
            int thresholdSeconds = getConfiguration().getEventNotificationStuckInFlightThresholdSeconds();
            java.sql.Timestamp cutoff = new java.sql.Timestamp(
                    System.currentTimeMillis() - thresholdSeconds * 1000L);
            List<WebhookDeliveryDispatchContext> stuck = fetch(remaining, true);
            if (!stuck.isEmpty()) {
                LOG.info("Reclaiming " + stuck.size() + " stuck in-flight webhook deliveries.");
            }
            reclaimed = submitBatch(stuck, true, cutoff);
        }
        if (submitted + reclaimed > 0) {
            LOG.info("Webhook delivery tick: submitted=" + submitted + ", reclaimed=" + reclaimed + ".");
        }
        return new int[] { submitted, reclaimed };
    }

    /**
     * Atomically consumes the one-time manual retry and submits exactly one exhausted delivery.
     * The persisted attempt count is retained, so a failure in the ordinary task path remains
     * terminal and cannot schedule another automatic retry.
     */
    public ManualRetrySubmissionResult submitManualRetry(String orgId, String subscriptionId, String deliveryId) {
        WebhookDeliveryDispatchContext[] contextHolder = new WebhookDeliveryDispatchContext[1];
        ManualRetrySubmissionResult result = DatabaseUtils.executeInTransaction(conn -> {
            Optional<WebhookDeliveryDispatchContext> context = deliveryDAO.getWebhookDeliveryDispatchContext(
                    conn, orgId, subscriptionId, deliveryId);
            if (!context.isPresent()) {
                return ManualRetrySubmissionResult.NOT_FOUND;
            }
            boolean prepared = deliveryDAO.prepareManualRetry(conn, orgId, subscriptionId, deliveryId,
                    getConfiguration().getEventNotificationMaxRetries());
            if (!prepared) {
                return ManualRetrySubmissionResult.NOT_ELIGIBLE;
            }
            contextHolder[0] = context.get();
            return ManualRetrySubmissionResult.ACCEPTED;
        });
        if (result != ManualRetrySubmissionResult.ACCEPTED) {
            return result;
        }

        try {
            executor.execute(() -> executeClaimed(contextHolder[0], false, null));
        } catch (RuntimeException e) {
            // The row remains pending and due, so the regular worker can pick it up on its next tick.
            LOG.error("Manual webhook retry was persisted but could not be submitted immediately for delivery ["
                    + LogSanitizer.sanitize(deliveryId) + "]: " + LogSanitizer.sanitize(e.getMessage()), e);
        }
        return ManualRetrySubmissionResult.ACCEPTED;
    }

    private List<WebhookDeliveryDispatchContext> fetch(int limit, boolean reclaim) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        try {
            return DatabaseUtils.executeInTransaction(conn -> {
                if (reclaim) {
                    int thresholdSeconds = getConfiguration().getEventNotificationStuckInFlightThresholdSeconds();
                    java.sql.Timestamp cutoff = new java.sql.Timestamp(
                            System.currentTimeMillis() - thresholdSeconds * 1000L);
                    return deliveryDAO.getStuckInFlightWebhookDispatchContexts(conn, limit, cutoff);
                }
                return deliveryDAO.getPendingWebhookDispatchContexts(conn, limit);
            });
        } catch (RuntimeException e) {
            LOG.error("Failed to fetch " + (reclaim ? "stuck" : "pending") + " webhook deliveries: "
                    + LogSanitizer.sanitize(e.getMessage()), e);
            return Collections.emptyList();
        }
    }

    /**
     * @param isReclaim  {@code true} when processing stuck in-flight rows; the claim uses
     *                   {@link DeliveryDAO#claimStuckWebhookDelivery} with a cutoff guard
     *                   to prevent re-claiming a row that is still being actively processed.
     * @param stuckCutoff the UPDATED_AT cutoff; only used when {@code isReclaim} is true.
     */
    private int submitBatch(List<WebhookDeliveryDispatchContext> contexts,
            boolean isReclaim, java.sql.Timestamp stuckCutoff) {
        int submitted = 0;
        for (WebhookDeliveryDispatchContext ctx : contexts) {
            WebhookDelivery delivery = ctx.getDelivery();
            try {
                executor.execute(() -> executeClaimed(ctx, isReclaim, stuckCutoff));
                submitted++;
            } catch (Exception e) {
                LOG.error("Failed to submit WebhookDeliveryTask for delivery ["
                        + LogSanitizer.sanitize(delivery.getDeliveryId()) + "]: "
                        + LogSanitizer.sanitize(e.getMessage()), e);
            }
        }
        return submitted;
    }

    private void executeClaimed(WebhookDeliveryDispatchContext ctx, boolean isReclaim,
            java.sql.Timestamp stuckCutoff) {
        WebhookDelivery delivery = ctx.getDelivery();
        boolean claimed = isReclaim
                ? claimStuck(delivery.getDeliveryId(), stuckCutoff)
                : claim(delivery.getDeliveryId());
        if (!claimed) {
            return;
        }
        if (!isDeliverable(ctx)) {
            markUnrecoverable(delivery, isReclaim
                    ? "missing callback URL, shared secret, or event payload "
                            + "(subscription may have been deleted)"
                    : "missing callback URL, shared secret, or event payload");
            return;
        }
        new WebhookDeliveryTask(
                delivery,
                ctx.getOrgId(),
                ctx.getGroupId(),
                ctx.getPayload(),
                ctx.getCallbackUrl(),
                ctx.getSharedSecret(),
                ctx.getTopic(),
                deliveryDAO,
                httpClient,
                getConfiguration()).run();
    }

    private DPDPConfigurationService getConfiguration() {
        if (configurationService == null) {
            return new org.wso2.dpdp.accelerator.common.config.DPDPConfigurationServiceImpl(false);
        }
        return configurationService;
    }

    private boolean claim(String deliveryId) {
        try {
            return DatabaseUtils.<Boolean>executeInTransaction(conn ->
                    deliveryDAO.claimWebhookDelivery(conn, deliveryId));
        } catch (RuntimeException e) {
            LOG.error("claimWebhookDelivery failed for [" + LogSanitizer.sanitize(deliveryId) + "]: "
                    + LogSanitizer.sanitize(e.getMessage()), e);
            return false;
        }
    }

    private boolean claimStuck(String deliveryId, java.sql.Timestamp cutoff) {
        try {
            return DatabaseUtils.<Boolean>executeInTransaction(conn ->
                    deliveryDAO.claimStuckWebhookDelivery(conn, deliveryId, cutoff));
        } catch (RuntimeException e) {
            LOG.error("claimStuckWebhookDelivery failed for [" + LogSanitizer.sanitize(deliveryId) + "]: "
                    + LogSanitizer.sanitize(e.getMessage()), e);
            return false;
        }
    }

    private static boolean isDeliverable(WebhookDeliveryDispatchContext ctx) {
        String url = ctx.getCallbackUrl();
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        String payload = ctx.getPayload();
        if (payload == null) {
            return false;
        }
        String sharedSecret = ctx.getSharedSecret();
        return sharedSecret != null && !sharedSecret.trim().isEmpty();
    }

    /**
     * Best-effort flip to {@code failed} for a delivery whose claim succeeded but
     * whose task
     * could not be hydrated. We deliberately skip an audit row here — the operator
     * can see
     * the FAILED status and the reason in the worker logs.
     */
    private void markUnrecoverable(WebhookDelivery delivery, String reason) {
        LOG.debug("Marking webhook delivery [" + LogSanitizer.sanitize(delivery.getDeliveryId())
                + "] unrecoverable: " + LogSanitizer.sanitize(reason));
        WebhookDelivery failed = new WebhookDelivery(
                delivery.getDeliveryId(),
                delivery.getSubscriptionId(),
                delivery.getEventId(),
                DeliveryStatus.FAILED.getValue(),
                delivery.getAttemptCount(),
                null,
                delivery.getCreatedAt(),
                new java.sql.Timestamp(System.currentTimeMillis()),
                null);
        try {
            DatabaseUtils.<Void>executeInTransaction(conn -> {
                deliveryDAO.updateWebhookDeliveryStatus(conn, failed);
                return null;
            });
        } catch (RuntimeException e) {
            LOG.error("Failed to mark unrecoverable webhook delivery ["
                    + LogSanitizer.sanitize(delivery.getDeliveryId()) + "] as failed: "
                    + LogSanitizer.sanitize(e.getMessage()), e);
        }
    }
}
