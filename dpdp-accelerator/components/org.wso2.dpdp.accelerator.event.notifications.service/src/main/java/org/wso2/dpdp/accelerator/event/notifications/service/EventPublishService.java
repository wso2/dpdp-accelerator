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

package org.wso2.dpdp.accelerator.event.notifications.service;

import org.wso2.dpdp.accelerator.event.notifications.service.dto.EventDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.EventPollingResponseDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDeliveryDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionEventHistoryDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.model.PaginatedResult;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

/**
 * Persists a published event and synchronously fans it out to active
 * subscriptions, plus the read-side search entry point used by
 * {@code GET /events}.
 *
 * <p>TODO: as the read surface grows (detail / list-by-subscription / delivery-history),
 * consider splitting this interface into {@code EventQueryService} and keeping
 * {@code EventPublishService} for write operations only.</p>
 */
public interface EventPublishService {

    /**
     * Processes OB-style polling acknowledgements/errors and returns pending poll deliveries.
     */
    EventPollingResponseDTO pollEvents(String orgId, String groupId, String subscriptionId,
            String requestBody, String eventSignature);

    /** Stores a signed completion report for a webhook delivery. */
    void completeDelivery(String orgId, String groupId, String deliveryId,
            String requestBody, String eventSignature);

    /**
     * @param orgId the organisation identifier (required).
     * @param groupId the consumer group identifier (optional, may be null/blank
     *                if the event targets all groups).
     * @param topicName the topic to publish against. Must resolve to an active
     *                  topic for the given org.
     * @param purposes the purpose tags associated with the event. May be null
     *                 or empty; per {@code PurposeFilterMode}, an empty
     *                 purpose set may still fan out to subscriptions with
     *                 {@code ALL} mode.
     * @param payload the event payload. Serialized to JSON before being stored
     *                on the {@code EVENT} row. Null payloads are stored as
     *                an empty JSON object.
     * @return the persisted event including its generated {@code eventId}.
     */
    EventDTO publishEvent(String orgId, String groupId, String topicName, List<String> purposes,
            Map<String, Object> payload);

    /**
     * Paginated, search-only list of events for an org.
     *
     * @param orgId the organisation identifier (required).
     * @param search optional free-text term matched (case-insensitive, LIKE)
     *               against {@code EVENT_ID}, {@code GROUP_ID}, {@code TOPIC_ID},
     *               and the raw {@code PAYLOAD} text. Null/blank disables the
     *               filter.
     * @param limit  page size; clamped to {@code [DEFAULT_LIMIT, MAX_LIMIT]} at
     *               the handler/service boundary.
     * @param offset row offset; negative values are treated as 0.
     * @return the matching page plus the total count.
     */
    default PaginatedResult<EventDTO> searchEvents(String orgId, String search, int limit, int offset) {
        return searchEvents(orgId, null, null, null, null, null, search, limit, offset);
    }

    /**
     * Paginated list of events across the organisation with optional topic, status,
     * group ID, purposes, and search filters.
     *
     * @param orgId the organisation identifier (required).
     * @param topic optional topic name filter.
     * @param status optional delivery status filter.
     * @param groupId optional group ID filter.
     * @param purposes optional comma-separated purposes filter.
     * @param search optional search string matching event ID, group ID, topic, or payload.
     * @param limit page size.
     * @param offset pagination offset.
     * @return paginated list of events.
     */
    PaginatedResult<EventDTO> searchEvents(String orgId, String topic, String status, String groupId,
            String subscriptionId, String purposes, String search, int limit, int offset);

    default PaginatedResult<EventDTO> searchEvents(String orgId, String topic, String status, String groupId,
            String purposes, String search, int limit, int offset) {
        return searchEvents(orgId, topic, status, groupId, null, purposes, search, limit, offset);
    }

    /**
     * Paginated list of event deliveries across the organisation with optional status,
     * subscription, purposes, and search filters.
     *
     * @param orgId the organisation identifier (required).
     * @param status optional delivery status filter.
     * @param subscriptionId optional subscription filter.
     * @param purposes optional comma-separated purposes filter.
     * @param search optional search string matching delivery ID, event ID, or topic.
     * @param limit page size.
     * @param offset pagination offset.
     * @return paginated list of deliveries.
     */
    default PaginatedResult<SubscriptionDeliveryDTO> listOrgDeliveries(String orgId, String status,
            String subscriptionId, String purposes, String search, int limit, int offset) {
        return listOrgDeliveries(orgId, status, subscriptionId, null, purposes, search, limit, offset);
    }

    /**
     * Paginated list of event deliveries across the organisation with optional status,
     * subscription, group ID, purposes, and search filters.
     *
     * @param orgId the organisation identifier (required).
     * @param status optional delivery status filter.
     * @param subscriptionId optional subscription filter.
     * @param groupId optional group ID filter.
     * @param purposes optional comma-separated purposes filter.
     * @param search optional search string matching delivery ID, event ID, or topic.
     * @param limit page size.
     * @param offset pagination offset.
     * @return paginated list of deliveries.
     */
    PaginatedResult<SubscriptionDeliveryDTO> listOrgDeliveries(String orgId, String status,
            String subscriptionId, String groupId, String purposes, String search, int limit, int offset);

    /**
     * Fetches delivery details and completion state for a specific delivery instance
     * without loading attempt audit history.
     *
     * @param orgId the organisation identifier (required).
     * @param deliveryId the delivery identifier (required).
     * @return the delivery details including retry eligibility and completion evidence.
     */
    default SubscriptionEventHistoryDTO getDelivery(String orgId, String deliveryId) {
        return getDeliveryHistory(orgId, deliveryId);
    }

    /**
     * Fetches delivery audit history and completion details for a specific delivery instance.
     *
     * @param orgId the organisation identifier (required).
     * @param deliveryId the delivery identifier (required).
     * @return the delivery history including attempts and completion evidence.
     */
    SubscriptionEventHistoryDTO getDeliveryHistory(String orgId, String deliveryId);

    /**
     * Fetches detailed information for a specific published event by ID.
     *
     * @param orgId the organisation identifier (required).
     * @param eventId the event identifier (required).
     * @return the event details including payload, purposes, topic name, and delivery count.
     */
    EventDTO getEventById(String orgId, String eventId);

    /**
     * Fetches the downstream subscriber deliveries generated for a specific published event.
     *
     * @param orgId the organisation identifier (required).
     * @param eventId the event identifier (required).
     * @param limit page size.
     * @param offset pagination offset.
     * @return paginated list of subscriber deliveries.
     */
    PaginatedResult<SubscriptionDeliveryDTO> getEventDeliveries(String orgId, String eventId, int limit, int offset);

    /** Persists an event and its deliveries in the caller's transaction without completing it. */
    EventDTO publishEvent(Connection connection, String orgId, String groupId, String topicName,
            List<String> purposes, Map<String, Object> payload);
}
