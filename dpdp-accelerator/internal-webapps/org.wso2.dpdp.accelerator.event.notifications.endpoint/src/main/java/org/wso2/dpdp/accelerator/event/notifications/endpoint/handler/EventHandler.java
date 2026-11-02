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

package org.wso2.dpdp.accelerator.event.notifications.endpoint.handler;

import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.dpdp.accelerator.event.notifications.service.EventPublishService;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.EventCreateDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.EventDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.EventPollingResponseDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDeliveryDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionEventHistoryDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.model.PaginatedResult;

import java.util.List;
import java.util.Map;

/**
 * Thin glue between the JAX-RS endpoint and the OSGi-scoped
 * {@link EventPublishService}. Mirrors the constructor pattern used by
 * {@link TopicHandler} and {@link SubscriptionHandler}.
 */
public class EventHandler {

    private final EventPublishService eventPublishService;

    public EventHandler() {
        EventPublishService svc = (EventPublishService) PrivilegedCarbonContext
                .getThreadLocalCarbonContext()
                .getOSGiService(EventPublishService.class, null);
        if (svc == null) {
            throw new IllegalStateException("EventPublishService OSGi service not available");
        }
        this.eventPublishService = svc;
    }

    public EventHandler(EventPublishService eventPublishService) {
        this.eventPublishService = eventPublishService;
    }

    public EventDTO publishEvent(String orgId, String groupId, EventCreateDTO request) {
        String topicName = request != null ? request.getTopic() : null;
        List<String> purposes = request != null ? request.getPurposes() : null;
        Map<String, Object> payload = request != null ? request.getPayload() : null;
        return eventPublishService.publishEvent(orgId, groupId, topicName, purposes, payload);
    }

    public EventPollingResponseDTO pollEvents(String orgId, String groupId, String subscriptionId,
            String requestBody, String signature) {

        return eventPublishService.pollEvents(orgId, groupId, subscriptionId, requestBody, signature);
    }

    public void completeDelivery(String orgId, String groupId, String deliveryId,
            String requestBody, String signature) {
        eventPublishService.completeDelivery(orgId, groupId, deliveryId, requestBody, signature);
    }

    public PaginatedResult<EventDTO> searchEvents(String orgId, String search, Integer limit, Integer offset) {
        return searchEvents(orgId, null, null, null, null, search, limit, offset);
    }

    public PaginatedResult<EventDTO> searchEvents(String orgId, String topic, String status,
            String groupId, String subscriptionId, String purposes, String search, Integer limit, Integer offset) {
        int lim = limit == null ? 0 : limit;
        int off = offset == null ? -1 : offset;
        return eventPublishService.searchEvents(orgId, topic, status, groupId, subscriptionId, purposes, search, lim, off);
    }

    public PaginatedResult<EventDTO> searchEvents(String orgId, String topic, String status,
            String groupId, String purposes, String search, Integer limit, Integer offset) {
        int lim = limit == null ? 0 : limit;
        int off = offset == null ? -1 : offset;
        return eventPublishService.searchEvents(orgId, topic, status, groupId, purposes, search, lim, off);
    }

    public PaginatedResult<SubscriptionDeliveryDTO> listOrgDeliveries(String orgId, String status,
            String subscriptionId, String groupId, String purposes, String search, Integer limit, Integer offset) {
        int lim = limit == null ? 0 : limit;
        int off = offset == null ? -1 : offset;
        return eventPublishService.listOrgDeliveries(orgId, status, subscriptionId, groupId, purposes, search, lim, off);
    }

    public SubscriptionEventHistoryDTO getDelivery(String orgId, String deliveryId) {
        return eventPublishService.getDelivery(orgId, deliveryId);
    }

    public SubscriptionEventHistoryDTO getDeliveryHistory(String orgId, String deliveryId) {
        return eventPublishService.getDeliveryHistory(orgId, deliveryId);
    }

    public EventDTO getEventById(String orgId, String eventId) {
        return eventPublishService.getEventById(orgId, eventId);
    }

    public PaginatedResult<SubscriptionDeliveryDTO> getEventDeliveries(String orgId, String eventId, Integer limit, Integer offset) {
        int lim = limit == null ? 0 : limit;
        int off = offset == null ? -1 : offset;
        return eventPublishService.getEventDeliveries(orgId, eventId, lim, off);
    }
}
