/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 */
package org.wso2.dpdp.accelerator.event.notifications.service.recovery;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.common.persistence.JDBCPersistenceManager;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.SubscriptionDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Subscription;
import org.wso2.dpdp.accelerator.event.notifications.service.SubscriptionService;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

import javax.sql.DataSource;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class DeliveryRecoveryServiceTest {

    @Mock
    private SubscriptionDAO subscriptionDAO;
    @Mock
    private DeliveryDAO deliveryDAO;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private DPDPConfigurationService configurationService;

    private DeliveryRecoveryService recoveryService;
    private Connection connection;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        connection = mock(Connection.class);
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        setStaticInstance(null);
        setStaticDataSource(dataSource);

        when(configurationService.getEventNotificationThreadPoolSize()).thenReturn(2);
        when(configurationService.getEventNotificationDeliveryWorkerPollSeconds()).thenReturn(5);
        when(configurationService.getEventNotificationDeliveryWorkerBatchSize()).thenReturn(20);
        when(configurationService.getEventNotificationStuckInFlightThresholdSeconds()).thenReturn(10);
        when(configurationService.getEventNotificationPendingSubscriptionRecoveryThresholdSeconds())
                .thenReturn(60);
        when(configurationService.getEventNotificationBackgroundWorkerInitialDelaySeconds()).thenReturn(10);
        when(configurationService.getEventNotificationPendingSubscriptionRecoveryIntervalSeconds()).thenReturn(30);
        when(configurationService.getEventNotificationPendingSubscriptionRecoveryBatchSize()).thenReturn(20);
        when(configurationService.getEventNotificationPendingSubscriptionRecoveryMaxBatchesPerRun()).thenReturn(10);
        when(configurationService.getEventNotificationPendingSubscriptionRecoveryMaxRunSeconds()).thenReturn(25);
        when(configurationService.getEventNotificationWorkerShutdownTimeoutSeconds()).thenReturn(5);
        recoveryService = new DeliveryRecoveryService(subscriptionDAO, deliveryDAO,
                subscriptionService, configurationService);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        setStaticDataSource(null);
        setStaticInstance(null);
    }

    private static void setStaticDataSource(DataSource dataSource) throws Exception {
        Field field = JDBCPersistenceManager.class.getDeclaredField("dataSource");
        field.setAccessible(true);
        field.set(null, dataSource);
    }

    private static void setStaticInstance(JDBCPersistenceManager instance) throws Exception {
        Field field = JDBCPersistenceManager.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, instance);
    }

    @Test
    public void pendingSubscriptionsWithCallbacksAreRetried() throws Exception {
        Subscription retryable = subscription("retryable", "https://example.com:443/callback");
        Subscription withoutCallback = subscription("without-callback", " ");
        when(subscriptionDAO.getPendingSubscriptionsForRecovery(any(Connection.class), any(Timestamp.class), anyInt()))
                .thenReturn(Arrays.asList(retryable, withoutCallback));
        runPendingRecoveryTask();

        verify(subscriptionService).retryVerification("org1", "retryable");
    }

    @Test
    public void retryFailureDoesNotAbortOtherRecoveryRuns() throws Exception {
        Subscription retryable = subscription("retryable", "https://example.com:443/callback");
        when(subscriptionDAO.getPendingSubscriptionsForRecovery(any(Connection.class), any(Timestamp.class), anyInt()))
                .thenReturn(Collections.singletonList(retryable));
        doThrow(new RuntimeException("verification unavailable"))
                .when(subscriptionService).retryVerification("org1", "retryable");

        runPendingRecoveryTask();

        verify(subscriptionService).retryVerification("org1", "retryable");
    }

    @Test
    public void recoveryDelegatesClaimOwnershipToSubscriptionService() throws Exception {
        Subscription retryable = subscription("already-claimed", "https://example.com:443/callback");
        when(subscriptionDAO.getPendingSubscriptionsForRecovery(any(Connection.class), any(Timestamp.class), anyInt()))
                .thenReturn(Collections.singletonList(retryable));

        runPendingRecoveryTask();

        verify(subscriptionService).retryVerification("org1", "already-claimed");
    }

    @Test
    public void serviceCanActivateAndDeactivate() {
        recoveryService.activate();
        recoveryService.deactivate();
        recoveryService.deactivate();
    }

    @Test
    public void workerExecutorQueueIsBoundedByConfiguredBatchSize() throws Exception {
        recoveryService.activate();
        try {
            Field workerPoolField = DeliveryRecoveryService.class.getDeclaredField("workerPool");
            workerPoolField.setAccessible(true);
            ThreadPoolExecutor workerPool = (ThreadPoolExecutor) workerPoolField.get(recoveryService);

            assertEquals(workerPool.getCorePoolSize(), 2);
            assertEquals(workerPool.getQueue().remainingCapacity(), 20);
        } finally {
            recoveryService.deactivate();
        }
    }

    @Test
    public void activationRejectsStuckThresholdAtHttpTimeout() {
        when(configurationService.getEventNotificationStuckInFlightThresholdSeconds()).thenReturn(5);

        IllegalStateException error = expectThrows(IllegalStateException.class, recoveryService::activate);

        assertTrue(error.getMessage().contains("must be at least 10 seconds"));
    }

    @Test
    public void activationRejectsStuckThresholdWithoutMargin() {
        when(configurationService.getEventNotificationStuckInFlightThresholdSeconds()).thenReturn(8);

        IllegalStateException error = expectThrows(IllegalStateException.class, recoveryService::activate);

        assertTrue(error.getMessage().contains("must be at least 10 seconds"));
    }

    @Test
    public void activationRejectsVerificationRecoveryThresholdAtHttpTimeout() {
        when(configurationService.getEventNotificationPendingSubscriptionRecoveryThresholdSeconds()).thenReturn(5);

        IllegalStateException error = expectThrows(IllegalStateException.class, recoveryService::activate);

        assertTrue(error.getMessage().contains("pending subscription recovery threshold"));
    }

    @Test
    public void pendingSubscriptionsDrainedAcrossBatches() throws Exception {
        when(configurationService.getEventNotificationPendingSubscriptionRecoveryBatchSize()).thenReturn(10);
        List<Subscription> allSubs = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            allSubs.add(subscription("sub-" + i, "https://example.com/callback/" + i));
        }

        when(subscriptionDAO.getPendingSubscriptionsForRecovery(any(Connection.class), any(Timestamp.class), anyInt()))
                .thenAnswer(inv -> {
                    int limit = inv.getArgument(2);
                    return new ArrayList<>(allSubs.subList(0, Math.min(limit, allSubs.size())));
                });

        runPendingRecoveryTask();

        for (int i = 0; i < 25; i++) {
            verify(subscriptionService).retryVerification("org1", "sub-" + i);
        }
    }

    @Test
    public void failingPendingSubscriptionsTouchedAndNotReQueried() throws Exception {
        when(configurationService.getEventNotificationPendingSubscriptionRecoveryBatchSize()).thenReturn(5);
        Subscription s1 = subscription("sub-succeed-1", "https://example.com/1");
        Subscription s2 = subscription("sub-fail-1", "https://example.com/2");
        Subscription s3 = subscription("sub-succeed-2", "https://example.com/3");
        Subscription s4 = subscription("sub-fail-2", "https://example.com/4");

        when(subscriptionDAO.getPendingSubscriptionsForRecovery(any(Connection.class), any(Timestamp.class), anyInt()))
                .thenReturn(Arrays.asList(s1, s2, s3, s4));
        doThrow(new RuntimeException("verification network timeout"))
                .when(subscriptionService).retryVerification("org1", "sub-fail-1");
        doThrow(new RuntimeException("verification 500 error"))
                .when(subscriptionService).retryVerification("org1", "sub-fail-2");

        runPendingRecoveryTask();

        // Successful ones are not touched
        verify(subscriptionDAO, never()).updateSubscriptionStatus(any(Connection.class),
                eq("sub-succeed-1"), eq("org1"), eq("pending"), eq("pending"));
        verify(subscriptionDAO, never()).updateSubscriptionStatus(any(Connection.class),
                eq("sub-succeed-2"), eq("org1"), eq("pending"), eq("pending"));

        // Failing ones are touched
        verify(subscriptionDAO).updateSubscriptionStatus(any(Connection.class),
                eq("sub-fail-1"), eq("org1"), eq("pending"), eq("pending"));
        verify(subscriptionDAO).updateSubscriptionStatus(any(Connection.class),
                eq("sub-fail-2"), eq("org1"), eq("pending"), eq("pending"));
    }

    @Test
    public void blankCallbackUrlSubscriptionIsTouchedWithoutRetry() throws Exception {
        Subscription blank = subscription("sub-blank", "   ");
        when(subscriptionDAO.getPendingSubscriptionsForRecovery(any(Connection.class), any(Timestamp.class), anyInt()))
                .thenReturn(Collections.singletonList(blank));

        runPendingRecoveryTask();

        verify(subscriptionService, never()).retryVerification(any(), any());
        verify(subscriptionDAO).updateSubscriptionStatus(any(Connection.class),
                eq("sub-blank"), eq("org1"), eq("pending"), eq("pending"));
    }

    @Test
    public void drainLoopStopsAtMaxBatches() throws Exception {
        when(configurationService.getEventNotificationPendingSubscriptionRecoveryBatchSize()).thenReturn(1);
        when(configurationService.getEventNotificationPendingSubscriptionRecoveryMaxBatchesPerRun()).thenReturn(3);
        List<Subscription> allSubs = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            allSubs.add(subscription("sub-" + i, "https://example.com/" + i));
        }

        when(subscriptionDAO.getPendingSubscriptionsForRecovery(any(Connection.class), any(Timestamp.class), anyInt()))
                .thenAnswer(inv -> {
                    int limit = inv.getArgument(2);
                    return new ArrayList<>(allSubs.subList(0, Math.min(limit, allSubs.size())));
                });

        runPendingRecoveryTask();

        // Configured max batches = 3, so exactly 3 batches fetched
        verify(subscriptionDAO, times(3))
                .getPendingSubscriptionsForRecovery(any(Connection.class), any(Timestamp.class), anyInt());
        for (int i = 0; i < 3; i++) {
            verify(subscriptionService).retryVerification("org1", "sub-" + i);
        }
        verify(subscriptionService, never()).retryVerification("org1", "sub-3");
    }

    @Test
    public void drainLoopStopsImmediatelyWhenStoppingIsTrue() throws Exception {
        Field stoppingField = DeliveryRecoveryService.class.getDeclaredField("stopping");
        stoppingField.setAccessible(true);
        stoppingField.set(recoveryService, true);

        runPendingRecoveryTask();

        verify(subscriptionDAO, never())
                .getPendingSubscriptionsForRecovery(any(Connection.class), any(Timestamp.class), anyInt());
    }

    @Test
    public void failedTouchPendingDoesNotAbortDrainLoop() throws Exception {
        when(configurationService.getEventNotificationPendingSubscriptionRecoveryBatchSize()).thenReturn(5);
        Subscription s1 = subscription("sub-fail-touch-err", "https://example.com/1");
        Subscription s2 = subscription("sub-normal", "https://example.com/2");

        when(subscriptionDAO.getPendingSubscriptionsForRecovery(any(Connection.class), any(Timestamp.class), anyInt()))
                .thenReturn(Arrays.asList(s1, s2));

        doThrow(new RuntimeException("verification failed"))
                .when(subscriptionService).retryVerification("org1", "sub-fail-touch-err");
        doThrow(new RuntimeException("DB touch error"))
                .when(subscriptionDAO).updateSubscriptionStatus(any(Connection.class),
                        eq("sub-fail-touch-err"), eq("org1"), eq("pending"), eq("pending"));

        runPendingRecoveryTask();

        // s2 should still be processed despite s1's touch failure
        verify(subscriptionService).retryVerification("org1", "sub-normal");
    }

    private void runPendingRecoveryTask() throws Exception {
        Class<?> taskClass = Arrays.stream(DeliveryRecoveryService.class.getDeclaredClasses())
                .filter(clazz -> clazz.getSimpleName().equals("PendingDeliveryRecoveryTask"))
                .findFirst()
                .orElseThrow();
        Constructor<?> constructor = taskClass.getDeclaredConstructor(DeliveryRecoveryService.class);
        constructor.setAccessible(true);
        Runnable task = (Runnable) constructor.newInstance(recoveryService);
        task.run();
    }

    private Subscription subscription(String id, String callbackUrl) {
        Subscription s = new Subscription();
        s.setSubscriptionId(id);
        s.setOrgId("org1");
        s.setGroupId("group1");
        s.setTopicIds(Collections.singletonList("topic1"));
        s.setTopicNames(Collections.emptyList());
        s.setPurposeFilterMode("ALL");
        s.setPurposes(Collections.emptyList());
        s.setDeliveryMode("WEBHOOK");
        s.setCallbackUrl(callbackUrl);
        s.setSharedSecret("secret");
        s.setStatus("PENDING");
        s.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        s.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        return s;
    }
}
