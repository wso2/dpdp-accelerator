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

package org.wso2.dpdp.accelerator.event.notifications.endpoint.api.v1_1;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.SubscriptionStatus;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.handler.EventHandler;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.handler.SubscriptionHandler;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.handler.TopicHandler;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1_1.dto.Delivery;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1_1.dto.DeliveryHistory;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1_1.dto.DeliveryRetryResponse;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1_1.dto.EventCreateRequest;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1_1.dto.EventCreateResponse;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1_1.dto.SubscriptionCreateRequest;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1_1.dto.TopicCreateRequest;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1_1.dto.TopicStatus;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1_1.dto.TopicStatusUpdate;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.EventDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.EventPollingResponseDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDeliveryAttemptDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDeliveryDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionEventHistoryDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.TopicDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.model.PaginatedResult;

import java.util.Collections;
import java.util.HashMap;

import javax.ws.rs.core.Response;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests covering V1.1 JAX-RS endpoints: TopicEndpoint, SubscriptionEndpoint,
 * EventEndpoint, and DeliveryEndpoint.
 */
public class V11EndpointTest {

    @Mock
    private TopicHandler topicHandler;

    @Mock
    private SubscriptionHandler subscriptionHandler;

    @Mock
    private EventHandler eventHandler;

    private TopicEndpoint topicEndpoint;
    private SubscriptionEndpoint subscriptionEndpoint;
    private EventEndpoint eventEndpoint;
    private DeliveryEndpoint deliveryEndpoint;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        topicEndpoint = new TopicEndpoint(topicHandler, () -> "org-1");
        subscriptionEndpoint = new SubscriptionEndpoint(subscriptionHandler, () -> "org-1");
        eventEndpoint = new EventEndpoint(eventHandler, () -> "org-1");
        deliveryEndpoint = new DeliveryEndpoint(eventHandler, subscriptionHandler, () -> "org-1");
    }

    @Test
    public void topicEndpointOperations() {
        TopicDTO dto = new TopicDTO("t1", "topic-name", "desc", "active", "system");
        PaginatedResult<TopicDTO> page = new PaginatedResult<>(Collections.singletonList(dto), 1);
        when(topicHandler.createTopic(eq("org-1"), any())).thenReturn(dto);
        when(topicHandler.listTopics("org-1", "active", "search", 20, 0, "name")).thenReturn(page);
        when(topicHandler.deleteTopic("org-1", "t1")).thenReturn(dto);

        Response createResp = topicEndpoint.createTopic(new TopicCreateRequest().name("topic-name"));
        assertEquals(createResp.getStatus(), 201);

        Response listResp = topicEndpoint.listTopics("active", "search", 20, 0, "name");
        assertEquals(listResp.getStatus(), 200);

        Response deleteResp = topicEndpoint.deleteTopic("t1");
        assertEquals(deleteResp.getStatus(), 200);
    }

    @Test
    public void topicDeleteReturnsOnlyIdAndStatus() {
        TopicDTO dto = new TopicDTO("t1", "topic-name", "desc", "deregistered", "user");
        when(topicHandler.deleteTopic("org-1", "t1")).thenReturn(dto);

        Response response = topicEndpoint.deleteTopic("t1");

        assertEquals(response.getStatus(), 200);
        TopicStatusUpdate body = (TopicStatusUpdate) response.getEntity();
        assertEquals(body.getTopicId(), "t1");
        assertEquals(body.getStatus(), TopicStatus.DEREGISTERED);
        assertEquals(toJson(body), "{\"topicId\":\"t1\",\"status\":\"deregistered\"}");
    }

    @Test
    public void subscriptionDeleteAndVerifyReturnOnlyIdStatusAndUpdatedAt() {
        SubscriptionDTO deleted = statusSubscription(SubscriptionStatus.DELETED);
        SubscriptionDTO verified = statusSubscription(SubscriptionStatus.ACTIVE);
        when(subscriptionHandler.deleteSubscription("org-1", "sub-1")).thenReturn(deleted);
        when(subscriptionHandler.retryVerification("org-1", "sub-1")).thenReturn(verified);

        Response deleteResp = subscriptionEndpoint.deleteSubscription("sub-1");
        Response verifyResp = subscriptionEndpoint.retryVerification("sub-1");

        assertEquals(deleteResp.getStatus(), 200);
        assertEquals(toJson(deleteResp.getEntity()),
                "{\"subscriptionId\":\"sub-1\",\"status\":\"deleted\",\"updatedAt\":1787652970000}");
        assertEquals(verifyResp.getStatus(), 200);
        assertEquals(toJson(verifyResp.getEntity()),
                "{\"subscriptionId\":\"sub-1\",\"status\":\"active\",\"updatedAt\":1787652970000}");
    }

    private static SubscriptionDTO statusSubscription(SubscriptionStatus status) {
        SubscriptionDTO dto = new SubscriptionDTO();
        dto.setSubscriptionId("sub-1");
        dto.setName("sub-name");
        dto.setTopics(Collections.singletonList("topic-name"));
        dto.setStatus(status);
        dto.setUpdatedAt(1787652970000L);
        return dto;
    }

    private static String toJson(Object entity) {
        try {
            return new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL)
                    .writeValueAsString(entity);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    public void subscriptionEndpointOperations() {
        SubscriptionDTO dto = new SubscriptionDTO();
        dto.setSubscriptionId("sub-1");
        dto.setName("sub-name");
        dto.setTopics(Collections.singletonList("topic-name"));
        PaginatedResult<SubscriptionDTO> page = new PaginatedResult<>(Collections.singletonList(dto), 1);
        PaginatedResult<SubscriptionDeliveryDTO> dlvPage = new PaginatedResult<>(
                Collections.singletonList(new SubscriptionDeliveryDTO("d1", "e1", "sub-1", "org-1", "t", "DELIVERED", "webhook", 100L)), 1);

        when(subscriptionHandler.createSubscription(eq("org-1"), any())).thenReturn(dto);
        when(subscriptionHandler.listSubscriptions("org-1", "active", "marketing", "search", 20, 0, "name")).thenReturn(page);
        when(subscriptionHandler.getSubscription("org-1", "sub-1")).thenReturn(dto);
        when(subscriptionHandler.deleteSubscription("org-1", "sub-1")).thenReturn(dto);
        when(subscriptionHandler.retryVerification("org-1", "sub-1")).thenReturn(dto);
        when(subscriptionHandler.listSubscriptionEvents("org-1", "sub-1", 20, 0)).thenReturn(dlvPage);

        Response createResp = subscriptionEndpoint.createSubscription(new SubscriptionCreateRequest().name("sub-name").topics(Collections.singletonList("topic-name")));
        assertEquals(createResp.getStatus(), 201);

        Response listResp = subscriptionEndpoint.listSubscriptions("active", "marketing", "search", 20, 0, "name");
        assertEquals(listResp.getStatus(), 200);

        Response getResp = subscriptionEndpoint.getSubscription("sub-1");
        assertEquals(getResp.getStatus(), 200);

        Response delResp = subscriptionEndpoint.deleteSubscription("sub-1");
        assertEquals(delResp.getStatus(), 200);

        Response verifyResp = subscriptionEndpoint.retryVerification("sub-1");
        assertEquals(verifyResp.getStatus(), 200);

        Response dlvResp = subscriptionEndpoint.listSubscriptionDeliveries("sub-1", 20, 0);
        assertEquals(dlvResp.getStatus(), 200);
    }

    @Test
    public void eventEndpointOperations() {
        EventDTO dto = new EventDTO("e1", "org-1", "org-1", "t1", "{}", Collections.emptyList(), null, null);
        dto.setTopic("topic-name");
        PaginatedResult<EventDTO> page = new PaginatedResult<>(Collections.singletonList(dto), 1);
        EventPollingResponseDTO pollDto = new EventPollingResponseDTO(false, Collections.emptyMap());
        PaginatedResult<SubscriptionDeliveryDTO> dlvPage = new PaginatedResult<>(
                Collections.singletonList(new SubscriptionDeliveryDTO("d1", "e1", "sub-1", "org-1", "t", "DELIVERED", "webhook", 100L)), 1);

        when(eventHandler.publishEvent(eq("org-1"), eq("org-1"), any())).thenReturn(dto);
        when(eventHandler.pollEvents(eq("org-1"), eq("org-1"), eq("sub-1"), any(), eq("sig"))).thenReturn(pollDto);
        when(eventHandler.searchEvents(eq("org-1"), any(), any(), eq("org-1"), any(), any(), eq(20), eq(0))).thenReturn(page);
        when(eventHandler.getEventById(eq("org-1"), eq("e1"))).thenReturn(dto);
        when(eventHandler.getEventDeliveries(eq("org-1"), eq("e1"), eq(20), eq(0))).thenReturn(dlvPage);

        Response pubResp = eventEndpoint.publishEvent(new EventCreateRequest().topic("topic-name").payload(new HashMap<>()));
        assertEquals(pubResp.getStatus(), 201);
        assertTrue(pubResp.getEntity() instanceof EventCreateResponse);
        EventCreateResponse created = (EventCreateResponse) pubResp.getEntity();
        assertEquals(created.getEventId(), "e1");
        assertEquals(created.getTopic(), "topic-name");

        Response pollResp = eventEndpoint.pollEvents("sub-1", "sig", "{}");
        assertEquals(pollResp.getStatus(), 200);

        Response listResp = eventEndpoint.listEvents(null, null, null, null, null, 20, 0);
        assertEquals(listResp.getStatus(), 200);

        Response getResp = eventEndpoint.getEvent("e1");
        assertEquals(getResp.getStatus(), 200);

        Response dlvResp = eventEndpoint.getEventDeliveries("e1", 20, 0);
        assertEquals(dlvResp.getStatus(), 200);
    }

    @Test
    public void deliveryEndpointOperations() {
        SubscriptionEventHistoryDTO historyDto = new SubscriptionEventHistoryDTO();
        historyDto.setDeliveryId("d1");
        historyDto.setEventId("e1");
        historyDto.setTopic("topic-1");
        historyDto.setDeliveryMode("webhook");
        historyDto.setCurrentStatus("DELIVERED");
        historyDto.setOccurredAt(100L);
        historyDto.setNextRetryAt(200L);
        historyDto.setCompletionStatus("completed");
        historyDto.setCompletionEvidence("receipt");
        historyDto.setManualRetryAvailable(true);
        historyDto.setHistory(Collections.singletonList(
                new SubscriptionDeliveryAttemptDTO(1, "DELIVERED", 100L, 200, null)));

        when(eventHandler.getDeliveryHistory("org-1", "d1")).thenReturn(historyDto);
        when(subscriptionHandler.retryDelivery("org-1", "d1")).thenReturn(historyDto);

        Response getDeliveryResp = deliveryEndpoint.getDelivery("d1");
        assertEquals(getDeliveryResp.getStatus(), 200);
        assertTrue(getDeliveryResp.getEntity() instanceof Delivery);
        Delivery delivery = (Delivery) getDeliveryResp.getEntity();
        assertEquals(delivery.getDeliveryId(), "d1");
        assertEquals(delivery.getCompletionStatus(), "completed");

        Response getHistoryResp = deliveryEndpoint.getDeliveryHistory("d1");
        assertEquals(getHistoryResp.getStatus(), 200);
        assertTrue(getHistoryResp.getEntity() instanceof DeliveryHistory);
        DeliveryHistory history = (DeliveryHistory) getHistoryResp.getEntity();
        assertEquals(history.getDeliveryId(), "d1");
        assertNotNull(history.getHistory());
        assertEquals(history.getHistory().size(), 1);

        Response retryResp = deliveryEndpoint.retryDelivery("d1");
        assertEquals(retryResp.getStatus(), 202);
        assertTrue(retryResp.getEntity() instanceof DeliveryRetryResponse);
        DeliveryRetryResponse retry = (DeliveryRetryResponse) retryResp.getEntity();
        assertEquals(retry.getDeliveryId(), "d1");

        Response completeResp = deliveryEndpoint.completeDelivery("d1", "sig", "{}");
        assertEquals(completeResp.getStatus(), 204);
        verify(eventHandler).completeDelivery("org-1", "org-1", "d1", "{}", "sig");
    }
}
