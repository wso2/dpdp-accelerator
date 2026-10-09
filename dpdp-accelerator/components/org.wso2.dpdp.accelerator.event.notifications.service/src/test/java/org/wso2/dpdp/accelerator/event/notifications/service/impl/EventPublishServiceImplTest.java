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

package org.wso2.dpdp.accelerator.event.notifications.service.impl;

import org.mockito.ArgumentCaptor;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.common.persistence.JDBCPersistenceManager;
import org.wso2.dpdp.accelerator.event.notifications.common.constants.EventNotificationCommonConstants;
import org.wso2.dpdp.accelerator.event.notifications.common.exception.dao.EventNotificationDuplicateResourceException;
import org.wso2.dpdp.accelerator.event.notifications.common.exception.service.EventNotificationServiceException;
import org.wso2.dpdp.accelerator.event.notifications.common.util.HmacSigner;
import org.wso2.dpdp.accelerator.event.notifications.dao.EventDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.PaginatedDAOResult;
import org.wso2.dpdp.accelerator.event.notifications.dao.SubscriptionDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.TopicDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Event;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.PollDelivery;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Subscription;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Topic;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDelivery;
import org.wso2.dpdp.accelerator.event.notifications.service.constants.EventNotificationServiceConstants;
import org.wso2.dpdp.accelerator.event.notifications.service.dispatch.SignedEventPayloadFactory;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.EventDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.EventPollingResponseDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDeliveryDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.model.PaginatedResult;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Covers the four branches of {@link EventPublishServiceImpl#publishEvent}:
 * happy path, missing orgId, missing topic, and inactive topic. Also asserts
 * that fan-out is invoked synchronously after the event is persisted.
 */
public class EventPublishServiceImplTest {

    private EventDAO eventDAO;
    private TopicDAO topicDAO;
    private org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryDAO deliveryDAO;
    private org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryAckDAO deliveryAckDAO;
    private SubscriptionDAO subscriptionDAO;
    private DPDPConfigurationService configurationService;
    private SignedEventPayloadFactory signedEventPayloadFactory;
    private EventPublishServiceImpl publishService;
    private Connection connection;

    @BeforeMethod
    public void setUp() throws Exception {
        eventDAO = mock(EventDAO.class);
        topicDAO = mock(TopicDAO.class);
        deliveryDAO = mock(org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryDAO.class);
        deliveryAckDAO = mock(org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryAckDAO.class);
        subscriptionDAO = mock(SubscriptionDAO.class);
        configurationService = mock(DPDPConfigurationService.class);
        signedEventPayloadFactory = mock(SignedEventPayloadFactory.class);
        connection = mock(Connection.class);
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        setStaticInstance(null);
        setStaticDataSource(dataSource);
        when(eventDAO.addEvent(any(Connection.class), any())).thenReturn(true);
        when(configurationService.isEventNotificationPollingDefaultReturnImmediately()).thenReturn(true);
        when(configurationService.getEventNotificationPollingDefaultMaxEvents()).thenReturn(20);
        when(configurationService.getEventNotificationPollingMaxEventsLimit()).thenReturn(100);
        when(configurationService.getEventNotificationPayloadSigningAudience())
                .thenReturn("dpdp-event-notifications");
        publishService = new EventPublishServiceImpl(eventDAO, topicDAO, deliveryDAO, deliveryAckDAO,
                subscriptionDAO, configurationService, signedEventPayloadFactory);
        when(subscriptionDAO.getActiveSubscriptionsForFanOut(any(Connection.class), anyString(), anyString()))
                .thenReturn(Collections.emptyList());
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
    public void publishEvent_happyPath_persistsAndFansOut() {
        when(topicDAO.getActiveTopicByOrgAndNameForUpdate(any(Connection.class), eq("org1"), eq("topic-a")))
                .thenReturn(Optional.of(new Topic("topic-id-1", "org1", "topic-a", "desc", "active")));
        Map<String, Object> payload = new HashMap<>();
        payload.put("k", "v");

        EventDTO dto = publishService.publishEvent("org1", "g1", "topic-a",
                Arrays.asList("marketing"), payload);

        assertNotNull(dto.getEventId());
        assertEquals(dto.getOrgId(), "org1");
        assertEquals(dto.getGroupId(), "g1");
        assertEquals(dto.getTopicId(), "topic-id-1");
        assertEquals(dto.getPayload(), "{\"k\":\"v\"}");
        assertEquals(dto.getPurposes(), Arrays.asList("marketing"));
        assertNotNull(dto.getOccurredAt());
        assertNotNull(dto.getCreatedAt());

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventDAO, times(1)).addEvent(eq(connection), eventCaptor.capture());
        Event persisted = eventCaptor.getValue();
        assertEquals(persisted.getEventId(), dto.getEventId());
        assertEquals(persisted.getOrgId(), "org1");
        assertEquals(persisted.getTopicId(), "topic-id-1");

        verify(eventDAO, times(1)).addEventPurposes(eq(connection), eq(dto.getEventId()), eq("org1"),
                eq(Arrays.asList("marketing")));
        verify(subscriptionDAO, times(1)).getActiveSubscriptionsForFanOut(eq(connection), eq("org1"),
                eq("topic-id-1"));
    }

    @Test
    public void pollEventsUpdatesAckAndErrorDeliveriesAndReturnsEventPayloads() {
        Event event = new Event("event-1", "org1", "group-1", "topic-1", "{\"value\":1}",
                new Timestamp(System.currentTimeMillis()));
        event.setTopic("accounts");
        event.setPurposes(Collections.singletonList("payments"));
        Subscription subscription = pollSub("group-1");
        when(subscriptionDAO.getSubscriptionById(any(Connection.class), eq("subscription-1"), eq("org1")))
                .thenReturn(Optional.of(subscription));
        when(eventDAO.getEventById(any(Connection.class), eq("event-1"), eq("org1"))).thenReturn(Optional.of(event));
        when(deliveryDAO.getPendingPollDeliveries(any(Connection.class), eq("org1"), eq("group-1"), eq("subscription-1"), eq(11)))
                .thenReturn(Collections.singletonList(
                new PollDelivery("pending-delivery", "subscription-1", "event-1", "pending",
                        event.getCreatedAt(), null)));
        try {
            when(signedEventPayloadFactory.sign(anyString(), anyString(), anyString(), anyString(), anyString(),
                    anyString(), anyString(), anyString(), anyString())).thenReturn("header.claims.signature");
        } catch (Exception e) {
            throw new AssertionError(e);
        }

        String body = "{\"ack\":[\"ack-delivery\"],\"setErrs\":{" +
                "\"error-delivery\":{\"err\":\"authentication_failed\"," +
                "\"description\":\"consumer failure\"}},\"maxEvents\":10," +
                "\"returnImmediately\":true}";
        when(configurationService.isEventNotificationPollingRequestHmacValidationEnabled()).thenReturn(true);
        EventPollingResponseDTO response = publishService.pollEvents(
                "org1", "group-1", "subscription-1", body,
                "sha256=" + HmacSigner.sign("shared-secret", body));

        verify(deliveryDAO).updatePollDeliveryStatusesByDeliveryIds(any(Connection.class), eq("org1"), eq("group-1"),
                eq("subscription-1"), eq(Collections.singletonList("ack-delivery")), anyMap());
        assertEquals(response.getSets().size(), 1);
        assertEquals(response.getSets().get("pending-delivery"), "header.claims.signature");
        assertTrue(!response.isMoreAvailable());
    }

    @Test
    public void pollEventsWithZeroMaxEventsOnlyAcknowledgesAndReportsAvailability() {
        Subscription subscription = pollSub("group-1");
        when(subscriptionDAO.getSubscriptionById(any(Connection.class), eq("subscription-1"), eq("org1")))
                .thenReturn(Optional.of(subscription));
        when(deliveryDAO.getPendingPollDeliveries(any(Connection.class), eq("org1"), eq("group-1"), eq("subscription-1"), eq(1)))
                .thenReturn(Collections.singletonList(new PollDelivery(
                        "pending-delivery", "subscription-1", "event-1", "pending", null, null)));

        EventPollingResponseDTO response = publishService.pollEvents("org1", "group-1", "subscription-1",
                "{\"ack\":[\"ack-delivery\"],\"maxEvents\":0,\"returnImmediately\":true}", null);

        assertTrue(response.isMoreAvailable());
        assertTrue(response.getSets().isEmpty());
        verify(eventDAO, never()).getEventById(any(Connection.class), anyString(), anyString());
    }

    @Test
    public void pollEventsRejectsInvalidRequestHmacBeforeUpdatingDeliveries() {
        Subscription subscription = pollSub("group-1");
        when(subscriptionDAO.getSubscriptionById(any(Connection.class), eq("subscription-1"), eq("org1")))
                .thenReturn(Optional.of(subscription));
        when(configurationService.isEventNotificationPollingRequestHmacValidationEnabled()).thenReturn(true);

        try {
            publishService.pollEvents("org1", "group-1", "subscription-1",
                    "{\"ack\":[\"delivery-1\"]}", "sha256=invalid");
            fail("Expected invalid HMAC to be rejected");
        } catch (EventNotificationServiceException e) {
            assertEquals(e.getStatusCode(), 401);
        }
        verify(deliveryDAO, never()).updatePollDeliveryStatusesByDeliveryIds(
                any(Connection.class), anyString(), anyString(), anyString(), anyList(), anyMap());
    }

    @Test
    public void pollEventsAuthenticatesAnEmptyFirstPollBeforeApplyingDefaults() {
        Subscription subscription = pollSub("group-1");
        when(subscriptionDAO.getSubscriptionById(any(Connection.class), eq("subscription-1"), eq("org1")))
                .thenReturn(Optional.of(subscription));
        when(configurationService.isEventNotificationPollingRequestHmacValidationEnabled()).thenReturn(true);
        when(deliveryDAO.getPendingPollDeliveries(any(Connection.class), eq("org1"), eq("group-1"), eq("subscription-1"), eq(1)))
                .thenReturn(Collections.emptyList());

        EventPollingResponseDTO response = publishService.pollEvents("org1", "group-1", "subscription-1", "",
                "sha256=" + HmacSigner.sign("shared-secret", ""));

        assertTrue(response.getSets().isEmpty());
        verify(deliveryDAO).updatePollDeliveryStatusesByDeliveryIds(any(Connection.class), eq("org1"), eq("group-1"), eq("subscription-1"),
                eq(Collections.emptyList()), eq(Collections.emptyMap()));
    }

    @Test
    public void pollEventsDoesNotTreatAnEmptyBodyAsAJsonObjectForHmacVerification() {
        Subscription subscription = pollSub("group-1");
        when(subscriptionDAO.getSubscriptionById(any(Connection.class), eq("subscription-1"), eq("org1")))
                .thenReturn(Optional.of(subscription));
        when(configurationService.isEventNotificationPollingRequestHmacValidationEnabled()).thenReturn(true);

        try {
            publishService.pollEvents("org1", "group-1", "subscription-1", "",
                    "sha256=" + HmacSigner.sign("shared-secret", "{}"));
            fail("Expected a signature for {} not to authenticate an empty request body");
        } catch (EventNotificationServiceException e) {
            assertEquals(e.getStatusCode(), 401);
        }
        verify(deliveryDAO, never()).updatePollDeliveryStatusesByDeliveryIds(
                any(Connection.class), anyString(), anyString(), anyString(), anyList(), anyMap());
    }

    @Test
    public void pollEventsRejectsOrganizationThatDiffersFromTenantContext() {
        Subscription subscription = pollSub("group-1");
        when(subscriptionDAO.getSubscriptionById(any(Connection.class), eq("subscription-1"), eq("org1")))
                .thenReturn(Optional.of(subscription));

        try {
            publishService.pollEvents("org1", "group-1", "subscription-1",
                    "{\"orgId\":\"another-tenant\"}", null);
            fail("Expected tenant mismatch to be rejected");
        } catch (EventNotificationServiceException e) {
            assertEquals(e.getStatusCode(), 400);
        }
        verify(deliveryDAO, never()).getPendingPollDeliveries(
                any(Connection.class), anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    public void pollEventsRejectsSubscriptionFromAnotherGroup() {
        Subscription subscription = pollSub("another-group");
        when(subscriptionDAO.getSubscriptionById(any(Connection.class), eq("subscription-1"), eq("org1")))
                .thenReturn(Optional.of(subscription));

        try {
            publishService.pollEvents("org1", "group-1", "subscription-1", "{}", null);
            fail("Expected group mismatch to be rejected");
        } catch (EventNotificationServiceException e) {
            assertEquals(e.getStatusCode(), 404);
        }
        verify(deliveryDAO, never()).getPendingPollDeliveries(
                any(Connection.class), anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    public void completeDelivery_verifiesHmacAndPersistsCompletion() {
        WebhookDelivery delivery = new WebhookDelivery("delivery-1", "subscription-1", "event-1", "delivered",
                1, null, new Timestamp(System.currentTimeMillis()), new Timestamp(System.currentTimeMillis()), null);
        Subscription subscription = mock(Subscription.class);
        when(subscription.getGroupId()).thenReturn("Group-A");
        when(subscription.getSharedSecret()).thenReturn("shared-secret");
        when(deliveryDAO.getWebhookDeliveryById(any(Connection.class), eq("delivery-1"), eq("org1"))).thenReturn(Optional.of(delivery));
        when(subscriptionDAO.getSubscriptionById(any(Connection.class), eq("subscription-1"), eq("org1"))).thenReturn(Optional.of(subscription));
        String body = "{\"completionStatus\":\"completed\",\"completionEvidence\":\"https://processor/evidence\"}";
        String signature = "sha256=" + HmacSigner.signCompletion("shared-secret", "delivery-1", body);

        publishService.completeDelivery("org1", "group-a", "delivery-1", body, signature);

        verify(deliveryAckDAO).addDeliveryAck(any(Connection.class), any());
    }

    @Test(expectedExceptions = EventNotificationServiceException.class)
    public void completeDelivery_rejectsInvalidSignature() {
        WebhookDelivery delivery = new WebhookDelivery("delivery-1", "subscription-1", "event-1", "delivered",
                1, null, null, null, null);
        Subscription subscription = mock(Subscription.class);
        when(subscription.getGroupId()).thenReturn("group-1");
        when(subscription.getSharedSecret()).thenReturn("shared-secret");
        when(deliveryDAO.getWebhookDeliveryById(any(Connection.class), eq("delivery-1"), eq("org1"))).thenReturn(Optional.of(delivery));
        when(subscriptionDAO.getSubscriptionById(any(Connection.class), eq("subscription-1"), eq("org1"))).thenReturn(Optional.of(subscription));

        publishService.completeDelivery("org1", "group-1", "delivery-1", "{}", "sha256=bad");
    }

    @Test
    public void completeDeliveryRejectsDeliveryThatHasNotBeenDelivered() {
        WebhookDelivery delivery = new WebhookDelivery("delivery-1", "subscription-1", "event-1", "in_flight",
                1, null, null, null, null);
        Subscription subscription = mock(Subscription.class);
        when(subscription.getGroupId()).thenReturn("group-1");
        when(subscription.getSharedSecret()).thenReturn("shared-secret");
        when(deliveryDAO.getWebhookDeliveryById(any(Connection.class), eq("delivery-1"), eq("org1"))).thenReturn(Optional.of(delivery));
        when(subscriptionDAO.getSubscriptionById(any(Connection.class), eq("subscription-1"), eq("org1"))).thenReturn(Optional.of(subscription));
        String body = "{}";

        try {
            publishService.completeDelivery("org1", "group-1", "delivery-1", body,
                    "sha256=" + HmacSigner.signCompletion("shared-secret", "delivery-1", body));
            fail("Expected an undelivered webhook delivery to reject completion");
        } catch (EventNotificationServiceException e) {
            assertEquals(e.getStatusCode(), 409);
            assertEquals(e.getCode(), EventNotificationServiceConstants.ERROR_CODE_INVALID_STATE);
        }
        verify(deliveryAckDAO, never()).addDeliveryAck(any(Connection.class), any());
    }

    @Test
    public void completeDeliveryDoesNotRevealUnknownDelivery() {
        when(deliveryDAO.getWebhookDeliveryById(any(Connection.class), eq("unknown-delivery"), eq("org1"))).thenReturn(Optional.empty());

        try {
            publishService.completeDelivery("org1", "group-1", "unknown-delivery", "{}", "sha256=bad");
            fail("Expected an unknown delivery to be rejected as unauthenticated");
        } catch (EventNotificationServiceException e) {
            assertEquals(e.getStatusCode(), 401);
            assertEquals(e.getCode(), EventNotificationServiceConstants.ERROR_CODE_INVALID_SIGNATURE);
        }
        verify(subscriptionDAO, never()).getSubscriptionById(any(Connection.class), anyString(), anyString());
        verify(deliveryAckDAO, never()).addDeliveryAck(any(Connection.class), any());
    }

    @Test
    public void completeDeliveryDoesNotRevealGroupMismatch() {
        WebhookDelivery delivery = new WebhookDelivery("delivery-1", "subscription-1", "event-1", "delivered",
                1, null, null, null, null);
        Subscription subscription = mock(Subscription.class);
        when(subscription.getGroupId()).thenReturn("another-group");
        when(subscription.getSharedSecret()).thenReturn("shared-secret");
        when(deliveryDAO.getWebhookDeliveryById(any(Connection.class), eq("delivery-1"), eq("org1"))).thenReturn(Optional.of(delivery));
        when(subscriptionDAO.getSubscriptionById(any(Connection.class), eq("subscription-1"), eq("org1"))).thenReturn(Optional.of(subscription));
        String body = "{}";

        try {
            publishService.completeDelivery("org1", "group-1", "delivery-1", body,
                    "sha256=" + HmacSigner.signCompletion("shared-secret", "delivery-1", body));
            fail("Expected a group mismatch to be rejected as unauthenticated");
        } catch (EventNotificationServiceException e) {
            assertEquals(e.getStatusCode(), 401);
            assertEquals(e.getCode(), EventNotificationServiceConstants.ERROR_CODE_INVALID_SIGNATURE);
        }
        verify(deliveryAckDAO, never()).addDeliveryAck(any(Connection.class), any());
    }

    @Test
    public void completeDeliveryDoesNotRevealMissingSubscription() {
        WebhookDelivery delivery = new WebhookDelivery("delivery-1", "missing-subscription", "event-1", "delivered",
                1, null, null, null, null);
        when(deliveryDAO.getWebhookDeliveryById(any(Connection.class), eq("delivery-1"), eq("org1"))).thenReturn(Optional.of(delivery));
        when(subscriptionDAO.getSubscriptionById(any(Connection.class), eq("missing-subscription"), eq("org1"))).thenReturn(Optional.empty());

        try {
            publishService.completeDelivery("org1", "group-1", "delivery-1", "{}", "sha256=bad");
            fail("Expected a missing subscription to be rejected as unauthenticated");
        } catch (EventNotificationServiceException e) {
            assertEquals(e.getStatusCode(), 401);
            assertEquals(e.getCode(), EventNotificationServiceConstants.ERROR_CODE_INVALID_SIGNATURE);
        }
        verify(deliveryAckDAO, never()).addDeliveryAck(any(Connection.class), any());
    }

    @Test
    public void completeDeliveryAuthenticatesBeforeExposingDeliveryState() {
        WebhookDelivery delivery = new WebhookDelivery("delivery-1", "subscription-1", "event-1", "in_flight",
                1, null, null, null, null);
        Subscription subscription = mock(Subscription.class);
        when(subscription.getGroupId()).thenReturn("group-1");
        when(subscription.getSharedSecret()).thenReturn("shared-secret");
        when(deliveryDAO.getWebhookDeliveryById(any(Connection.class), eq("delivery-1"), eq("org1"))).thenReturn(Optional.of(delivery));
        when(subscriptionDAO.getSubscriptionById(any(Connection.class), eq("subscription-1"), eq("org1"))).thenReturn(Optional.of(subscription));

        try {
            publishService.completeDelivery("org1", "group-1", "delivery-1", "{}", "sha256=bad");
            fail("Expected invalid authentication to be rejected before delivery state");
        } catch (EventNotificationServiceException e) {
            assertEquals(e.getStatusCode(), 401);
            assertEquals(e.getCode(), EventNotificationServiceConstants.ERROR_CODE_INVALID_SIGNATURE);
        }
        verify(deliveryAckDAO, never()).addDeliveryAck(any(Connection.class), any());
    }

    @Test
    public void completeDeliveryMapsDuplicateCompletionToConflict() {
        WebhookDelivery delivery = new WebhookDelivery("delivery-1", "subscription-1", "event-1", "delivered",
                1, null, null, null, null);
        Subscription subscription = mock(Subscription.class);
        when(subscription.getGroupId()).thenReturn("group-1");
        when(subscription.getSharedSecret()).thenReturn("shared-secret");
        when(deliveryDAO.getWebhookDeliveryById(any(Connection.class), eq("delivery-1"), eq("org1"))).thenReturn(Optional.of(delivery));
        when(subscriptionDAO.getSubscriptionById(any(Connection.class), eq("subscription-1"), eq("org1"))).thenReturn(Optional.of(subscription));
        doThrow(new EventNotificationDuplicateResourceException("duplicate"))
                .when(deliveryAckDAO).addDeliveryAck(any(Connection.class), any());
        String body = "{\"completionStatus\":\"completed\"," +
                "\"completionEvidence\":\"https://processor.example/evidence\"}";

        try {
            publishService.completeDelivery("org1", "group-1", "delivery-1", body,
                    "sha256=" + HmacSigner.signCompletion("shared-secret", "delivery-1", body));
            fail("Expected duplicate completion to produce a conflict");
        } catch (EventNotificationServiceException e) {
            assertEquals(e.getStatusCode(), 409);
            assertEquals(e.getCode(), EventNotificationServiceConstants.ERROR_CODE_RESOURCE_EXISTS);
        }
    }

    @Test
    public void completeDeliveryRejectsInvalidEvidenceUrl() {
        WebhookDelivery delivery = new WebhookDelivery("delivery-1", "subscription-1", "event-1", "delivered",
                1, null, null, null, null);
        Subscription subscription = mock(Subscription.class);
        when(subscription.getGroupId()).thenReturn("group-1");
        when(subscription.getSharedSecret()).thenReturn("shared-secret");
        when(deliveryDAO.getWebhookDeliveryById(any(Connection.class), eq("delivery-1"), eq("org1"))).thenReturn(Optional.of(delivery));
        when(subscriptionDAO.getSubscriptionById(any(Connection.class), eq("subscription-1"), eq("org1"))).thenReturn(Optional.of(subscription));
        String body = "{\"completionStatus\":\"completed\"," +
                "\"completionEvidence\":\"http://processor.example/evidence\"}";

        try {
            publishService.completeDelivery("org1", "group-1", "delivery-1", body,
                    "sha256=" + HmacSigner.signCompletion("shared-secret", "delivery-1", body));
            fail("Expected non-HTTPS completion evidence to be rejected");
        } catch (EventNotificationServiceException e) {
            assertEquals(e.getStatusCode(), 400);
            assertEquals(e.getDescription(), EventNotificationServiceConstants.COMPLETION_EVIDENCE_INVALID_ERROR_MSG);
        }
        verify(deliveryAckDAO, never()).addDeliveryAck(any(Connection.class), any());
    }

    @Test
    public void completeDelivery_rejectsSignatureReplayedForAnotherDelivery() {
        WebhookDelivery delivery = new WebhookDelivery("delivery-2", "subscription-1", "event-2", "delivered",
                1, null, null, null, null);
        Subscription subscription = mock(Subscription.class);
        when(subscription.getGroupId()).thenReturn("group-1");
        when(subscription.getSharedSecret()).thenReturn("shared-secret");
        when(deliveryDAO.getWebhookDeliveryById(any(Connection.class), eq("delivery-2"), eq("org1"))).thenReturn(Optional.of(delivery));
        when(subscriptionDAO.getSubscriptionById(any(Connection.class), eq("subscription-1"), eq("org1"))).thenReturn(Optional.of(subscription));
        String body = "{\"completionStatus\":\"completed\",\"completionEvidence\":\"evidence\"}";
        String deliveryOneSignature = "sha256="
                + HmacSigner.signCompletion("shared-secret", "delivery-1", body);

        try {
            publishService.completeDelivery("org1", "group-1", "delivery-2", body, deliveryOneSignature);
            fail("Expected a completion signature bound to another delivery to be rejected");
        } catch (EventNotificationServiceException e) {
            assertEquals(e.getStatusCode(), 401);
            assertEquals(e.getCode(), EventNotificationServiceConstants.ERROR_CODE_INVALID_SIGNATURE);
        }
        verify(deliveryAckDAO, never()).addDeliveryAck(any(Connection.class), any());
    }

    @Test
    public void completeDelivery_rejectsLegacyBodyOnlySignature() {
        WebhookDelivery delivery = new WebhookDelivery("delivery-1", "subscription-1", "event-1", "delivered",
                1, null, null, null, null);
        Subscription subscription = mock(Subscription.class);
        when(subscription.getGroupId()).thenReturn("group-1");
        when(subscription.getSharedSecret()).thenReturn("shared-secret");
        when(deliveryDAO.getWebhookDeliveryById(any(Connection.class), eq("delivery-1"), eq("org1"))).thenReturn(Optional.of(delivery));
        when(subscriptionDAO.getSubscriptionById(any(Connection.class), eq("subscription-1"), eq("org1"))).thenReturn(Optional.of(subscription));
        String body = "{\"completionStatus\":\"completed\",\"completionEvidence\":\"evidence\"}";
        String legacySignature = "sha256=" + HmacSigner.sign("shared-secret", body);

        try {
            publishService.completeDelivery("org1", "group-1", "delivery-1", body, legacySignature);
            fail("Expected the legacy body-only completion signature to be rejected");
        } catch (EventNotificationServiceException e) {
            assertEquals(e.getStatusCode(), 401);
            assertEquals(e.getCode(), EventNotificationServiceConstants.ERROR_CODE_INVALID_SIGNATURE);
        }
        verify(deliveryAckDAO, never()).addDeliveryAck(any(Connection.class), any());
    }

    @Test
    public void publishEvent_nullPayload_isRejected() {
        try {
            publishService.publishEvent("org1", "g1", "topic-a", Collections.emptyList(), null);
            fail("Expected EventNotificationServiceException");
        } catch (EventNotificationServiceException e) {
            assertEquals(e.getStatusCode(), 422);
            assertEquals(e.getCode(), EventNotificationServiceConstants.ERROR_CODE_MISSING_REQUIRED_PARAM);
            assertEquals(e.getDescription(), EventNotificationServiceConstants.EVENT_PAYLOAD_REQUIRED_ERROR_MSG);
        }
        verify(topicDAO, never()).getActiveTopicByOrgAndNameForUpdate(any(Connection.class), anyString(), anyString());
    }

    @Test
    public void publishEvent_missingOrgId_throws400() {
        try {
            publishService.publishEvent(null, "g1", "topic-a", null, null);
            fail("Expected EventNotificationServiceException");
        } catch (EventNotificationServiceException e) {
            assertEquals(e.getStatusCode(), 400);
            assertEquals(e.getCode(), EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST);
        }
        verify(topicDAO, never()).getActiveTopicByOrgAndNameForUpdate(any(Connection.class), anyString(), anyString());
        verify(eventDAO, never()).addEvent(any(Connection.class), any());
    }

    @Test
    public void publishEvent_missingTopicName_throws400() {
        try {
            publishService.publishEvent("org1", "g1", null, null, null);
            fail("Expected EventNotificationServiceException");
        } catch (EventNotificationServiceException e) {
            assertEquals(e.getStatusCode(), 400);
        }
        verify(eventDAO, never()).addEvent(any(Connection.class), any());
    }

    @Test
    public void publishEvent_nullGroupId_throws400() {
        try {
            publishService.publishEvent("org1", null, "topic-a", null, null);
            fail("Expected EventNotificationServiceException");
        } catch (EventNotificationServiceException e) {
            assertEquals(e.getStatusCode(), 400);
            assertEquals(e.getCode(), EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST);
            assertEquals(e.getDescription(),
                    EventNotificationServiceConstants.GROUP_ID_MISSING_ERROR_MSG);
        }
        verify(topicDAO, never()).getActiveTopicByOrgAndNameForUpdate(any(Connection.class), anyString(), anyString());
        verify(eventDAO, never()).addEvent(any(Connection.class), any());
    }

    @Test
    public void publishEvent_blankGroupId_throws400() {
        try {
            publishService.publishEvent("org1", "   ", "topic-a", null, null);
            fail("Expected EventNotificationServiceException");
        } catch (EventNotificationServiceException e) {
            assertEquals(e.getStatusCode(), 400);
            assertEquals(e.getCode(), EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST);
            assertEquals(e.getDescription(),
                    EventNotificationServiceConstants.GROUP_ID_MISSING_ERROR_MSG);
        }
        verify(topicDAO, never()).getActiveTopicByOrgAndNameForUpdate(any(Connection.class), anyString(), anyString());
        verify(eventDAO, never()).addEvent(any(Connection.class), any());
    }

    @Test
    public void publishEvent_groupIdGuardFiresBeforeTopicLookup() {
        // orgId is blank but groupId is also blank. The orgId guard must fire first since it's checked first.
        try {
            publishService.publishEvent(null, null, "topic-a", null, null);
            fail("Expected EventNotificationServiceException");
        } catch (EventNotificationServiceException e) {
            assertEquals(e.getStatusCode(), 400);
            assertEquals(e.getDescription(),
                    EventNotificationServiceConstants.ORG_ID_MISSING_ERROR_MSG);
        }
    }

    @Test
    public void publishEvent_groupIdGuardFiresBeforeTopicName() {
        // orgId is present, groupId is blank, topicName is also blank.
        // The groupId guard must fire before the topicName guard.
        try {
            publishService.publishEvent("org1", null, null, null, null);
            fail("Expected EventNotificationServiceException");
        } catch (EventNotificationServiceException e) {
            assertEquals(e.getStatusCode(), 400);
            assertEquals(e.getDescription(),
                    EventNotificationServiceConstants.GROUP_ID_MISSING_ERROR_MSG);
        }
    }

    @Test
    public void publishEvent_topicNotFound_throws404() {
        when(topicDAO.getActiveTopicByOrgAndNameForUpdate(any(Connection.class), eq("org1"), eq("missing-topic")))
                .thenReturn(Optional.empty());

        try {
            publishService.publishEvent("org1", "g1", "missing-topic", null, Collections.emptyMap());
            fail("Expected EventNotificationServiceException");
        } catch (EventNotificationServiceException e) {
            assertEquals(e.getStatusCode(), 404);
            assertEquals(e.getCode(), EventNotificationServiceConstants.ERROR_CODE_TOPIC_NOT_FOUND);
            assertTrue(e.getDescription().contains("missing-topic"));
        }
        verify(eventDAO, never()).addEvent(any(Connection.class), any());
    }

    @Test
    public void publishEvent_topicNotActive_throws400() {
        when(topicDAO.getActiveTopicByOrgAndNameForUpdate(any(Connection.class), eq("org1"), eq("topic-a")))
                .thenReturn(Optional.of(new Topic("topic-id-1", "org1", "topic-a", null, "deleted")));

        try {
            publishService.publishEvent("org1", "g1", "topic-a", null, Collections.emptyMap());
            fail("Expected EventNotificationServiceException");
        } catch (EventNotificationServiceException e) {
            assertEquals(e.getStatusCode(), 400);
            assertEquals(e.getCode(), EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST);
        }
        verify(eventDAO, never()).addEvent(any(Connection.class), any());
    }

    @Test
    public void publishEvent_topicDeletedBeforeInsert_throws400() {
        when(topicDAO.getActiveTopicByOrgAndNameForUpdate(any(Connection.class), eq("org1"), eq("topic-a")))
                .thenReturn(Optional.of(new Topic("topic-id-1", "org1", "topic-a", null, "active")));
        when(eventDAO.addEvent(any(Connection.class), any())).thenReturn(false);

        EventNotificationServiceException exception = org.testng.Assert.expectThrows(EventNotificationServiceException.class,
                () -> publishService.publishEvent("org1", "g1", "topic-a", null, Collections.emptyMap()));

        assertEquals(exception.getStatusCode(), 400);
        assertEquals(exception.getCode(), EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST);
        verify(eventDAO, never()).addEventPurposes(any(Connection.class), anyString(), anyString(), any());
    }

    @Test
    public void publishEvent_fanOutFails_throws500() {
        when(topicDAO.getActiveTopicByOrgAndNameForUpdate(any(Connection.class), eq("org1"), eq("topic-a")))
                .thenReturn(Optional.of(new Topic("topic-id-1", "org1", "topic-a", null, "active")));
        when(subscriptionDAO.getActiveSubscriptionsForFanOut(any(Connection.class), eq("org1"), eq("topic-id-1")))
                .thenThrow(new RuntimeException("boom"));

        try {
            publishService.publishEvent("org1", "g1", "topic-a", Collections.emptyList(), Collections.emptyMap());
            fail("Expected EventNotificationServiceException when fan-out fails");
        } catch (EventNotificationServiceException e) {
            assertEquals(e.getStatusCode(), 500);
            assertEquals(e.getCode(), EventNotificationServiceConstants.ERROR_CODE_EVENT_PUBLISH_FAILED);
        }
    }

    @Test
    public void publishEvent_addEventThrows_throws500() {
        when(topicDAO.getActiveTopicByOrgAndNameForUpdate(any(Connection.class), eq("org1"), eq("topic-a")))
                .thenReturn(Optional.of(new Topic("topic-id-1", "org1", "topic-a", null, "active")));
        doThrow(new RuntimeException("db down")).when(eventDAO).addEvent(any(Connection.class), any());

        try {
            publishService.publishEvent("org1", "g1", "topic-a", null, Collections.emptyMap());
            fail("Expected EventNotificationServiceException");
        } catch (EventNotificationServiceException e) {
            assertEquals(e.getStatusCode(), 500);
            assertEquals(e.getCode(), EventNotificationServiceConstants.ERROR_CODE_EVENT_PUBLISH_FAILED);
        }
    }

    @Test
    public void searchEvents_happyPath_mapsAndReturnsTotal() {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        Event e1 = new Event("evt-1", "org1", "g1", "topic-id-1", "{\"k\":\"v1\"}", now);
        e1.setPurposes(Arrays.asList("marketing"));
        e1.setTopic("consent-events");
        e1.setDeliveriesCount(2);
        Event e2 = new Event("evt-2", "org1", null, "topic-id-1", "{\"k\":\"v2\"}", now);
        e2.setPurposes(Collections.emptyList());
        e2.setTopic("consent-events");
        e2.setDeliveriesCount(0);
        PaginatedDAOResult<Event> daoResult = new PaginatedDAOResult<>(Arrays.asList(e1, e2), 2);
        when(eventDAO.searchEvents(any(Connection.class), eq("org1"), any(), any(), any(), any(), eq("k"), anyInt(), anyInt())).thenReturn(daoResult);

        PaginatedResult<EventDTO> result = publishService.searchEvents("org1", "k", 10, 0);

        assertNotNull(result);
        assertEquals(result.getTotal(), 2);
        assertEquals(result.getItems().size(), 2);
        assertEquals(result.getItems().get(0).getEventId(), "evt-1");
        assertEquals(result.getItems().get(0).getTopic(), "consent-events");
        assertEquals(result.getItems().get(0).getDeliveriesCount(), 2);
        assertEquals(result.getItems().get(0).getPurposes(), Arrays.asList("marketing"));
        assertEquals(result.getItems().get(0).getPayload(), "{\"k\":\"v1\"}");
        assertEquals(result.getItems().get(1).getEventId(), "evt-2");
        assertEquals(result.getItems().get(1).getPurposes(), Collections.emptyList());
    }

    @Test
    public void searchEvents_withAllFilters_forwardsToDao() {
        when(eventDAO.searchEvents(any(Connection.class), eq("org1"), eq("topic1"), eq("delivered"), eq("grp1"), eq("marketing"), eq("search1"), eq(10), eq(0)))
                .thenReturn(new PaginatedDAOResult<>(Collections.emptyList(), 0));

        PaginatedResult<EventDTO> result = publishService.searchEvents("org1", "topic1", "DELIVERED", "grp1", "marketing", "search1", 10, 0);
        assertNotNull(result);
        verify(eventDAO, times(1)).searchEvents(any(Connection.class), eq("org1"), eq("topic1"), eq("delivered"), eq("grp1"), eq("marketing"), eq("search1"), eq(10), eq(0));
    }

    @Test(expectedExceptions = EventNotificationServiceException.class)
    public void searchEvents_blankOrgId_throws400() {
        publishService.searchEvents("  ", "search", 10, 0);
    }

    @Test(expectedExceptions = EventNotificationServiceException.class)
    public void searchEvents_nullOrgId_throws400() {
        publishService.searchEvents(null, "search", 10, 0);
    }

    @Test
    public void searchEvents_limitClampedToMaxLimit() {
        when(eventDAO.searchEvents(any(Connection.class), anyString(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PaginatedDAOResult<>(Collections.<Event>emptyList(), 0));

        publishService.searchEvents("org1", null, 10000, 0);

        verify(eventDAO, times(1)).searchEvents(any(Connection.class), eq("org1"), eq((String) null), eq((String) null), eq((String) null), eq((String) null), eq((String) null),
                eq(EventNotificationCommonConstants.MAX_LIMIT), eq(0));
    }

    @Test
    public void searchEvents_offsetNegative_treatedAsZero() {
        when(eventDAO.searchEvents(any(Connection.class), anyString(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PaginatedDAOResult<>(Collections.<Event>emptyList(), 0));

        publishService.searchEvents("org1", null, 10, -5);

        verify(eventDAO, times(1)).searchEvents(any(Connection.class), eq("org1"), eq((String) null), eq((String) null), eq((String) null), eq((String) null), eq((String) null), eq(10), eq(0));
    }

    @Test
    public void searchEvents_nullSearch_daoReceivesNull() {
        when(eventDAO.searchEvents(any(Connection.class), anyString(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PaginatedDAOResult<>(Collections.<Event>emptyList(), 0));

        publishService.searchEvents("org1", null, 10, 0);

        verify(eventDAO, times(1)).searchEvents(any(Connection.class), eq("org1"), eq((String) null), eq((String) null), eq((String) null), eq((String) null), eq((String) null), eq(10), eq(0));
    }

    @Test
    public void searchEvents_orgIdIsTrimmedBeforeDaoCall() {
        when(eventDAO.searchEvents(any(Connection.class), anyString(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PaginatedDAOResult<>(Collections.<Event>emptyList(), 0));

        publishService.searchEvents("  org1  ", "search", 10, 0);

        verify(eventDAO, times(1)).searchEvents(any(Connection.class), eq("org1"), eq((String) null), eq((String) null), eq((String) null), eq((String) null), eq("search"), eq(10), eq(0));
    }

    @Test
    public void searchEvents_emptyResult_isValidPaginatedResult() {
        when(eventDAO.searchEvents(any(Connection.class), anyString(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PaginatedDAOResult<>(Collections.<Event>emptyList(), 0));

        PaginatedResult<EventDTO> result = publishService.searchEvents("org1", "no-match", 10, 0);

        assertNotNull(result);
        assertEquals(result.getTotal(), 0);
        assertNotNull(result.getItems());
        assertEquals(result.getItems().size(), 0);
    }

    @Test
    public void searchEvents_daoThrows_propagates() {
        when(eventDAO.searchEvents(any(Connection.class), anyString(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("db down"));

        try {
            publishService.searchEvents("org1", "search", 10, 0);
            fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertEquals(e.getMessage(), "db down");
        }
    }

    @Test
    public void listOrgDeliveries_happyPath() {
        org.wso2.dpdp.accelerator.event.notifications.dao.model.SubscriptionDeliverySummary summary =
                new org.wso2.dpdp.accelerator.event.notifications.dao.model.SubscriptionDeliverySummary(
                        "dlv-1", "evt-1", "sub-1", "topic-1", "DELIVERED", "webhook",
                        new Timestamp(1710000000000L), new Timestamp(1710000000000L), "{}");
        when(deliveryDAO.listOrgDeliveries(any(Connection.class), eq("org1"), eq("delivered"), eq("sub-1"), eq("grp-1"), eq("marketing"), eq("search"), eq(10), eq(0), any(int[].class)))
                .thenAnswer(invocation -> {
                    int[] total = invocation.getArgument(9);
                    total[0] = 1;
                    return Collections.singletonList(summary);
                });

        PaginatedResult<org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDeliveryDTO> result =
                publishService.listOrgDeliveries("org1", "DELIVERED", "sub-1", "grp-1", "marketing", "search", 10, 0);

        assertNotNull(result);
        assertEquals(result.getTotal(), 1);
        assertEquals(result.getItems().size(), 1);
        assertEquals(result.getItems().get(0).getDeliveryId(), "dlv-1");
    }

    @Test
    public void listOrgDeliveries_missingOrgId_throws() {
        try {
            publishService.listOrgDeliveries(" ", null, null, null, null, 10, 0);
            fail("Expected EventNotificationServiceException");
        } catch (EventNotificationServiceException e) {
            assertEquals(e.getStatusCode(), 400);
        }
    }

    @Test
    public void getDeliveryHistory_happyPath() {
        org.wso2.dpdp.accelerator.event.notifications.dao.model.SubscriptionDeliverySummary summary =
                new org.wso2.dpdp.accelerator.event.notifications.dao.model.SubscriptionDeliverySummary(
                        "dlv-1", "evt-1", "sub-1", "topic-1", "DELIVERED", "webhook",
                        new Timestamp(1710000000000L), new Timestamp(1710000000000L), "{}");
        when(deliveryDAO.getOrgDeliveryById(any(Connection.class), eq("org1"), eq("dlv-1"))).thenReturn(Optional.of(summary));
        when(deliveryDAO.getWebhookDeliveryAudits(any(Connection.class), eq("dlv-1"), eq("org1"))).thenReturn(Collections.emptyList());

        org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionEventHistoryDTO dto =
                publishService.getDeliveryHistory("org1", "dlv-1");

        assertNotNull(dto);
        assertEquals(dto.getDeliveryId(), "dlv-1");
        assertEquals(dto.getEventId(), "evt-1");
    }

    @Test
    public void getDeliveryHistory_notFound_throws404() {
        when(deliveryDAO.getOrgDeliveryById(any(Connection.class), eq("org1"), eq("dlv-1"))).thenReturn(Optional.empty());

        try {
            publishService.getDeliveryHistory("org1", "dlv-1");
            fail("Expected EventNotificationServiceException");
        } catch (EventNotificationServiceException e) {
            assertEquals(e.getStatusCode(), 404);
        }
    }

    @Test
    public void getEventById_happyPath() {
        Timestamp now = new Timestamp(1710000000000L);
        Event event = new Event("evt-1", "org1", "group-1", "top-1", "{\"msg\":\"hi\"}", now);
        event.setPurposes(Arrays.asList("marketing"));
        when(eventDAO.getEventById(any(Connection.class), eq("evt-1"), eq("org1"))).thenReturn(Optional.of(event));
        when(topicDAO.getTopicById(any(Connection.class), eq("top-1"), eq("org1"))).thenReturn(Optional.of(new Topic("top-1", "org1", "consent.granted", "desc", "active")));

        EventDTO dto = publishService.getEventById("org1", "evt-1");
        assertNotNull(dto);
        assertEquals(dto.getEventId(), "evt-1");
        assertEquals(dto.getTopic(), "consent.granted");
        assertEquals(dto.getPurposes(), Arrays.asList("marketing"));
    }

    @Test
    public void getEventById_notFound_throws404() {
        when(eventDAO.getEventById(any(Connection.class), eq("evt-1"), eq("org1"))).thenReturn(Optional.empty());

        try {
            publishService.getEventById("org1", "evt-1");
            fail("Expected EventNotificationServiceException");
        } catch (EventNotificationServiceException e) {
            assertEquals(e.getStatusCode(), 404);
        }
    }

    @Test
    public void getEventDeliveries_happyPath() {
        org.wso2.dpdp.accelerator.event.notifications.dao.model.SubscriptionDeliverySummary summary =
                new org.wso2.dpdp.accelerator.event.notifications.dao.model.SubscriptionDeliverySummary(
                        "dlv-1", "evt-1", "sub-1", "consent.granted", "DELIVERED", "webhook",
                        new Timestamp(1710000000000L), new Timestamp(1710000000000L), "{}");
        when(deliveryDAO.listEventDeliveries(any(Connection.class), eq("org1"), eq("evt-1"), anyInt(), anyInt(), any(int[].class)))
                .thenReturn(Collections.singletonList(summary));

        PaginatedResult<SubscriptionDeliveryDTO> result = publishService.getEventDeliveries("org1", "evt-1", 10, 0);
        assertNotNull(result);
        assertEquals(result.getItems().size(), 1);
        assertEquals(result.getItems().get(0).getDeliveryId(), "dlv-1");
    }

    private static Subscription pollSub(String groupId) {
        Subscription s = new Subscription();
        s.setSubscriptionId("subscription-1");
        s.setOrgId("org1");
        s.setGroupId(groupId);
        s.setTopicIds(Collections.singletonList("topic-1"));
        s.setTopicNames(Collections.emptyList());
        s.setPurposeFilterMode("all");
        s.setPurposes(Collections.emptyList());
        s.setDeliveryMode("poll");
        s.setSharedSecret("shared-secret");
        s.setStatus("active");
        return s;
    }
}
