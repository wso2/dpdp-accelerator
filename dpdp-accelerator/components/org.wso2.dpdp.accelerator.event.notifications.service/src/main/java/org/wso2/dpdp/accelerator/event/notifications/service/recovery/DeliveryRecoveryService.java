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

import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.common.util.DatabaseUtils;
import org.wso2.dpdp.accelerator.common.util.LogSanitizer;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.SubscriptionDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Subscription;
import org.wso2.dpdp.accelerator.event.notifications.service.SubscriptionService;
import org.wso2.dpdp.accelerator.event.notifications.service.constants.EventNotificationServiceConstants;
import org.wso2.dpdp.accelerator.event.notifications.service.dispatch.WebhookDeliveryWorker;

import java.net.http.HttpClient;

import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Dedicated OSGi background recovery service for recovering overdue webhook
 * retries
 * and stuck pending subscriptions across JVM server restarts.
 */
public class DeliveryRecoveryService {

    private static final Log LOG = LogFactory.getLog(DeliveryRecoveryService.class);

    private SubscriptionDAO subscriptionDAO;

    private DeliveryDAO deliveryDAO;

    private SubscriptionService subscriptionService;

    private DPDPConfigurationService configurationService;

    private ScheduledExecutorService scheduler;
    private ExecutorService workerPool;
    private WebhookDeliveryWorker webhookDeliveryWorker;

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
        if (stuckThresholdSeconds <= EventNotificationServiceConstants.WEBHOOK_HTTP_TIMEOUT_SECONDS) {
            throw new IllegalStateException("Event notification stuck in-flight threshold must be greater than "
                    + EventNotificationServiceConstants.WEBHOOK_HTTP_TIMEOUT_SECONDS
                    + " seconds so an active webhook request cannot be reclaimed.");
        }
        int verificationRecoveryThresholdSeconds =
                configurationService.getEventNotificationPendingSubscriptionRecoveryThresholdSeconds();
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
        int recoveryIntervalSeconds =
                configurationService.getEventNotificationPendingSubscriptionRecoveryIntervalSeconds();
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

    private class PendingDeliveryRecoveryTask implements Runnable {
        @Override
        public void run() {
            try {
                recoverPendingSubscriptions();
            } catch (Exception e) {
                LOG.error("Error during pending subscription recovery run: "
                        + LogSanitizer.sanitize(e.getMessage()), e);
            }
        }

        private void recoverPendingSubscriptions() {
            Timestamp threshold = new Timestamp(System.currentTimeMillis()
                    - configurationService.getEventNotificationPendingSubscriptionRecoveryThresholdSeconds()
                    * 1000L);
            int batchSize = configurationService.getEventNotificationPendingSubscriptionRecoveryBatchSize();
            List<Subscription> pendingSubs;
            try {
                pendingSubs = DatabaseUtils.executeInTransaction(conn ->
                        subscriptionDAO.getPendingSubscriptionsForRecovery(conn, threshold, batchSize));
            } catch (RuntimeException e) {
                LOG.error("Failed to fetch pending subscriptions for recovery: "
                        + LogSanitizer.sanitize(e.getMessage()), e);
                return;
            }
            for (Subscription sub : pendingSubs) {
                if (sub.getCallbackUrl() != null && !sub.getCallbackUrl().trim().isEmpty()) {
                    try {
                        subscriptionService.retryVerification(sub.getOrgId(), sub.getSubscriptionId());
                        LOG.info("Recovered and re-verified pending subscription ["
                                + LogSanitizer.sanitize(sub.getSubscriptionId()) + "].");
                    } catch (Exception e) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Recovery retry verification for subscription ["
                                    + LogSanitizer.sanitize(sub.getSubscriptionId()) + "] deferred: "
                                    + LogSanitizer.sanitize(e.getMessage()));
                        }
                    }
                }
            }
        }
    }
}
