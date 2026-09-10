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

package org.wso2.dpdp.accelerator.event.notifications.endpoint.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.EventCreateRequest;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.Subscription;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.SubscriptionCreateRequest;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.SubscriptionStatus;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.TopicCreateRequest;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.exception.EventNotificationExceptionMapper;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.util.EventNotificationDtoMapper;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.EventCreateDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.EventDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.EventPollingResponseDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDeliveryAttemptDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDeliveryDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionEventHistoryDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.TopicDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.model.PaginatedResult;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import static org.testng.Assert.*;

/** Compares the public JSON with the previous service DTO wire representation. */
public class DtoContractTest {
    private final ObjectMapper json = new ObjectMapper();

    private void equivalent(Object service, Object api) {
        assertEquals((Object) json.valueToTree(api), (Object) json.valueToTree(service));
    }

    @Test
    public void eventPayloadAndEpochDatesRemainUnchanged() {
        EventDTO source = new EventDTO("event", "org", "group", "topic-id", "{\"nested\":true}",
                Arrays.asList("a", "b"), new Timestamp(1712345678901L), new Timestamp(1712345678902L));
        source.setTopic("topic");
        source.setDeliveriesCount(3);
        equivalent(source, EventNotificationDtoMapper.toApi(source));
        equivalent(new PaginatedResult<>(Collections.singletonList(source), 9),
                EventNotificationDtoMapper.events(new PaginatedResult<>(Collections.singletonList(source), 9)));
        equivalent(new EventDTO(), EventNotificationDtoMapper.toApi(new EventDTO()));
    }

    @Test
    public void subscriptionsPreserveNestedFieldsAndSuppressSecrets() throws Exception {
        String body = "{\"topic\":\"topic\",\"filter\":{\"type\":\" EXCEPT \",\"purposes\":[\"p\"]},"
                + "\"delivery\":{\"mode\":\" WEBHOOK \",\"callbackUrl\":\"https://receiver.example/callback\","
                + "\"sharedSecret\":\"secret\"}}";
        SubscriptionDTO oldRequest = json.readValue(body, SubscriptionDTO.class);
        SubscriptionDTO mapped = EventNotificationDtoMapper.toService(
                json.readValue(body, SubscriptionCreateRequest.class));
        equivalent(oldRequest, mapped);
        mapped.setSubscriptionId("subscription");
        mapped.setOrgId("org");
        mapped.setGroupId("group");
        mapped.setStatus(org.wso2.dpdp.accelerator.event.notifications.common.enums.SubscriptionStatus.ACTIVE);
        mapped.setCreatedAt(100L);
        mapped.setUpdatedAt(200L);
        mapped.setAlreadyExists(true);
        mapped.setMessage("existing");
        Subscription response = EventNotificationDtoMapper.toApi(mapped);
        assertNull(response.getDelivery().getSharedSecret());
        mapped.getDelivery().setSharedSecret(null);
        equivalent(mapped, response);
        equivalent(new PaginatedResult<>(Collections.singletonList(mapped), 5),
                EventNotificationDtoMapper.subscriptions(new PaginatedResult<>(Collections.singletonList(mapped), 5)));
        equivalent(new SubscriptionDTO(), EventNotificationDtoMapper.toApi(new SubscriptionDTO()));
    }

    @Test
    public void topicsAndDeliveryHistoryKeepAllFields() {
        TopicDTO topic = new TopicDTO("t", "topic", "description", "active", "system");
        equivalent(topic, EventNotificationDtoMapper.toApi(topic));
        equivalent(new PaginatedResult<>(Collections.singletonList(topic), 7),
                EventNotificationDtoMapper.topics(new PaginatedResult<>(Collections.singletonList(topic), 7)));
        equivalent(new TopicDTO(), EventNotificationDtoMapper.toApi(new TopicDTO()));
        SubscriptionDeliveryDTO delivery = new SubscriptionDeliveryDTO("d", "e", "topic", "DELIVERED",
                "webhook", 123L);
        delivery.setSubscriptionId("s");
        delivery.setGroupId("g");
        equivalent(delivery, EventNotificationDtoMapper.toApi(delivery));
        equivalent(new PaginatedResult<>(Collections.singletonList(delivery), 8),
                EventNotificationDtoMapper.deliveries(new PaginatedResult<>(Collections.singletonList(delivery), 8)));
        SubscriptionEventHistoryDTO history = new SubscriptionEventHistoryDTO();
        history.setDeliveryId("d");
        history.setEventId("e");
        history.setTopic("topic");
        history.setDeliveryMode("webhook");
        history.setCurrentStatus("DELIVERED");
        history.setOccurredAt(123L);
        history.setNextRetryAt(234L);
        history.setCompletionStatus("completed");
        history.setCompletionEvidence("https://receiver.example/evidence");
        history.setHistory(Arrays.asList(new SubscriptionDeliveryAttemptDTO(1, "FAILED", 123L, 503, "unavailable"),
                new SubscriptionDeliveryAttemptDTO(2, "DELIVERED", 234L, 200, null)));
        equivalent(history, EventNotificationDtoMapper.toApi(history));
        equivalent(new SubscriptionEventHistoryDTO(), EventNotificationDtoMapper.toApi(new SubscriptionEventHistoryDTO()));
        equivalent(new EventPollingResponseDTO(false, Collections.emptyMap()),
                EventNotificationDtoMapper.toApi(new EventPollingResponseDTO(false, Collections.emptyMap())));
    }

    @Test
    public void missingFieldsAndNullRequestsReachServiceValidation() throws Exception {
        assertNull(EventNotificationDtoMapper.toService((EventCreateRequest) null));
        assertNull(EventNotificationDtoMapper.toService((TopicCreateRequest) null));
        assertNull(EventNotificationDtoMapper.toService((SubscriptionCreateRequest) null));
        equivalent(json.readValue("{}", EventCreateDTO.class),
                EventNotificationDtoMapper.toService(json.readValue("{}", EventCreateRequest.class)));
        equivalent(json.readValue("{}", TopicDTO.class),
                EventNotificationDtoMapper.toService(json.readValue("{}", TopicCreateRequest.class)));
        equivalent(json.readValue("{}", SubscriptionDTO.class),
                EventNotificationDtoMapper.toService(json.readValue("{}", SubscriptionCreateRequest.class)));
        String body = "{\"name\":\"topic\",\"description\":\"description\"}";
        equivalent(json.readValue(body, TopicDTO.class),
                EventNotificationDtoMapper.toService(json.readValue(body, TopicCreateRequest.class)));
        String event = "{\"topic\":\"t\",\"payload\":{\"nested\":[1,true,{\"value\":\"x\"}]}}";
        equivalent(json.readValue(event, EventCreateDTO.class),
                EventNotificationDtoMapper.toService(json.readValue(event, EventCreateRequest.class)));
    }

    @Test
    public void legacyMetadataAndEnumAliasesStillParse() throws Exception {
        String topic = "{\"name\":\"t\",\"topicId\":\"ignored\",\"status\":\"ignored\",\"initiatedBy\":\"ignored\"}";
        equivalent(json.readValue(topic, TopicDTO.class),
                EventNotificationDtoMapper.toService(json.readValue(topic, TopicCreateRequest.class)));
        String subscription = "{\"topic\":\"t\",\"subscriptionId\":\"ignored\",\"orgId\":\"ignored\","
                + "\"groupId\":\"ignored\",\"status\":\" ACTIVE \",\"createdAt\":1,\"updatedAt\":2,"
                + "\"alreadyExists\":true,\"message\":\"ignored\"}";
        equivalent(json.readValue(subscription, SubscriptionDTO.class),
                EventNotificationDtoMapper.toService(json.readValue(subscription, SubscriptionCreateRequest.class)));
        for (String mode : Arrays.asList("poll", " POLL ", "", " ")) {
            String body = "{\"delivery\":{\"mode\":\"" + mode + "\"},\"filter\":{\"type\":\" \"}}";
            equivalent(json.readValue(body, SubscriptionDTO.class),
                    EventNotificationDtoMapper.toService(json.readValue(body, SubscriptionCreateRequest.class)));
        }
    }

    @Test
    public void malformedRequestsKeepErrorCodes() throws Exception {
        for (String body : Arrays.asList("{\"unknown\":1}", "{\"delivery\":{\"mode\":\"invalid\"}}",
                "{\"filter\":{\"type\":\"invalid\"}}", "{\"status\":\"invalid\"}",
                "{\"delivery\":{\"mode\":[]}}", "{\"filter\":{\"type\":{}}}",
                "{\"delivery\":{\"mode\":42}}", "{\"filter\":{\"type\":true}}")) {
            JsonNode previous = error(body, SubscriptionDTO.class);
            JsonNode current = error(body, SubscriptionCreateRequest.class);
            assertEquals(current.get("code").asText(), previous.get("code").asText());
            assertEquals(current.get("message").asText(), previous.get("message").asText());
        }
        Object entity = new EventNotificationExceptionMapper().toResponse(
                new javax.ws.rs.WebApplicationException(404)).getEntity();
        assertFalse(json.valueToTree(entity).has("description"));
    }

    private JsonNode error(String body, Class<?> type) throws Exception {
        try {
            json.readValue(body, type);
            fail("Expected malformed JSON for " + type);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            javax.ws.rs.core.Response response = new EventNotificationExceptionMapper().toResponse(e);
            assertEquals(response.getStatus(), 400);
            return json.valueToTree(response.getEntity());
        }
        throw new AssertionError();
    }
}
