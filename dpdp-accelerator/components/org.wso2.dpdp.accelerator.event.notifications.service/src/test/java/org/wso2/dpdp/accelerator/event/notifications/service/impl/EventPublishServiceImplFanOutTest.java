/**
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 */

package org.wso2.dpdp.accelerator.event.notifications.service.impl;

import org.mockito.ArgumentCaptor;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.common.persistence.JDBCPersistenceManager;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.PurposeFilterMode;
import org.wso2.dpdp.accelerator.event.notifications.common.exception.service.EventNotificationServiceException;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryAckDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.EventDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.SubscriptionDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.TopicDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Event;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.PollDelivery;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Subscription;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Topic;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDelivery;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;

/** Verifies publication fan-out filtering and delivery-row creation. */
public class EventPublishServiceImplFanOutTest {

    private EventDAO eventDAO;
    private TopicDAO topicDAO;
    private DeliveryDAO deliveryDAO;
    private SubscriptionDAO subscriptionDAO;
    private EventPublishServiceImpl publishService;
    private Connection connection;

    @BeforeMethod
    public void setUp() throws Exception {
        eventDAO = mock(EventDAO.class);
        topicDAO = mock(TopicDAO.class);
        deliveryDAO = mock(DeliveryDAO.class);
        subscriptionDAO = mock(SubscriptionDAO.class);
        connection = mock(Connection.class);
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        setStaticInstance(null);
        setStaticDataSource(dataSource);

        when(topicDAO.getActiveTopicByOrgAndNameForUpdate(any(Connection.class), eq("org1"), eq("topic-a")))
                .thenReturn(Optional.of(new Topic("topic-1", "org1", "topic-a", null, "active")));
        when(eventDAO.addEvent(any(Connection.class), any(Event.class))).thenReturn(true);
        when(deliveryDAO.addWebhookDelivery(any(Connection.class), any(WebhookDelivery.class))).thenReturn(true);
        when(deliveryDAO.addPollDelivery(any(Connection.class), any())).thenReturn(true);
        when(subscriptionDAO.getActiveSubscriptionsForFanOut(any(Connection.class), anyString(), anyString()))
                .thenReturn(Collections.emptyList());

        publishService = new EventPublishServiceImpl(eventDAO, topicDAO, deliveryDAO,
                mock(DeliveryAckDAO.class), subscriptionDAO);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        setStaticDataSource(null);
        setStaticInstance(null);
    }

    @Test
    public void matchingWebhookSubscriptionIsQueued() {
        when(subscriptionDAO.getActiveSubscriptionsForFanOut(connection, "org1", "topic-1"))
                .thenReturn(Collections.singletonList(subscription("sub-1", "g1", "webhook",
                        PurposeFilterMode.SPECIFIC.getValue(), Arrays.asList("marketing"), "active")));

        Event event = publish(Arrays.asList("marketing"));

        ArgumentCaptor<WebhookDelivery> captor = ArgumentCaptor.forClass(WebhookDelivery.class);
        verify(deliveryDAO).addWebhookDelivery(eq(connection), captor.capture());
        assertEquals(captor.getValue().getSubscriptionId(), "sub-1");
        assertEquals(captor.getValue().getEventId(), event.getEventId());
        assertEquals(captor.getValue().getStatus(), "pending");
        assertEquals(captor.getValue().getAttemptCount(), 0);
    }

    @Test
    public void groupAndPurposeMismatchesAreFiltered() {
        when(subscriptionDAO.getActiveSubscriptionsForFanOut(connection, "org1", "topic-1"))
                .thenReturn(Arrays.asList(
                        subscription("wrong-group", "g9", "webhook", PurposeFilterMode.ALL.getValue(), null, "active"),
                        subscription("wrong-purpose", "g1", "webhook", PurposeFilterMode.SPECIFIC.getValue(),
                                Arrays.asList("billing"), "active")));

        publish(Arrays.asList("marketing"));

        verify(deliveryDAO, never()).addWebhookDelivery(any(Connection.class), any(WebhookDelivery.class));
    }

    @Test
    public void blankSubscriptionGroupMatchesAnyEventGroup() {
        when(subscriptionDAO.getActiveSubscriptionsForFanOut(connection, "org1", "topic-1"))
                .thenReturn(Collections.singletonList(subscription("any-group", null, "webhook",
                        PurposeFilterMode.ALL.getValue(), null, "active")));

        publish(Arrays.asList("marketing"));

        verify(deliveryDAO).addWebhookDelivery(any(Connection.class), any(WebhookDelivery.class));
    }

    @Test
    public void matchingWebhookAndPollSubscriptionsAreBothQueued() {
        List<Subscription> candidates = new ArrayList<>();
        candidates.add(subscription("webhook-match", "g1", "webhook", PurposeFilterMode.ALL.getValue(), null, "active"));
        candidates.add(subscription("poll-match", "g1", "poll", PurposeFilterMode.ALL.getValue(), null, "active"));
        candidates.add(subscription("pending", "g1", "webhook", PurposeFilterMode.ALL.getValue(), null, "pending"));
        when(subscriptionDAO.getActiveSubscriptionsForFanOut(connection, "org1", "topic-1"))
                .thenReturn(candidates);

        publish(Arrays.asList("marketing"));

        verify(deliveryDAO).addWebhookDelivery(any(Connection.class), any(WebhookDelivery.class));
        verify(deliveryDAO).addPollDelivery(any(Connection.class), any());
    }

    @Test
    public void duplicateSubscriptionIsQueuedOnlyOnce() {
        Subscription duplicate = subscription("sub-1", "g1", "webhook", PurposeFilterMode.ALL.getValue(), null, "active");
        when(subscriptionDAO.getActiveSubscriptionsForFanOut(connection, "org1", "topic-1"))
                .thenReturn(Arrays.asList(duplicate, duplicate));

        publish(Collections.emptyList());

        verify(deliveryDAO, times(1)).addWebhookDelivery(any(Connection.class), any(WebhookDelivery.class));
    }

    @Test
    public void deliveryDaoFailureIsMappedToPublishFailure() {
        when(subscriptionDAO.getActiveSubscriptionsForFanOut(connection, "org1", "topic-1"))
                .thenReturn(Collections.singletonList(subscription("sub-1", "g1", "webhook",
                        PurposeFilterMode.ALL.getValue(), null, "active")));
        when(deliveryDAO.addWebhookDelivery(any(Connection.class), any(WebhookDelivery.class))).thenReturn(false);

        EventNotificationServiceException exception = expectThrows(EventNotificationServiceException.class,
                () -> publish(Collections.emptyList()));

        assertEquals(exception.getStatusCode(), 500);
    }

    @Test
    public void multipleActivePollSubscriptionsOnSameTopicCreateIndependentPollDeliveries() {
        Subscription sub1 = subscription("sub-1", "g1", "poll", PurposeFilterMode.ALL.getValue(), null, "active");
        Subscription sub2 = subscription("sub-2", "g1", "poll", PurposeFilterMode.ALL.getValue(), null, "active");
        when(subscriptionDAO.getActiveSubscriptionsForFanOut(connection, "org1", "topic-1"))
                .thenReturn(Arrays.asList(sub1, sub2));
        when(deliveryDAO.addPollDelivery(any(Connection.class), any(PollDelivery.class))).thenReturn(true);

        publish(Collections.emptyList());

        ArgumentCaptor<PollDelivery> captor = ArgumentCaptor.forClass(PollDelivery.class);
        verify(deliveryDAO, times(2)).addPollDelivery(eq(connection), captor.capture());
        List<PollDelivery> deliveries = captor.getAllValues();
        assertEquals(deliveries.size(), 2);
        assertEquals(deliveries.get(0).getSubscriptionId(), "sub-1");
        assertEquals(deliveries.get(1).getSubscriptionId(), "sub-2");
    }

    private Event publish(List<String> purposes) {
        org.wso2.dpdp.accelerator.event.notifications.service.dto.EventDTO dto =
                publishService.publishEvent("org1", "g1", "topic-a", purposes,
                        Collections.singletonMap("value", 1));
        return new Event(dto.getEventId(), dto.getOrgId(), dto.getGroupId(), dto.getTopicId(),
                dto.getPayload(), dto.getOccurredAt());
    }

    private static Subscription subscription(String id, String groupId, String deliveryMode,
            String purposeFilterMode, List<String> purposes, String status) {
        Subscription s = new Subscription();
        s.setSubscriptionId(id);
        s.setOrgId("org1");
        s.setGroupId(groupId);
        s.setTopicIds(Collections.singletonList("topic-1"));
        s.setTopicNames(Collections.emptyList());
        s.setPurposeFilterMode(purposeFilterMode);
        s.setPurposes(purposes);
        s.setDeliveryMode(deliveryMode);
        s.setCallbackUrl("https://example.test/callback");
        s.setSharedSecret("shared-secret");
        s.setStatus(status);
        s.setCreatedAt(new java.sql.Timestamp(System.currentTimeMillis()));
        s.setUpdatedAt(new java.sql.Timestamp(System.currentTimeMillis()));
        return s;
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
}
