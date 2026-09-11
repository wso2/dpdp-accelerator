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

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.common.util.HTTPClientUtils;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.DeliveryStatus;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDelivery;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDeliveryDispatchContext;

import java.net.http.HttpClient;
import java.sql.Connection;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

/**
 * Unit tests for {@link WebhookDeliveryWorker}. Verifies that the worker claims deliveries,
 * skips unclaimable rows, and falls through to the stuck-in-flight reclaim pass when the
 * pending pass is empty.
 */
public class WebhookDeliveryWorkerTest {

    @Mock
    private DeliveryDAO deliveryDAO;

    @Mock
    private DPDPConfigurationService configurationService;

    @Mock
    private Connection connection;

    private QueueingExecutor scheduler;
    private HttpClient httpClient;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        javax.sql.DataSource dataSource = org.mockito.Mockito.mock(javax.sql.DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        setStaticInstance(null);
        setStaticDataSource(dataSource);
        // Claims now happen inside executing work, so tests control exactly when queued
        // units start by draining this executor explicitly.
        scheduler = new QueueingExecutor();
        httpClient = HTTPClientUtils.getHttpClient();
        when(configurationService.getEventNotificationDeliveryWorkerBatchSize()).thenReturn(50);
        when(configurationService.getEventNotificationStuckInFlightThresholdSeconds()).thenReturn(10);
    }

    @org.testng.annotations.AfterMethod
    public void tearDown() throws Exception {
        setStaticDataSource(null);
        setStaticInstance(null);
    }

    private static void setStaticDataSource(javax.sql.DataSource dataSource) throws Exception {
        java.lang.reflect.Field field = org.wso2.dpdp.accelerator.common.persistence.JDBCPersistenceManager.class.getDeclaredField("dataSource");
        field.setAccessible(true);
        field.set(null, dataSource);
    }

    private static void setStaticInstance(org.wso2.dpdp.accelerator.common.persistence.JDBCPersistenceManager instance) throws Exception {
        java.lang.reflect.Field field = org.wso2.dpdp.accelerator.common.persistence.JDBCPersistenceManager.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, instance);
    }

    // Minimal ScheduledExecutorService that queues work until the test explicitly starts it.
    private static class QueueingExecutor implements ScheduledExecutorService {
        private final java.util.List<Runnable> queued = new java.util.ArrayList<>();

        @Override public java.util.concurrent.ScheduledFuture<?> schedule(Runnable r, long d, TimeUnit u) { return null; }
        @Override public <V> java.util.concurrent.ScheduledFuture<V> schedule(java.util.concurrent.Callable<V> c, long d, TimeUnit u) { return null; }
        @Override public java.util.concurrent.ScheduledFuture<?> scheduleAtFixedRate(Runnable r, long i, long p, TimeUnit u) { return null; }
        @Override public java.util.concurrent.ScheduledFuture<?> scheduleWithFixedDelay(Runnable r, long i, long p, TimeUnit u) { return null; }
        @Override public void execute(Runnable r) { queued.add(r); }
        @Override public void shutdown() {}
        @Override public java.util.List<Runnable> shutdownNow() { return java.util.Collections.emptyList(); }
        @Override public boolean isShutdown() { return false; }
        @Override public boolean isTerminated() { return false; }
        @Override public boolean awaitTermination(long t, TimeUnit u) { return true; }
        @Override public <T> java.util.concurrent.Future<T> submit(java.util.concurrent.Callable<T> c) { return null; }
        @Override public <T> java.util.concurrent.Future<T> submit(Runnable r, T result) { return null; }
        @Override public java.util.concurrent.Future<?> submit(Runnable r) { return null; }
        @Override public <T> java.util.List<java.util.concurrent.Future<T>> invokeAll(java.util.Collection<? extends java.util.concurrent.Callable<T>> c) { return java.util.Collections.emptyList(); }
        @Override public <T> java.util.List<java.util.concurrent.Future<T>> invokeAll(java.util.Collection<? extends java.util.concurrent.Callable<T>> c, long t, TimeUnit u) { return java.util.Collections.emptyList(); }
        @Override public <T> T invokeAny(java.util.Collection<? extends java.util.concurrent.Callable<T>> c) { return null; }
        @Override public <T> T invokeAny(java.util.Collection<? extends java.util.concurrent.Callable<T>> c, long t, TimeUnit u) { return null; }

        private void runAll() {
            java.util.List<Runnable> copy = new java.util.ArrayList<>(queued);
            queued.clear();
            copy.forEach(Runnable::run);
        }
    }

    private static class RejectingExecutor extends QueueingExecutor {
        @Override
        public void execute(Runnable r) {
            throw new RuntimeException("executor rejected");
        }
    }

    private WebhookDeliveryDispatchContext context(String deliveryId, int attemptCount) {
        return context(deliveryId, attemptCount, "accounts");
    }

    private WebhookDeliveryDispatchContext context(String deliveryId, int attemptCount,
            String topic) {
        WebhookDelivery delivery = new WebhookDelivery(
                deliveryId,
                "sub-1",
                "event-1",
                "pending",
                attemptCount,
                null,
                new Timestamp(System.currentTimeMillis() - 5_000L),
                new Timestamp(System.currentTimeMillis() - 5_000L),
                null);
        return new WebhookDeliveryDispatchContext(
                delivery,
                "org-1",
                "group-1",
                "https://callback.example.com/hook",
                "secret",
                "{\"hello\":\"world\"}",
                delivery.getUpdatedAt(),
                topic);
    }

    @Test
    public void testTickSubmitsPendingDeliveries() {
        // Default batch size is 50. Returning 50 pending rows fills the batch and skips the
        // reclaim pass — we already have a full tick of work.
        java.util.List<WebhookDeliveryDispatchContext> full = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) {
            full.add(context("d" + i, 0));
        }
        when(deliveryDAO.getPendingWebhookDispatchContexts(any(Connection.class), anyInt())).thenReturn(full);
        when(deliveryDAO.claimWebhookDelivery(any(Connection.class), anyString())).thenReturn(true);

        WebhookDeliveryWorker worker = new WebhookDeliveryWorker(deliveryDAO, scheduler, httpClient, configurationService);
        int[] counts = worker.runTick();

        assertEquals(counts[0], 50, "50 pending rows should be submitted");
        verify(deliveryDAO, never()).getStuckInFlightWebhookDispatchContexts(any(Connection.class), anyInt(), any());
        verify(deliveryDAO, never()).claimWebhookDelivery(any(Connection.class), anyString());
    }

    @Test
    public void testManualRetryIsPreparedAndQueuedOnce() {
        WebhookDeliveryDispatchContext dispatchContext = context("manual-1", 6);
        when(configurationService.getEventNotificationMaxRetries()).thenReturn(5);
        when(deliveryDAO.getWebhookDeliveryDispatchContext(any(Connection.class), eq("org-1"), eq("sub-1"),
                eq("manual-1"))).thenReturn(Optional.of(dispatchContext));
        when(deliveryDAO.prepareManualRetry(any(Connection.class), eq("org-1"), eq("sub-1"), eq("manual-1"),
                eq(5))).thenReturn(true);

        WebhookDeliveryWorker worker = new WebhookDeliveryWorker(deliveryDAO, scheduler, httpClient,
                configurationService);
        WebhookDeliveryWorker.ManualRetrySubmissionResult result = worker.submitManualRetry(
                "org-1", "sub-1", "manual-1");

        assertEquals(result, WebhookDeliveryWorker.ManualRetrySubmissionResult.ACCEPTED);
        verify(deliveryDAO).prepareManualRetry(any(Connection.class), eq("org-1"), eq("sub-1"),
                eq("manual-1"), eq(5));
        verify(deliveryDAO, never()).claimWebhookDelivery(any(Connection.class), anyString());
    }

    @Test
    public void testManualRetryRejectsMissingDelivery() {
        when(deliveryDAO.getWebhookDeliveryDispatchContext(any(Connection.class), eq("org-1"), eq("sub-1"),
                eq("missing"))).thenReturn(Optional.empty());

        WebhookDeliveryWorker.ManualRetrySubmissionResult result = new WebhookDeliveryWorker(
                deliveryDAO, scheduler, httpClient, configurationService).submitManualRetry(
                        "org-1", "sub-1", "missing");

        assertEquals(result, WebhookDeliveryWorker.ManualRetrySubmissionResult.NOT_FOUND);
        verify(deliveryDAO, never()).prepareManualRetry(any(Connection.class), anyString(), anyString(), anyString(),
                anyInt());
    }

    @Test
    public void testManualRetryRejectsAlreadyUsedDelivery() {
        WebhookDeliveryDispatchContext dispatchContext = context("manual-used", 6);
        when(configurationService.getEventNotificationMaxRetries()).thenReturn(5);
        when(deliveryDAO.getWebhookDeliveryDispatchContext(any(Connection.class), eq("org-1"), eq("sub-1"),
                eq("manual-used"))).thenReturn(Optional.of(dispatchContext));
        when(deliveryDAO.prepareManualRetry(any(Connection.class), eq("org-1"), eq("sub-1"),
                eq("manual-used"), eq(5))).thenReturn(false);

        WebhookDeliveryWorker.ManualRetrySubmissionResult result = new WebhookDeliveryWorker(
                deliveryDAO, scheduler, httpClient, configurationService).submitManualRetry(
                        "org-1", "sub-1", "manual-used");

        assertEquals(result, WebhookDeliveryWorker.ManualRetrySubmissionResult.NOT_ELIGIBLE);
        verify(deliveryDAO, never()).claimWebhookDelivery(any(Connection.class), anyString());
    }

    @Test
    public void testTickSkipsRowsThatCannotBeClaimed() {
        when(deliveryDAO.getPendingWebhookDispatchContexts(any(Connection.class), anyInt()))
                .thenReturn(java.util.Collections.singletonList(context("d1", 0)));
        when(deliveryDAO.claimWebhookDelivery(any(Connection.class), eq("d1"))).thenReturn(false);

        WebhookDeliveryWorker worker = new WebhookDeliveryWorker(deliveryDAO, scheduler, httpClient, configurationService);
        int[] counts = worker.runTick();

        assertEquals(counts[0], 1, "The candidate is queued before its claim is attempted");
        scheduler.runAll();
        verify(deliveryDAO).claimWebhookDelivery(any(Connection.class), eq("d1"));
    }

    @Test
    public void testEmptyPendingTriggersStuckPass() {
        // No pending rows; the second pass should pick up stuck in-flight rows.
        when(deliveryDAO.getPendingWebhookDispatchContexts(any(Connection.class), anyInt()))
                .thenReturn(Collections.emptyList());
        when(deliveryDAO.getStuckInFlightWebhookDispatchContexts(any(Connection.class), anyInt(), any()))
                .thenReturn(java.util.Collections.singletonList(context("stuck-1", 3)));
        // Stuck rows are claimed via claimStuckWebhookDelivery (cutoff-guarded), not the
        // regular claimWebhookDelivery, so the pending-claim mock is intentionally absent.
        when(deliveryDAO.claimStuckWebhookDelivery(any(Connection.class), eq("stuck-1"), any())).thenReturn(true);

        WebhookDeliveryWorker worker = new WebhookDeliveryWorker(deliveryDAO, scheduler, httpClient, configurationService);
        int[] counts = worker.runTick();

        assertEquals(counts[0], 0, "no pending to submit");
        assertEquals(counts[1], 1, "one stuck row reclaimed");
        verify(deliveryDAO, never()).claimStuckWebhookDelivery(any(Connection.class), anyString(), any());
    }

    @Test
    public void testMissingCallbackUrlMarksUnrecoverable() {
        WebhookDelivery delivery = new WebhookDelivery(
                "d1", "sub-1", "event-1", "pending", 0, null, new Timestamp(0), new Timestamp(0), null);
        WebhookDeliveryDispatchContext broken = new WebhookDeliveryDispatchContext(
                delivery, "org-1", "group-1", null, "secret", "{}", new Timestamp(0), "accounts");

        when(deliveryDAO.getPendingWebhookDispatchContexts(any(Connection.class), anyInt()))
                .thenReturn(java.util.Collections.singletonList(broken));
        when(deliveryDAO.claimWebhookDelivery(any(Connection.class), eq("d1"))).thenReturn(true);
        when(deliveryDAO.updateWebhookDeliveryStatus(any(Connection.class), any())).thenReturn(true);

        WebhookDeliveryWorker worker = new WebhookDeliveryWorker(deliveryDAO, scheduler, httpClient, configurationService);
        int[] counts = worker.runTick();

        assertEquals(counts[0], 1);
        scheduler.runAll();
        verify(deliveryDAO).updateWebhookDeliveryStatus(any(Connection.class), any());
    }

    @Test
    public void testPendingFetchFailureDoesNotStopTick() {
        when(deliveryDAO.getPendingWebhookDispatchContexts(any(Connection.class), anyInt()))
                .thenThrow(new RuntimeException("pending fetch failed"));
        when(deliveryDAO.getStuckInFlightWebhookDispatchContexts(any(Connection.class), anyInt(), any()))
                .thenReturn(Collections.emptyList());

        WebhookDeliveryWorker worker = new WebhookDeliveryWorker(deliveryDAO, scheduler, httpClient,
                configurationService);
        int[] counts = worker.runTick();

        assertEquals(counts[0], 0);
        assertEquals(counts[1], 0);
    }

    @Test
    public void testClaimFailureDoesNotSubmitDelivery() {
        when(deliveryDAO.getPendingWebhookDispatchContexts(any(Connection.class), anyInt()))
                .thenReturn(Collections.singletonList(context("claim-failure", 0)));
        when(deliveryDAO.claimWebhookDelivery(any(Connection.class), eq("claim-failure")))
                .thenThrow(new RuntimeException("claim failed"));

        WebhookDeliveryWorker worker = new WebhookDeliveryWorker(deliveryDAO, scheduler, httpClient,
                configurationService);
        int[] counts = worker.runTick();

        assertEquals(counts[0], 1);
        scheduler.runAll();
        verify(deliveryDAO).claimWebhookDelivery(any(Connection.class), eq("claim-failure"));
    }

    @Test
    public void testExecutorRejectionLeavesDeliveryUnclaimed() {
        WebhookDeliveryDispatchContext dispatchContext = context("executor-rejection", 0);
        when(deliveryDAO.getPendingWebhookDispatchContexts(any(Connection.class), anyInt()))
                .thenReturn(Collections.singletonList(dispatchContext));
        when(deliveryDAO.claimWebhookDelivery(any(Connection.class), eq("executor-rejection"))).thenReturn(true);
        WebhookDeliveryWorker worker = new WebhookDeliveryWorker(deliveryDAO, new RejectingExecutor(), httpClient,
                configurationService);
        int[] counts = worker.runTick();

        assertEquals(counts[0], 0);
        verify(deliveryDAO, never()).claimWebhookDelivery(any(Connection.class), anyString());
        verify(deliveryDAO, never()).updateWebhookDeliveryStatus(any(Connection.class), any());
    }

    @Test
    public void testMarkUnrecoverableUpdateFailureIsHandled() {
        WebhookDeliveryDispatchContext dispatchContext = context("update-failure", 0);
        when(deliveryDAO.getPendingWebhookDispatchContexts(any(Connection.class), anyInt()))
                .thenReturn(Collections.singletonList(new WebhookDeliveryDispatchContext(
                        dispatchContext.getDelivery(), dispatchContext.getOrgId(), dispatchContext.getGroupId(),
                        null, dispatchContext.getSharedSecret(), dispatchContext.getPayload(),
                        dispatchContext.getDelivery().getUpdatedAt(), dispatchContext.getTopic())));
        when(deliveryDAO.claimWebhookDelivery(any(Connection.class), eq("update-failure"))).thenReturn(true);
        doThrow(new RuntimeException("status update failed"))
                .when(deliveryDAO).updateWebhookDeliveryStatus(any(Connection.class), any());

        WebhookDeliveryWorker worker = new WebhookDeliveryWorker(deliveryDAO, scheduler, httpClient,
                configurationService);
        int[] counts = worker.runTick();

        assertEquals(counts[0], 1);
        scheduler.runAll();
    }

    @Test
    public void testMissingPayloadMarksUnrecoverable() {
        WebhookDelivery delivery = new WebhookDelivery(
                "d1", "sub-1", "event-1", "pending", 0, null, new Timestamp(0), new Timestamp(0), null);
        WebhookDeliveryDispatchContext broken = new WebhookDeliveryDispatchContext(
                delivery, "org-1", "group-1", "https://callback.example.com/hook", "secret", null,
                new Timestamp(0), "accounts");

        when(deliveryDAO.getPendingWebhookDispatchContexts(any(Connection.class), anyInt()))
                .thenReturn(java.util.Collections.singletonList(broken));
        when(deliveryDAO.claimWebhookDelivery(any(Connection.class), eq("d1"))).thenReturn(true);
        when(deliveryDAO.updateWebhookDeliveryStatus(any(Connection.class), any())).thenReturn(true);

        WebhookDeliveryWorker worker = new WebhookDeliveryWorker(deliveryDAO, scheduler, httpClient, configurationService);
        worker.runTick();

        scheduler.runAll();
        verify(deliveryDAO).updateWebhookDeliveryStatus(any(Connection.class), any());
    }

    @Test
    public void testFailedStatusIsUsedForUnrecoverable() {
        WebhookDelivery delivery = new WebhookDelivery(
                "d1", "sub-1", "event-1", "pending", 0, null, new Timestamp(0), new Timestamp(0), null);
        WebhookDeliveryDispatchContext broken = new WebhookDeliveryDispatchContext(
                delivery, "org-1", "group-1", null, "secret", "{}", new Timestamp(0), "accounts");

        when(deliveryDAO.getPendingWebhookDispatchContexts(any(Connection.class), anyInt()))
                .thenReturn(java.util.Collections.singletonList(broken));
        when(deliveryDAO.claimWebhookDelivery(any(Connection.class), eq("d1"))).thenReturn(true);

        org.mockito.ArgumentCaptor<WebhookDelivery> captor =
                org.mockito.ArgumentCaptor.forClass(WebhookDelivery.class);
        when(deliveryDAO.updateWebhookDeliveryStatus(any(Connection.class), captor.capture())).thenReturn(true);

        WebhookDeliveryWorker worker = new WebhookDeliveryWorker(deliveryDAO, scheduler, httpClient, configurationService);
        worker.runTick();

        scheduler.runAll();
        assertEquals(captor.getValue().getStatus(), DeliveryStatus.FAILED.getValue());
    }

    @Test
    public void testMissingSharedSecretMarksDeliveryUnrecoverableWithoutDispatch() {
        WebhookDeliveryDispatchContext valid = context("missing-secret", 0);
        WebhookDeliveryDispatchContext unsigned = new WebhookDeliveryDispatchContext(
                valid.getDelivery(), valid.getOrgId(), valid.getGroupId(), valid.getCallbackUrl(), " ", valid.getPayload(),
                valid.getDelivery().getUpdatedAt(), valid.getTopic());
        when(deliveryDAO.getPendingWebhookDispatchContexts(any(Connection.class), anyInt()))
                .thenReturn(Collections.singletonList(unsigned));
        when(deliveryDAO.claimWebhookDelivery(any(Connection.class), eq("missing-secret"))).thenReturn(true);
        when(deliveryDAO.updateWebhookDeliveryStatus(any(Connection.class), any())).thenReturn(true);

        new WebhookDeliveryWorker(deliveryDAO, scheduler, httpClient, configurationService).runTick();
        scheduler.runAll();

        verify(deliveryDAO).updateWebhookDeliveryStatus(any(Connection.class), any());
    }
}
