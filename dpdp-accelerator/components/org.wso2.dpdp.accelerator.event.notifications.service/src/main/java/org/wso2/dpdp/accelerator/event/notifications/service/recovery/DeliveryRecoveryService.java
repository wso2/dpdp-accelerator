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

package org.wso2.dpdp.accelerator.event.notifications.service.recovery;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.common.util.DatabaseUtils;
import org.wso2.dpdp.accelerator.common.util.LogSanitizer;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.SubscriptionStatus;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.SubscriptionDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Subscription;
import org.wso2.dpdp.accelerator.event.notifications.service.SubscriptionService;
import org.wso2.dpdp.accelerator.event.notifications.service.constants.EventNotificationServiceConstants;
import org.wso2.dpdp.accelerator.event.notifications.service.dispatch.WebhookDeliveryWorker;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Dedicated OSGi background recovery service for recovering overdue webhook
 * retries
 * and stuck pending subscriptions across JVM server restarts.
 */
public class DeliveryRecoveryService {

    enum ExitReason {
        TIME_BUDGET,
        MAX_BATCHES,
        NO_MORE_WORK,
        NO_FRESH_ROWS,
        STOPPING
    }

    private static final Log LOG = LogFactory.getLog(DeliveryRecoveryService.class);
    static final int MAX_BATCHES_PER_TICK = 10;
    // Do not start a verification unless it can complete within the window at its
    // normal timeout.
    static final long ITEM_START_FLOOR_NANOS = TimeUnit.SECONDS
            .toNanos(EventNotificationServiceConstants.WEBHOOK_HTTP_TIMEOUT_SECONDS);

    private SubscriptionDAO subscriptionDAO;

    private DeliveryDAO deliveryDAO;

    private SubscriptionService subscriptionService;

    private DPDPConfigurationService configurationService;

    private ScheduledExecutorService scheduler;
    private ExecutorService workerPool;
    private WebhookDeliveryWorker webhookDeliveryWorker;
    private volatile boolean stopping;
    private volatile ExitReason lastExitReason;

    public DeliveryRecoveryService() {
    }

    public DeliveryRecoveryService(SubscriptionDAO subscriptionDAO,
            DeliveryDAO deliveryDAO, SubscriptionService subscriptionService,
            DPDPConfigurationService configurationService) {
        this.subscriptionDAO = subscriptionDAO;
        this.deliveryDAO = deliveryDAO;
        this.subscriptionService = subscriptionService;
        this.configurationService = configurationService;
    }

    protected void activate() {
        int stuckThresholdSeconds = configurationService.getEventNotificationStuckInFlightThresholdSeconds();
        int minStuckThreshold = (int) EventNotificationServiceConstants.WEBHOOK_HTTP_TIMEOUT_SECONDS + 5;
        if (stuckThresholdSeconds < minStuckThreshold) {
            throw new IllegalStateException("Event notification stuck in-flight threshold must be at least "
                    + minStuckThreshold + " seconds to provide a safety margin over the "
                    + EventNotificationServiceConstants.WEBHOOK_HTTP_TIMEOUT_SECONDS
                    + " second HTTP timeout, so an active webhook request cannot be reclaimed.");
        }
        int verificationRecoveryThresholdSeconds = configurationService
                .getEventNotificationPendingSubscriptionRecoveryThresholdSeconds();
        if (verificationRecoveryThresholdSeconds <= EventNotificationServiceConstants.WEBHOOK_HTTP_TIMEOUT_SECONDS) {
            throw new IllegalStateException("Event notification pending subscription recovery threshold must be " +
                    "greater than " + EventNotificationServiceConstants.WEBHOOK_HTTP_TIMEOUT_SECONDS +
                    " seconds so an active verification request is not selected for recovery.");
        }
        int poolSize = configurationService.getEventNotificationThreadPoolSize();
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "delivery-recovery-scheduler");
            t.setDaemon(true);
            return t;
        });
        int workerCount = Math.max(1, poolSize);
        int queueCapacity = Math.max(1,
                configurationService.getEventNotificationDeliveryWorkerBatchSize());
        this.workerPool = new ThreadPoolExecutor(workerCount, workerCount, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity), r -> {
                    Thread t = new Thread(r, "webhook-delivery-worker");
                    t.setDaemon(true);
                    return t;
                }, new ThreadPoolExecutor.AbortPolicy());

        int initialDelaySeconds = configurationService.getEventNotificationBackgroundWorkerInitialDelaySeconds();
        int recoveryIntervalSeconds = configurationService
                .getEventNotificationPendingSubscriptionRecoveryIntervalSeconds();
        this.scheduler.scheduleWithFixedDelay(new PendingDeliveryRecoveryTask(), initialDelaySeconds,
                recoveryIntervalSeconds, TimeUnit.SECONDS);

        int deliveryPollSeconds = configurationService.getEventNotificationDeliveryWorkerPollSeconds();
        this.webhookDeliveryWorker = new WebhookDeliveryWorker(deliveryDAO, this.workerPool, configurationService);
        this.scheduler.scheduleWithFixedDelay(
                webhookDeliveryWorker,
                initialDelaySeconds,
                deliveryPollSeconds,
                TimeUnit.SECONDS);

        LOG.info("Delivery Recovery Service activated with background recovery worker and webhook "
                + "delivery worker (poll every " + deliveryPollSeconds + "s).");
    }

    protected void deactivate() {
        this.stopping = true;
        if (this.webhookDeliveryWorker != null) {
            this.webhookDeliveryWorker.stop();
        }
        int shutdownTimeoutSeconds = configurationService.getEventNotificationWorkerShutdownTimeoutSeconds();
        shutdownGracefully("delivery-recovery-scheduler", scheduler, shutdownTimeoutSeconds);
        shutdownGracefully("webhook-delivery-worker-pool", workerPool, shutdownTimeoutSeconds);
        LOG.info("Delivery Recovery Service deactivated cleanly.");
    }

    public void start() {
        activate();
    }

    public void stop() {
        deactivate();
    }

    public WebhookDeliveryWorker.ManualRetrySubmissionResult submitManualRetry(String orgId, String subscriptionId,
            String deliveryId) {
        if (webhookDeliveryWorker == null) {
            throw new IllegalStateException("Webhook delivery worker is not initialized.");
        }
        return webhookDeliveryWorker.submitManualRetry(orgId, subscriptionId, deliveryId);
    }

    public WebhookDeliveryWorker.ManualRetrySubmissionResult submitManualRetry(String orgId, String deliveryId) {
        if (webhookDeliveryWorker == null) {
            throw new IllegalStateException("Webhook delivery worker is not initialized.");
        }
        return webhookDeliveryWorker.submitManualRetry(orgId, deliveryId);
    }

    private static void shutdownGracefully(String name, java.util.concurrent.ExecutorService pool,
            int timeoutSeconds) {
        if (pool == null || pool.isShutdown()) {
            return;
        }
        pool.shutdown();
        try {
            if (!pool.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                LOG.debug(LogSanitizer.sanitize(name) + " did not terminate within " + timeoutSeconds
                        + " s; forcing interrupt.");
                pool.shutdownNow();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            pool.shutdownNow();
        }
    }

    ExitReason getLastExitReason() {
        return lastExitReason;
    }

    private class PendingDeliveryRecoveryTask implements Runnable {
        @Override
        public void run() {
            if (stopping) {
                lastExitReason = ExitReason.STOPPING;
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Pending subscription recovery tick finished: exit=" + ExitReason.STOPPING);
                }
                return;
            }
            try {
                recoverPendingSubscriptions();
            } catch (Exception e) {
                LOG.error("Error during pending subscription recovery run: "
                        + LogSanitizer.sanitize(e.getMessage()), e);
            }
        }

        private void recoverPendingSubscriptions() {
            long startNanos = System.nanoTime();
            Timestamp threshold = new Timestamp(System.currentTimeMillis()
                    - configurationService.getEventNotificationPendingSubscriptionRecoveryThresholdSeconds()
                            * 1000L);
            int batchSize = Math.max(1,
                    configurationService.getEventNotificationPendingSubscriptionRecoveryBatchSize());
            int intervalSeconds = Math.max(1,
                    configurationService.getEventNotificationPendingSubscriptionRecoveryIntervalSeconds());
            long maxRunNanos = TimeUnit.SECONDS.toNanos(intervalSeconds);
            long deadlineNanos = startNanos + maxRunNanos;
            Set<String> seen = new HashSet<>();
            int processedInTick = 0;
            long maxBatchDurationNanos = 0L;
            ExitReason exitReason = ExitReason.MAX_BATCHES;

            for (int batch = 0; batch < MAX_BATCHES_PER_TICK; batch++) {
                if (stopping) {
                    exitReason = ExitReason.STOPPING;
                    break;
                }
                if (batch > 0) {
                    long remainingNanos = deadlineNanos - System.nanoTime();
                    if (remainingNanos <= 0 || remainingNanos < maxBatchDurationNanos) {
                        exitReason = ExitReason.TIME_BUDGET;
                        break;
                    }
                }
                long batchStartNanos = System.nanoTime();
                int limit = batchSize + seen.size();
                List<Subscription> pendingSubs;
                try {
                    pendingSubs = DatabaseUtils.executeInTransaction(
                            conn -> subscriptionDAO.getPendingSubscriptionsForRecovery(conn, threshold, limit));
                } catch (RuntimeException e) {
                    LOG.error("Failed to fetch pending subscriptions for recovery: "
                            + LogSanitizer.sanitize(e.getMessage()), e);
                    return;
                }
                if (pendingSubs == null || pendingSubs.isEmpty()) {
                    exitReason = ExitReason.NO_MORE_WORK;
                    break;
                }

                List<Subscription> fresh = new ArrayList<>();
                for (Subscription sub : pendingSubs) {
                    if (sub != null && sub.getSubscriptionId() != null && seen.add(sub.getSubscriptionId())) {
                        fresh.add(sub);
                        if (fresh.size() == batchSize) {
                            break;
                        }
                    }
                }

                if (fresh.isEmpty()) {
                    exitReason = (pendingSubs.size() < limit) ? ExitReason.NO_MORE_WORK : ExitReason.NO_FRESH_ROWS;
                    break;
                }

                boolean deadlineHit = false;
                for (Subscription sub : fresh) {
                    if (stopping) {
                        deadlineHit = true;
                        break;
                    }
                    // Hard bound: gate the START of each item. The first item of the tick always
                    // runs.
                    // recoverOne(sub) and its 5s HTTP timeout stay untouched.
                    if (processedInTick > 0 && (deadlineNanos - System.nanoTime()) < ITEM_START_FLOOR_NANOS) {
                        deadlineHit = true;
                        break;
                    }
                    if (!recoverOne(sub)) {
                        touchPending(sub);
                    }
                    processedInTick++;
                }

                long batchDurationNanos = Math.max(1L, System.nanoTime() - batchStartNanos);
                maxBatchDurationNanos = Math.max(maxBatchDurationNanos, batchDurationNanos);

                if (deadlineHit) {
                    exitReason = stopping ? ExitReason.STOPPING : ExitReason.TIME_BUDGET;
                    break;
                }
                if (pendingSubs.size() < limit) {
                    exitReason = ExitReason.NO_MORE_WORK;
                    break;
                }
            }
            lastExitReason = exitReason;
            if (LOG.isDebugEnabled()) {
                LOG.debug("Pending subscription recovery tick finished: exit=" + exitReason);
            }
        }

        private boolean recoverOne(Subscription sub) {
            if (sub.getCallbackUrl() == null || sub.getCallbackUrl().trim().isEmpty()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Pending subscription [" + LogSanitizer.sanitize(sub.getSubscriptionId())
                            + "] has blank callback URL; touching timestamp to avoid busy-spin.");
                }
                return false;
            }
            try {
                subscriptionService.retryVerification(sub.getOrgId(), sub.getSubscriptionId());
                LOG.info("Recovered and re-verified pending subscription ["
                        + LogSanitizer.sanitize(sub.getSubscriptionId()) + "].");
                return true;
            } catch (Exception e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Recovery retry verification for subscription ["
                            + LogSanitizer.sanitize(sub.getSubscriptionId()) + "] deferred: "
                            + LogSanitizer.sanitize(e.getMessage()));
                }
                return false;
            }
        }

        private void touchPending(Subscription sub) {
            try {
                DatabaseUtils.executeInTransaction(conn ->
                        subscriptionDAO.updateSubscriptionStatus(conn, sub.getSubscriptionId(),
                                sub.getOrgId(), SubscriptionStatus.PENDING.getValue(),
                                SubscriptionStatus.PENDING.getValue()));
            } catch (Exception e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Failed to touch pending subscription ["
                            + LogSanitizer.sanitize(sub.getSubscriptionId()) + "]: "
                            + LogSanitizer.sanitize(e.getMessage()));
                }
            }
        }
    }
}
