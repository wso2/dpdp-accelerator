package org.wso2.dpdp.accelerator.event.notifications.service.impl;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.common.constant.DPDPCommonConstants;
import org.wso2.dpdp.accelerator.common.persistence.JDBCPersistenceManager;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryAckDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.PaginatedDAOResult;
import org.wso2.dpdp.accelerator.event.notifications.dao.SubscriptionDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.TopicDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Subscription;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.SubscriptionDeliverySummary;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDelivery;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDeliveryAck;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDeliveryAudit;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionEventHistoryDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dispatch.WebhookDeliveryWorker;
import org.wso2.dpdp.accelerator.event.notifications.service.model.PaginatedResult;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.sql.Connection;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class SubscriptionServiceReadAndDeleteTest {

    @Mock private SubscriptionDAO subscriptionDAO;
    @Mock private TopicDAO topicDAO;
    @Mock private DeliveryDAO deliveryDAO;
    @Mock private DeliveryAckDAO deliveryAckDAO;
    @Mock private DPDPConfigurationService configurationService;
    @Mock private Connection connection;

    private SubscriptionServiceImpl service;

    @Test(expectedExceptions = org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException.class)
    public void listSubscriptionsRequiresOrganization() {
        service.listSubscriptions(" ", null, null, null, 1, 0, null);
    }

    @Test(expectedExceptions = org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException.class)
    public void listSubscriptionEventsRequiresSubscription() {
        service.listSubscriptionEvents("org-1", " ", 1, 0);
    }

    @Test(expectedExceptions = org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException.class)
    public void subscriptionHistoryRequiresDelivery() {
        service.getSubscriptionEventHistory("org-1", "sub-1", " ");
    }

    @Test(expectedExceptions = org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException.class)
    public void retryVerificationRejectsActiveSubscription() {
        Subscription active = subscription("sub-1", "topic-1", "active");
        when(subscriptionDAO.getSubscriptionById(any(Connection.class), eq("sub-1"), eq("org-1")))
                .thenReturn(Optional.of(active));
        service.retryVerification("org-1", "sub-1");
    }

    @Test(expectedExceptions = org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException.class)
    public void getSubscriptionRequiresId() {
        service.getSubscription("org-1", " ");
    }

    @Test(expectedExceptions = org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException.class)
    public void subscriptionHistoryReportsMissingSubscription() {
        when(subscriptionDAO.getSubscriptionById(any(Connection.class), eq("missing"), eq("org-1"))).thenReturn(Optional.empty());
        service.getSubscriptionEventHistory("org-1", "missing", "delivery");
    }

    @Test(expectedExceptions = org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException.class)
    public void subscriptionHistoryReportsMissingDelivery() {
        Subscription sub = subscription("sub-1", "topic-1", "active");
        when(subscriptionDAO.getSubscriptionById(any(Connection.class), eq("sub-1"), eq("org-1"))).thenReturn(Optional.of(sub));
        when(deliveryDAO.getSubscriptionDeliveryById(any(Connection.class), eq("org-1"), eq("sub-1"), eq("missing")))
                .thenReturn(Optional.empty());
        service.getSubscriptionEventHistory("org-1", "sub-1", "missing");
    }

    @Test(expectedExceptions = org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException.class)
    public void getSubscriptionRequiresOrganization() {
        service.getSubscription(" ", "sub-1");
    }

    @Test(expectedExceptions = org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException.class)
    public void listSubscriptionEventsRequiresOrganization() {
        service.listSubscriptionEvents(" ", "sub-1", 1, 0);
    }

    @Test(expectedExceptions = org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException.class)
    public void deleteSubscriptionReportsMissingResource() {
        when(subscriptionDAO.getSubscriptionById(any(Connection.class), eq("missing"), eq("org-1"))).thenReturn(Optional.empty());
        service.deleteSubscription("org-1", "missing");
    }

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        when(configurationService.getEventNotificationThreadPoolSize()).thenReturn(1);
        when(configurationService.getEventNotificationBaseBackoffSeconds()).thenReturn(1L);
        when(configurationService.getEventNotificationMaxRetries()).thenReturn(1);
        when(configurationService.isEventNotificationHttpCallbackUrlAllowed()).thenReturn(true);
        when(configurationService.getEventNotificationAllowedCallbackPorts())
                .thenReturn(DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_ALLOWED_CALLBACK_PORTS);
        when(configurationService.isEventNotificationPrivateNetworkCallbackTargetsAllowed()).thenReturn(false);
        when(configurationService.getEventNotificationMaxVerificationResponseBodyBytes()).thenReturn(4096);
        DataSource dataSource = org.mockito.Mockito.mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        setStaticInstance(null);
        setStaticDataSource(dataSource);
        when(subscriptionDAO.lockSubscriptionForVerification(eq(connection), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> Optional.of(webhookSubscription(invocation.getArgument(1),
                        invocation.getArgument(3))));
        when(subscriptionDAO.updateSubscriptionStatus(eq(connection), anyString(), anyString(), anyString(),
                anyString())).thenReturn(true);
        service = new SubscriptionServiceImpl(subscriptionDAO, topicDAO, deliveryDAO, deliveryAckDAO,
                configurationService);
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
    public void listSubscriptionsMapsItemsAndNormalizesPagination() {
        Subscription sub = subscription("sub-1", "topic-1", "active");
        when(subscriptionDAO.listSubscriptions(any(Connection.class), eq("org-1"), eq("active"), eq("p"), eq("search"),
                anyInt(), eq(0), eq("createdAt")))
                .thenReturn(new PaginatedDAOResult<>(Collections.singletonList(sub), 3));
        when(topicDAO.getTopicById(any(Connection.class), eq("topic-1"), eq("org-1"))).thenReturn(Optional.empty());

        PaginatedResult<?> result = service.listSubscriptions(" org-1 ", " ACTIVE ", "p", "search", 0, -1,
                "createdAt");

        assertEquals(result.getTotal(), 3);
        assertEquals(result.getItems().size(), 1);
        assertEquals(((org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDTO)
                result.getItems().get(0)).getTopic(), "unknown");
        verify(subscriptionDAO).listSubscriptions(any(Connection.class), eq("org-1"), eq("active"), eq("p"), eq("search"), eq(20), eq(0), eq("createdAt"));
    }

    @Test(expectedExceptions = org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException.class)
    public void getSubscriptionRejectsMissingSubscription() {
        when(subscriptionDAO.getSubscriptionById(any(Connection.class), eq("missing"), eq("org-1"))).thenReturn(Optional.empty());
        service.getSubscription("org-1", "missing");
    }

    @Test
    public void listSubscriptionEventsUsesFallbackValues() {
        Subscription sub = subscription("sub-1", "topic-1", "active");
        when(subscriptionDAO.getSubscriptionById(any(Connection.class), eq("sub-1"), eq("org-1"))).thenReturn(Optional.of(sub));
        SubscriptionDeliverySummary summary = new SubscriptionDeliverySummary("del-1", "evt-1", "sub-1",
                "topic", null, null, null, new Timestamp(1000), null);
        when(deliveryDAO.listSubscriptionDeliveries(any(Connection.class), eq("org-1"), eq("sub-1"), anyInt(), eq(0), any(int[].class)))
                .thenReturn(Collections.singletonList(summary));

        PaginatedResult<?> result = service.listSubscriptionEvents("org-1", "sub-1", 0, -1);

        assertEquals(result.getTotal(), 0);
        assertEquals(result.getItems().size(), 1);
        verify(deliveryDAO).listSubscriptionDeliveries(any(Connection.class), eq("org-1"), eq("sub-1"), eq(20), eq(0), any(int[].class));
    }

    @Test
    public void webhookHistoryMapsAckAndAuditAttempts() {
        Subscription sub = subscription("sub-1", "topic-1", "active");
        when(subscriptionDAO.getSubscriptionById(any(Connection.class), eq("sub-1"), eq("org-1"))).thenReturn(Optional.of(sub));
        SubscriptionDeliverySummary summary = new SubscriptionDeliverySummary("del-1", "evt-1", "sub-1",
                "topic", "FAILED", "webhook", new Timestamp(1000), new Timestamp(900), null);
        when(deliveryDAO.getSubscriptionDeliveryById(any(Connection.class), eq("org-1"), eq("sub-1"), eq("del-1")))
                .thenReturn(Optional.of(summary));
        when(deliveryDAO.getWebhookDeliveryById(any(Connection.class), eq("del-1"), eq("org-1")))
                .thenReturn(Optional.of(new WebhookDelivery("del-1", "sub-1", "evt-1", "FAILED", 1,
                        new Timestamp(2000), null, null, null)));
        when(deliveryAckDAO.getDeliveryAckByDeliveryId(any(Connection.class), eq("del-1")))
                .thenReturn(Optional.of(new WebhookDeliveryAck("ack", "del-1", null, "COMPLETED", "evidence")));
        when(deliveryDAO.getWebhookDeliveryAudits(any(Connection.class), eq("del-1"), eq("org-1")))
                .thenReturn(Arrays.asList(
                        new WebhookDeliveryAudit("a1", "evt-1", "del-1", "org-1", "200", null, new Timestamp(3000)),
                        new WebhookDeliveryAudit("a2", "evt-1", "del-1", "org-1", "500", null, null)));

        SubscriptionEventHistoryDTO result = service.getSubscriptionEventHistory("org-1", "sub-1", "del-1");

        assertNotNull(result);
        assertEquals(result.getCompletionStatus(), "COMPLETED");
        assertEquals(result.getCompletionEvidence(), "evidence");
        assertEquals(result.getHistory().size(), 2);
        assertEquals(result.getNextRetryAt().longValue(), 2000L);
        assertTrue(!result.isManualRetryAvailable());
    }

    @Test
    public void webhookHistoryExposesManualRetryAvailabilityAfterExhaustion() {
        Subscription sub = subscription("sub-1", "topic-1", "active");
        when(subscriptionDAO.getSubscriptionById(any(Connection.class), eq("sub-1"), eq("org-1")))
                .thenReturn(Optional.of(sub));
        SubscriptionDeliverySummary summary = new SubscriptionDeliverySummary("del-1", "evt-1", "sub-1",
                "topic", "failed", "webhook", new Timestamp(1000), new Timestamp(900), null);
        when(deliveryDAO.getSubscriptionDeliveryById(any(Connection.class), eq("org-1"), eq("sub-1"),
                eq("del-1"))).thenReturn(Optional.of(summary));
        when(deliveryDAO.getWebhookDeliveryById(any(Connection.class), eq("del-1"), eq("org-1")))
                .thenReturn(Optional.of(new WebhookDelivery("del-1", "sub-1", "evt-1", "failed", 2,
                        null, null, null, null, false)));
        when(deliveryDAO.getWebhookDeliveryAudits(any(Connection.class), eq("del-1"), eq("org-1")))
                .thenReturn(Collections.emptyList());

        SubscriptionEventHistoryDTO result = service.getSubscriptionEventHistory("org-1", "sub-1", "del-1");

        assertTrue(result.isManualRetryAvailable());
        assertTrue(!result.isManualRetryUsed());
    }

    @Test
    public void retryDeliveryMapsMissingDeliveryToNotFound() {
        service.setManualRetryDispatcher((orgId, subscriptionId, deliveryId) ->
                WebhookDeliveryWorker.ManualRetrySubmissionResult.NOT_FOUND);

        org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException exception =
                org.testng.Assert.expectThrows(
                        org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException.class,
                        () -> service.retryDelivery("org-1", "sub-1", "missing"));

        assertEquals(exception.getStatusCode(), 404);
    }

    @Test
    public void retryDeliveryMapsIneligibleDeliveryToConflict() {
        service.setManualRetryDispatcher((orgId, subscriptionId, deliveryId) ->
                WebhookDeliveryWorker.ManualRetrySubmissionResult.NOT_ELIGIBLE);

        org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException exception =
                org.testng.Assert.expectThrows(
                        org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException.class,
                        () -> service.retryDelivery("org-1", "sub-1", "del-1"));

        assertEquals(exception.getStatusCode(), 409);
    }

    @Test
    public void deleteSubscriptionReportsInFlightConflict() {
        Subscription sub = subscription("sub-1", "topic-1", "active");
        when(subscriptionDAO.getSubscriptionById(any(Connection.class), eq("sub-1"), eq("org-1"))).thenReturn(Optional.of(sub));
        when(subscriptionDAO.deleteSubscriptionAtomic(any(Connection.class), eq("sub-1"), eq("org-1"), eq("active"))).thenReturn(false);
        when(subscriptionDAO.hasPendingOrInFlightDeliveries(any(Connection.class), eq("sub-1"), eq("org-1"))).thenReturn(true);

        try {
            service.deleteSubscription("org-1", "sub-1");
        } catch (org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException e) {
            assertEquals(e.getStatusCode(), 409);
        }
    }

    @Test
    public void verificationTaskRetriesWhenCallbackFails() throws Exception {
        java.lang.Class<?> taskClass = java.util.Arrays.stream(SubscriptionServiceImpl.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("WebhookVerificationTask")).findFirst().get();
        java.lang.reflect.Constructor<?> constructor = taskClass.getDeclaredConstructor(SubscriptionServiceImpl.class,
                String.class, String.class, String.class, String.class, int.class);
        constructor.setAccessible(true);
        Runnable task = (Runnable) constructor.newInstance(service, "sub-1", "org-1", "not-a-url", "topic", 0);
        task.run();
        verify(configurationService).getEventNotificationMaxRetries();
    }

    @Test
    public void verificationTaskMarksStaleAfterRetries() throws Exception {
        java.lang.Class<?> taskClass = java.util.Arrays.stream(SubscriptionServiceImpl.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("WebhookVerificationTask")).findFirst().get();
        java.lang.reflect.Constructor<?> constructor = taskClass.getDeclaredConstructor(SubscriptionServiceImpl.class,
                String.class, String.class, String.class, String.class, int.class);
        constructor.setAccessible(true);
        Runnable task = (Runnable) constructor.newInstance(service, "sub-1", "org-1", "not-a-url", "topic", 1);
        task.run();
        verify(subscriptionDAO).updateSubscriptionStatus(connection, "sub-1", "org-1", "pending", "stale");
    }

    @Test
    public void verificationTaskActivatesSubscriptionWhenChallengeMatches() throws Exception {
        installSuccessfulVerificationClient();
        java.lang.Class<?> taskClass = java.util.Arrays.stream(SubscriptionServiceImpl.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("WebhookVerificationTask")).findFirst().get();
        java.lang.reflect.Constructor<?> constructor = taskClass.getDeclaredConstructor(SubscriptionServiceImpl.class,
                String.class, String.class, String.class, String.class, int.class);
        constructor.setAccessible(true);
        ((Runnable) constructor.newInstance(service, "sub-1", "org-1", "https://93.184.216.34:443/callback",
                "topic", 0)).run();
        verify(subscriptionDAO).updateSubscriptionStatus(connection, "sub-1", "org-1", "pending", "active");
        verify(connection).commit();
        verify(connection).close();
    }

    @Test
    public void verificationAcceptsResponseExactlyAtConfiguredLimit() throws Exception {
        when(configurationService.getEventNotificationMaxVerificationResponseBodyBytes()).thenReturn(36);
        prepareRetryVerification(webhookSubscription("sub-1", "pending"));
        installSuccessfulVerificationClient();

        service.retryVerification("org-1", "sub-1");

        verify(subscriptionDAO).updateSubscriptionStatus(connection, "sub-1", "org-1", "pending", "active");
    }

    @Test
    public void verificationRejectsOversizedResponseAndClosesStream() throws Exception {
        when(configurationService.getEventNotificationMaxVerificationResponseBodyBytes()).thenReturn(36);
        prepareRetryVerification(webhookSubscription("sub-1", "pending"));
        TrackingInputStream responseBody = new TrackingInputStream(new byte[4096]);
        installVerificationClient(request -> responseBody);

        org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException exception =
                org.testng.Assert.expectThrows(
                        org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException.class,
                        () -> service.retryVerification("org-1", "sub-1"));

        assertEquals(exception.getStatusCode(), 422);
        assertTrue(responseBody.closed);
        assertEquals(responseBody.bytesRead, 37);
        verify(subscriptionDAO, never()).updateSubscriptionStatus(connection, "sub-1", "org-1", "pending", "active");
    }

    @Test
    public void retryVerificationUsesGuardedExpectedStatusTransition() throws Exception {
        Subscription pending = webhookSubscription("sub-1", "pending");
        when(subscriptionDAO.getSubscriptionById(any(Connection.class), eq("sub-1"), eq("org-1"))).thenReturn(Optional.of(pending));
        when(topicDAO.getTopicById(any(Connection.class), eq("topic-1"), eq("org-1")))
                .thenReturn(Optional.of(new org.wso2.dpdp.accelerator.event.notifications.dao.model.Topic(
                        "topic-1", "org-1", "accounts", "", "active")));
        when(subscriptionDAO.updateSubscriptionStatus(any(Connection.class), eq("sub-1"), eq("org-1"), eq("pending"), eq("active")))
                .thenReturn(true);
        installSuccessfulVerificationClient();

        org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDTO result =
                service.retryVerification("org-1", "sub-1");

        assertEquals(result.getStatus().getValue(), "active");
        verify(subscriptionDAO).updateSubscriptionStatus(connection, "sub-1", "org-1", "pending", "active");
        verify(subscriptionDAO, never()).updateSubscriptionStatus(any(Connection.class), eq("sub-1"), eq("org-1"), eq("active"));
    }

    @Test
    public void retryVerificationCannotReactivateConcurrentlyDeletedSubscription() throws Exception {
        Subscription pending = webhookSubscription("sub-1", "pending");
        Subscription deleted = webhookSubscription("sub-1", "deleted");
        when(subscriptionDAO.getSubscriptionById(any(Connection.class), eq("sub-1"), eq("org-1")))
                .thenReturn(Optional.of(pending), Optional.of(deleted));
        when(subscriptionDAO.lockSubscriptionForVerification(connection, "sub-1", "org-1", "pending"))
                .thenReturn(Optional.empty());
        when(topicDAO.getTopicById(any(Connection.class), eq("topic-1"), eq("org-1")))
                .thenReturn(Optional.of(new org.wso2.dpdp.accelerator.event.notifications.dao.model.Topic(
                        "topic-1", "org-1", "accounts", "", "active")));
        installSuccessfulVerificationClient();

        org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException exception =
                org.testng.Assert.expectThrows(
                        org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException.class,
                        () -> service.retryVerification("org-1", "sub-1"));

        assertEquals(exception.getStatusCode(), 404);
        verify(subscriptionDAO, never()).updateSubscriptionStatus(connection, "sub-1", "org-1", "pending", "active");
        verify(subscriptionDAO, never()).updateSubscriptionStatus(any(Connection.class), eq("sub-1"), eq("org-1"), eq("active"));
    }

    private void installSuccessfulVerificationClient() throws Exception {
        installVerificationClient(request -> {
            String query = request.uri().getQuery();
            String encoded = Arrays.stream(query.split("&"))
                    .filter(part -> part.startsWith("hub.challenge="))
                    .findFirst().get().substring("hub.challenge=".length());
            return new ByteArrayInputStream(URLDecoder.decode(encoded, StandardCharsets.UTF_8)
                    .getBytes(StandardCharsets.UTF_8));
        });
    }

    private void installVerificationClient(Function<HttpRequest, InputStream> bodyProvider) throws Exception {
        HttpClient httpClient = org.mockito.Mockito.mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<InputStream> response = (HttpResponse<InputStream>) org.mockito.Mockito.mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        doAnswer(invocation -> {
            HttpRequest request = invocation.getArgument(0);
            when(response.body()).thenReturn(bodyProvider.apply(request));
            return response;
        }).when(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        java.lang.reflect.Field field = SubscriptionServiceImpl.class.getDeclaredField("httpClient");
        field.setAccessible(true);
        field.set(service, httpClient);
    }

    private void prepareRetryVerification(Subscription subscription) {
        when(subscriptionDAO.getSubscriptionById(any(Connection.class), eq(subscription.getSubscriptionId()), eq(subscription.getOrgId())))
                .thenReturn(Optional.of(subscription));
        when(topicDAO.getTopicById(any(Connection.class), eq(subscription.getTopicId()), eq(subscription.getOrgId())))
                .thenReturn(Optional.of(new org.wso2.dpdp.accelerator.event.notifications.dao.model.Topic(
                        subscription.getTopicId(), subscription.getOrgId(), "accounts", "", "active")));
        when(subscriptionDAO.updateSubscriptionStatus(any(Connection.class), eq(subscription.getSubscriptionId()), eq(subscription.getOrgId()),
                eq("pending"), eq("active"))).thenReturn(true);
    }

    private Subscription webhookSubscription(String id, String status) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        return new Subscription(id, "org-1", "group-1", "topic-1", "ALL", Collections.emptyList(),
                "WEBHOOK", "https://93.184.216.34:443/callback", "secret", status, now, now);
    }

    private Subscription subscription(String id, String topicId, String status) {
        Subscription sub = org.mockito.Mockito.mock(Subscription.class);
        when(sub.getSubscriptionId()).thenReturn(id);
        when(sub.getOrgId()).thenReturn("org-1");
        when(sub.getGroupId()).thenReturn("group-1");
        when(sub.getTopicId()).thenReturn(topicId);
        when(sub.getStatus()).thenReturn(status);
        when(sub.getPurposeFilterMode()).thenReturn("ALL");
        when(sub.getDeliveryMode()).thenReturn("POLL");
        when(sub.getPurposes()).thenReturn(Collections.emptyList());
        return sub;
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {

        private int bytesRead;
        private boolean closed;

        private TrackingInputStream(byte[] buffer) {
            super(buffer);
        }

        @Override
        public synchronized int read(byte[] target, int offset, int length) {
            int count = super.read(target, offset, length);
            if (count > 0) {
                bytesRead += count;
            }
            return count;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
