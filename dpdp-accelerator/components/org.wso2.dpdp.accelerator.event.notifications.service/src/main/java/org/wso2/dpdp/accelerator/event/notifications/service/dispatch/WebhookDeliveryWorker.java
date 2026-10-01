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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.common.util.DatabaseUtils;
import org.wso2.dpdp.accelerator.common.util.HTTPClientUtils;
import org.wso2.dpdp.accelerator.common.util.LogSanitizer;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.DeliveryStatus;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDelivery;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDeliveryDispatchContext;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
 * <li>Submits each context to the shared executor while it is still
 * pending.</li>
 * <li>When the executor starts that unit of work, atomically flips the row to
 * {@code in_flight} via {@link DeliveryDAO#claimWebhookDelivery(String)}.
 * Claimed
 * rows therefore represent running work, not work waiting in an executor
 * queue.</li>
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

    /**
     * Fine-grained result from {@link #submitOne} so that {@link #submitBatch}
     * can distinguish between three outcomes:
     * <ul>
     * <li>{@code QUEUED} — the task was handed to the executor; counts as forward
     * progress.</li>
     * <li>{@code CAPPED} — the subscription has already reached its per-subscription
     * in-flight limit; the row is skipped but the pool may still have capacity for
     * other subscriptions, so the batch loop should continue.</li>
     * <li>{@code POOL_SATURATED} — the executor rejected the task (queue full);
     * the loop must stop immediately.</li>
     * </ul>
     */
    private enum SubmitStatus {
        QUEUED,
        CAPPED,
        POOL_SATURATED
    }

    /** Carries the results of one {@link #submitBatch} call back to {@link #runTick}. */
    private static final class BatchResult {
        final int submitted;
        final boolean poolSaturated;

        BatchResult(int submitted, boolean poolSaturated) {
            this.submitted = submitted;
            this.poolSaturated = poolSaturated;
        }
    }

    private static final Log LOG = LogFactory.getLog(WebhookDeliveryWorker.class);

    private final DeliveryDAO deliveryDAO;
    private final Executor executor;
    private final HttpClient httpClient;
    private final DPDPConfigurationService configurationService;

    private final Set<String> tracked = ConcurrentHashMap.newKeySet();
    /**
     * Tracks how many deliveries for each subscription are currently in-flight
     * (i.e. handed to the executor and not yet complete). The counter is
     * incremented <em>before</em> the executor lambda starts (inside
     * {@link #submitOne}) and decremented in a {@code finally} block inside the
     * lambda so it is always released regardless of claim failure or exception.
     *
     * <p>Keys are never explicitly removed; a lingering zero-count entry is
     * harmless and avoids the TOCTOU race that arises when a concurrent decrement
     * sees the key disappear between {@code get} and {@code remove}.</p>
     */
    private final ConcurrentHashMap<String, AtomicInteger> perSubInFlight = new ConcurrentHashMap<>();
    private volatile boolean stopping;

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

    public void stop() {
        this.stopping = true;
    }

    public boolean isStopping() {
        return stopping;
    }

    int getTrackedCount() {
        return tracked.size();
    }

    boolean isTracked(String deliveryId) {
        return tracked.contains(deliveryId);
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
        if (stopping) {
            return new int[] { 0, 0 };
        }
        int batchSize = getConfiguration().getEventNotificationDeliveryWorkerBatchSize();
        int maxBatches = Math.max(1, getConfiguration().getEventNotificationDeliveryWorkerMaxBatchesPerRun());
        long maxRunNanos = TimeUnit.SECONDS.toNanos(
                Math.max(1, getConfiguration().getEventNotificationDeliveryWorkerMaxRunSeconds()));
        long startNanos = System.nanoTime();

        // 1. Reserved reclaim pass: always attempted first so stuck in-flight
        // deliveries
        // are never starved by a sustained backlog of pending deliveries.
        int reclaimBudget = Math.max(1, batchSize / 10);
        int reclaimed = reclaimStuck(reclaimBudget);

        // 2. Pending drain loop: fills the remaining budget up to batchSize, and
        // continues
        // fetching further batches while forward progress is being made and capacity
        // allows.
        int totalSubmitted = 0;
        int pendingBudget = Math.max(0, batchSize - reclaimed);
        Set<String> seenThisTick = new HashSet<>();

        for (int batch = 0; batch < maxBatches; batch++) {
            if (stopping || (System.nanoTime() - startNanos) >= maxRunNanos) {
                break;
            }
            int budget = (batch == 0) ? pendingBudget : batchSize;
            if (budget <= 0) {
                break;
            }
            int fetchLimit = budget + seenThisTick.size();
            List<WebhookDeliveryDispatchContext> pending = fetch(fetchLimit, false);
            if (pending.isEmpty()) {
                break;
            }

            List<WebhookDeliveryDispatchContext> fresh = new ArrayList<>();
            for (WebhookDeliveryDispatchContext ctx : pending) {
                if (ctx != null && ctx.getDelivery() != null) {
                    String id = ctx.getDelivery().getDeliveryId();
                    if (id != null && !tracked.contains(id) && seenThisTick.add(id)) {
                        fresh.add(ctx);
                        if (fresh.size() == budget) {
                            break;
                        }
                    }
                }
            }

            if (fresh.isEmpty()) {
                break;
            }

            BatchResult result = submitBatch(fresh, false, null);
            totalSubmitted += result.submitted;

            // Stop if the pool is saturated — more rows exist but we have no capacity.
            // Do NOT stop just because some rows were capped; other subscriptions may
            // still have capacity in both the per-subscription limit and the pool.
            if (result.poolSaturated || pending.size() < fetchLimit) {
                break;
            }
        }

        if (totalSubmitted + reclaimed > 0) {
            LOG.info("Webhook delivery tick: submitted=" + totalSubmitted + ", reclaimed=" + reclaimed + ".");
        }
        return new int[] { totalSubmitted, reclaimed };
    }

    private int reclaimStuck(int limit) {
        if (limit <= 0 || stopping) {
            return 0;
        }
        int thresholdSeconds = getConfiguration().getEventNotificationStuckInFlightThresholdSeconds();
        java.sql.Timestamp cutoff = new java.sql.Timestamp(
                System.currentTimeMillis() - thresholdSeconds * 1000L);
        List<WebhookDeliveryDispatchContext> stuck = fetch(limit, true);
        if (stuck.isEmpty()) {
            return 0;
        }
        LOG.info("Reclaiming " + stuck.size() + " stuck in-flight webhook deliveries.");
        return submitBatch(stuck, true, cutoff).submitted;
    }

    /**
     * Atomically consumes the one-time manual retry and submits exactly one
     * exhausted delivery.
     * The persisted attempt count is retained, so a failure in the ordinary task
     * path remains
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

        if (!tracked.add(deliveryId)) {
            return ManualRetrySubmissionResult.ACCEPTED;
        }
        try {
            executor.execute(() -> {
                try {
                    executeClaimed(contextHolder[0], false, null);
                } finally {
                    tracked.remove(deliveryId);
                }
            });
        } catch (RuntimeException e) {
            tracked.remove(deliveryId);
            // The row remains pending and due, so the regular worker can pick it up on its
            // next tick.
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
     * @param isReclaim   {@code true} when processing stuck in-flight rows; the
     *                    claim uses
     *                    {@link DeliveryDAO#claimStuckWebhookDelivery} with a
     *                    cutoff guard
     *                    to prevent re-claiming a row that is still being actively
     *                    processed.
     * @param stuckCutoff the UPDATED_AT cutoff; only used when {@code isReclaim} is
     *                    true.
     */
    private BatchResult submitBatch(List<WebhookDeliveryDispatchContext> contexts,
            boolean isReclaim, java.sql.Timestamp stuckCutoff) {
        int submitted = 0;
        for (WebhookDeliveryDispatchContext ctx : contexts) {
            if (stopping) {
                break;
            }
            String id = ctx.getDelivery().getDeliveryId();
            if (tracked.contains(id)) {
                continue;
            }
            SubmitStatus status = submitOne(ctx, isReclaim, stuckCutoff);
            if (status == SubmitStatus.QUEUED) {
                submitted++;
            } else if (status == SubmitStatus.POOL_SATURATED) {
                return new BatchResult(submitted, true);
            }
            // CAPPED: skip this row but continue trying other subscriptions.
        }
        return new BatchResult(submitted, false);
    }

    private SubmitStatus submitOne(WebhookDeliveryDispatchContext ctx, boolean isReclaim,
            java.sql.Timestamp stuckCutoff) {
        String id = ctx.getDelivery().getDeliveryId();
        String subscriptionId = ctx.getDelivery().getSubscriptionId();
        int cap = getConfiguration().getEventNotificationDeliveryWorkerMaxConcurrentPerSubscription();

        // Per-subscription cap gate: increment before acquiring the tracker slot so
        // the counter is always paired with a decrement in the executor finally block.
        AtomicInteger counter = perSubInFlight.computeIfAbsent(subscriptionId, k -> new AtomicInteger(0));
        int current = counter.getAndUpdate(v -> v < cap ? v + 1 : v);
        if (current >= cap) {
            // Counter was not incremented; this subscription is at its limit.
            if (LOG.isDebugEnabled()) {
                LOG.debug("Per-subscription cap reached for subscription ["
                        + LogSanitizer.sanitize(subscriptionId) + "]; skipping delivery ["
                        + LogSanitizer.sanitize(id) + "].");
            }
            return SubmitStatus.CAPPED;
        }

        // Counter incremented — must decrement in all exit paths below.
        if (!tracked.add(id)) {
            // Already tracked by a concurrent tick; undo the counter increment.
            counter.decrementAndGet();
            return SubmitStatus.CAPPED;
        }
        try {
            executor.execute(() -> {
                try {
                    executeClaimed(ctx, isReclaim, stuckCutoff);
                } finally {
                    tracked.remove(id);
                    counter.decrementAndGet();
                }
            });
            return SubmitStatus.QUEUED;
        } catch (RejectedExecutionException e) {
            tracked.remove(id);
            counter.decrementAndGet();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Delivery worker pool saturated; backing off delivery ["
                        + LogSanitizer.sanitize(id) + "].");
            }
            return SubmitStatus.POOL_SATURATED;
        } catch (RuntimeException e) {
            tracked.remove(id);
            counter.decrementAndGet();
            LOG.error("Failed to submit WebhookDeliveryTask for delivery ["
                    + LogSanitizer.sanitize(id) + "]: "
                    + LogSanitizer.sanitize(e.getMessage()), e);
            return SubmitStatus.POOL_SATURATED;
        }
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
            return DatabaseUtils
                    .<Boolean>executeInTransaction(conn -> deliveryDAO.claimWebhookDelivery(conn, deliveryId));
        } catch (RuntimeException e) {
            LOG.error("claimWebhookDelivery failed for [" + LogSanitizer.sanitize(deliveryId) + "]: "
                    + LogSanitizer.sanitize(e.getMessage()), e);
            return false;
        }
    }

    private boolean claimStuck(String deliveryId, java.sql.Timestamp cutoff) {
        try {
            return DatabaseUtils.<Boolean>executeInTransaction(
                    conn -> deliveryDAO.claimStuckWebhookDelivery(conn, deliveryId, cutoff));
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

    /** Marks an unhydratable claimed delivery as failed; the reason is logged. */
    private void markUnrecoverable(WebhookDelivery delivery, String reason) {
        LOG.debug("Marking webhook delivery [" + LogSanitizer.sanitize(delivery.getDeliveryId())
                + "] unrecoverable: " + LogSanitizer.sanitize(reason));
        WebhookDelivery failed = new WebhookDelivery(
                delivery.getDeliveryId(),
                delivery.getOrgId(),
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
