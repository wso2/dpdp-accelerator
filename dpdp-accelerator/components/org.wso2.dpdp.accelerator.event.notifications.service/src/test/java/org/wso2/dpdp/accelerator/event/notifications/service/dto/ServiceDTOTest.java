/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 */
package org.wso2.dpdp.accelerator.event.notifications.service.dto;

import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.DeliveryMode;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.PurposeFilterMode;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.SubscriptionStatus;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;

public class ServiceDTOTest {

    @Test
    public void deliveryConfigDTOAccessors() {
        DeliveryConfigDTO dto = new DeliveryConfigDTO();
        dto.setMode(DeliveryMode.WEBHOOK);
        dto.setCallbackUrl("https://example.com:443/callback");
        dto.setSharedSecret("secret");
        assertEquals(dto.getMode(), DeliveryMode.WEBHOOK);
        assertEquals(dto.getCallbackUrl(), "https://example.com:443/callback");
        assertEquals(dto.getSharedSecret(), "secret");
    }

    @Test
    public void eventCreateDTOAccessors() {
        List<String> purposes = Collections.singletonList("analytics");
        Map<String, Object> payload = Collections.singletonMap("key", "value");
        EventCreateDTO dto = new EventCreateDTO();
        dto.setTopic("topic");
        dto.setPurposes(purposes);
        dto.setPayload(payload);
        assertEquals(dto.getTopic(), "topic");
        assertEquals(dto.getPurposes(), purposes);
        assertEquals(dto.getPayload(), payload);
        EventCreateDTO constructed = new EventCreateDTO("constructed", purposes, payload);
        assertEquals(constructed.getTopic(), "constructed");
    }

    @Test
    public void eventDTOAccessors() {
        Timestamp timestamp = new Timestamp(1L);
        List<String> purposes = Collections.singletonList("analytics");
        EventDTO dto = new EventDTO();
        dto.setEventId("event");
        dto.setOrgId("org");
        dto.setGroupId("group");
        dto.setTopicId("topic-id");
        dto.setTopic("topic");
        dto.setPayload("{}");
        dto.setPurposes(purposes);
        dto.setOccurredAt(timestamp);
        dto.setCreatedAt(timestamp);
        dto.setDeliveriesCount(2);
        assertEquals(dto.getEventId(), "event");
        assertEquals(dto.getOrgId(), "org");
        assertEquals(dto.getGroupId(), "group");
        assertEquals(dto.getTopicId(), "topic-id");
        assertEquals(dto.getTopic(), "topic");
        assertEquals(dto.getPayload(), "{}");
        assertEquals(dto.getPurposes(), purposes);
        assertEquals(dto.getOccurredAt(), timestamp);
        assertEquals(dto.getCreatedAt(), timestamp);
        assertEquals(dto.getDeliveriesCount(), 2);
    }

    @Test
    public void filterDTOAccessors() {
        List<String> purposes = Collections.singletonList("analytics");
        FilterDTO dto = new FilterDTO();
        dto.setType(PurposeFilterMode.SPECIFIC);
        dto.setPurposes(purposes);
        assertEquals(dto.getType(), PurposeFilterMode.SPECIFIC);
        assertEquals(dto.getPurposes(), purposes);
    }

    @Test
    public void subscriptionDTOAccessors() {
        FilterDTO filter = new FilterDTO(PurposeFilterMode.ALL, Collections.emptyList());
        DeliveryConfigDTO delivery = new DeliveryConfigDTO(DeliveryMode.POLL, null, "secret");
        SubscriptionDTO dto = new SubscriptionDTO();
        dto.setSubscriptionId("subscription");
        dto.setOrgId("org");
        dto.setGroupId("group");
        dto.setTopic("topic");
        dto.setFilter(filter);
        dto.setDelivery(delivery);
        dto.setStatus(SubscriptionStatus.ACTIVE);
        dto.setCreatedAt(1L);
        dto.setUpdatedAt(2L);
        dto.setAlreadyExists(Boolean.TRUE);
        dto.setMessage("message");
        assertEquals(dto.getSubscriptionId(), "subscription");
        assertEquals(dto.getOrgId(), "org");
        assertEquals(dto.getGroupId(), "group");
        assertEquals(dto.getTopic(), "topic");
        assertEquals(dto.getFilter(), filter);
        assertEquals(dto.getDelivery(), delivery);
        assertEquals(dto.getStatus(), SubscriptionStatus.ACTIVE);
        assertEquals(dto.getCreatedAt(), Long.valueOf(1L));
        assertEquals(dto.getUpdatedAt(), Long.valueOf(2L));
        assertEquals(dto.getAlreadyExists(), Boolean.TRUE);
        assertEquals(dto.getMessage(), "message");
    }

    @Test
    public void deliveryAndHistoryDTOAccessors() {
        SubscriptionDeliveryAttemptDTO attempt = new SubscriptionDeliveryAttemptDTO();
        attempt.setAttempt(1);
        attempt.setStatus("DELIVERED");
        attempt.setTimestamp(2L);
        attempt.setHttpStatus(200);
        attempt.setError(null);
        assertEquals(attempt.getAttempt(), 1);
        assertEquals(attempt.getStatus(), "DELIVERED");
        assertEquals(attempt.getTimestamp(), 2L);
        assertEquals(attempt.getHttpStatus(), Integer.valueOf(200));

        SubscriptionDeliveryDTO delivery = new SubscriptionDeliveryDTO();
        delivery.setGroupId("group");
        delivery.setSubscriptionId("subscription");
        delivery.setDeliveryId("delivery");
        delivery.setEventId("event");
        delivery.setTopic("topic");
        delivery.setCurrentStatus("DELIVERED");
        delivery.setDeliveryMode("POLL");
        delivery.setOccurredAt(3L);
        assertEquals(delivery.getGroupId(), "group");
        assertEquals(delivery.getSubscriptionId(), "subscription");
        assertEquals(delivery.getDeliveryId(), "delivery");
        assertEquals(delivery.getEventId(), "event");
        assertEquals(delivery.getTopic(), "topic");
        assertEquals(delivery.getCurrentStatus(), "DELIVERED");
        assertEquals(delivery.getDeliveryMode(), "POLL");
        assertEquals(delivery.getOccurredAt(), 3L);

        SubscriptionEventHistoryDTO history = new SubscriptionEventHistoryDTO();
        history.setDeliveryId("delivery");
        history.setEventId("event");
        history.setTopic("topic");
        history.setDeliveryMode("WEBHOOK");
        history.setCurrentStatus("FAILED");
        history.setOccurredAt(4L);
        history.setNextRetryAt(5L);
        history.setCompletionStatus("FAILED");
        history.setCompletionEvidence("timeout");
        history.setHistory(Collections.singletonList(attempt));
        assertEquals(history.getDeliveryId(), "delivery");
        assertEquals(history.getEventId(), "event");
        assertEquals(history.getTopic(), "topic");
        assertEquals(history.getDeliveryMode(), "WEBHOOK");
        assertEquals(history.getCurrentStatus(), "FAILED");
        assertEquals(history.getOccurredAt(), 4L);
        assertEquals(history.getNextRetryAt(), Long.valueOf(5L));
        assertEquals(history.getCompletionStatus(), "FAILED");
        assertEquals(history.getCompletionEvidence(), "timeout");
        assertEquals(history.getHistory(), Collections.singletonList(attempt));
        SubscriptionEventHistoryDTO constructedHistory = new SubscriptionEventHistoryDTO("d", "e", "t", "POLL",
                "DONE", 6L, 7L, "DONE", "ok", Collections.emptyList());
        assertEquals(constructedHistory.getDeliveryId(), "d");
    }

    @Test
    public void topicDTOConstructorsAndAccessors() {
        TopicDTO dto = new TopicDTO("id", "name", "description", "ACTIVE");
        assertEquals(dto.getTopicId(), "id");
        assertEquals(dto.getName(), "name");
        assertEquals(dto.getDescription(), "description");
        assertEquals(dto.getStatus(), "ACTIVE");
        dto.setTopicId("id2");
        dto.setName("name2");
        dto.setDescription("description2");
        dto.setStatus("DELETED");
        dto.setInitiatedBy("SYSTEM");
        assertEquals(dto.getTopicId(), "id2");
        assertEquals(dto.getName(), "name2");
        assertEquals(dto.getDescription(), "description2");
        assertEquals(dto.getStatus(), "DELETED");
        assertEquals(dto.getInitiatedBy(), "SYSTEM");
    }
}
