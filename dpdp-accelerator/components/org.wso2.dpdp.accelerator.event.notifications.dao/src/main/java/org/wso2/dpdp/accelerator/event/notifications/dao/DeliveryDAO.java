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

package org.wso2.dpdp.accelerator.event.notifications.dao;

import org.wso2.dpdp.accelerator.event.notifications.dao.model.PollDelivery;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.PollDeliveryError;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.SubscriptionDeliverySummary;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDelivery;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDeliveryAudit;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDeliveryDispatchContext;

import java.sql.Connection;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface DeliveryDAO {

    boolean addWebhookDelivery(Connection conn, WebhookDelivery delivery);

    Optional<WebhookDelivery> getWebhookDeliveryById(Connection conn, String deliveryId, String orgId);

    /**
     * Returns the next batch of pending webhook deliveries joined with the matching
     * subscription callback URL, shared secret, and event payload so the dispatch worker can
     * issue a single HTTP POST without further DAO calls.
     */
    List<WebhookDeliveryDispatchContext> getPendingWebhookDispatchContexts(Connection conn, int limit);

    /**
     * Returns the next batch of stuck in-flight webhook deliveries joined with the same
     * subscription/event context used by {@link #getPendingWebhookDispatchContexts(Connection, int)}.
     */
    List<WebhookDeliveryDispatchContext> getStuckInFlightWebhookDispatchContexts(Connection conn, int limit);

    List<WebhookDeliveryDispatchContext> getStuckInFlightWebhookDispatchContexts(Connection conn, int limit, Timestamp updatedBefore);

    Optional<WebhookDeliveryDispatchContext> getWebhookDeliveryDispatchContext(Connection conn, String orgId,
            String subscriptionId, String deliveryId);

    boolean prepareManualRetry(Connection conn, String orgId, String subscriptionId, String deliveryId,
            int maxRetries);

    boolean updateWebhookDeliveryStatus(Connection conn, WebhookDelivery delivery);

    boolean recordSuccessfulAttempt(Connection conn, WebhookDeliveryAudit audit, WebhookDelivery delivery);

    boolean recordRetryableFailure(Connection conn, WebhookDeliveryAudit audit, String deliveryId, int attemptCount, Timestamp nextRetryAt);

    boolean recordPermanentFailure(Connection conn, WebhookDeliveryAudit audit, WebhookDelivery delivery);

    boolean addWebhookDeliveryAudit(Connection conn, WebhookDeliveryAudit audit);

    List<WebhookDeliveryAudit> getWebhookDeliveryAudits(Connection conn, String deliveryId, String orgId);

    boolean addPollDelivery(Connection connection, PollDelivery delivery);

    Optional<PollDelivery> getPollDeliveryById(Connection conn, String deliveryId, String orgId);

    List<PollDelivery> getPendingPollDeliveries(Connection conn, String orgId, String groupId, String subscriptionId, int limit);

    void updatePollDeliveryStatusesByDeliveryIds(Connection connection, String orgId, String groupId,
            String subscriptionId, List<String> ackDeliveryIds, Map<String, PollDeliveryError> errors);

    boolean claimWebhookDelivery(Connection connection, String deliveryId);

    /**
     * Atomically reclaims a stuck {@code in_flight} delivery whose {@code UPDATED_AT} is
     * older than {@code updatedBefore}. Returns {@code true} only if the row was still
     * in_flight AND old enough — a concurrent active worker whose UPDATED_AT was just
     * refreshed will not be interrupted.
     */
    boolean claimStuckWebhookDelivery(Connection connection, String deliveryId, Timestamp updatedBefore);

    boolean releaseWebhookDelivery(Connection connection, String deliveryId, int attemptCount, Timestamp nextRetryAt);

    boolean claimPollDelivery(Connection connection, String deliveryId);

    boolean updatePollDeliveryStatus(Connection connection, String deliveryId, String status);

    boolean updatePollDeliveryStatus(Connection connection, String deliveryId, String expectedStatus,
            String newStatus);

    List<SubscriptionDeliverySummary> listSubscriptionDeliveries(Connection conn, String orgId, String subscriptionId, int limit, int offset, int[] totalOut);

    Optional<SubscriptionDeliverySummary> getSubscriptionDeliveryById(Connection conn, String orgId, String subscriptionId, String deliveryId);

    /**
     * Paginated list of event deliveries across the organisation.
     *
     * @param conn database connection.
     * @param orgId organisation identifier.
     * @param statusFilter optional delivery status filter.
     * @param subscriptionIdFilter optional subscription filter.
     * @param purposesFilter optional comma-separated purposes filter.
     * @param search optional free-text search term.
     * @param limit page size.
     * @param offset pagination offset.
     * @param totalOut 1-element array to receive the total matching row count.
     * @return list of delivery summaries.
     */
    default List<SubscriptionDeliverySummary> listOrgDeliveries(Connection conn, String orgId, String statusFilter,
            String subscriptionIdFilter, String purposesFilter, String search, int limit, int offset, int[] totalOut) {
        return listOrgDeliveries(conn, orgId, statusFilter, subscriptionIdFilter, null, purposesFilter, search, limit, offset, totalOut);
    }

    /**
     * Paginated list of event deliveries across the organisation with group filter support.
     *
     * @param conn database connection.
     * @param orgId organisation identifier.
     * @param statusFilter optional delivery status filter.
     * @param subscriptionIdFilter optional subscription filter.
     * @param groupIdFilter optional consumer group ID filter.
     * @param purposesFilter optional comma-separated purposes filter.
     * @param search optional free-text search term.
     * @param limit page size.
     * @param offset pagination offset.
     * @param totalOut 1-element array to receive the total matching row count.
     * @return list of delivery summaries.
     */
    List<SubscriptionDeliverySummary> listOrgDeliveries(Connection conn, String orgId, String statusFilter,
            String subscriptionIdFilter, String groupIdFilter, String purposesFilter, String search, int limit, int offset, int[] totalOut);

    Optional<SubscriptionDeliverySummary> getOrgDeliveryById(Connection conn, String orgId, String deliveryId);

    /**
     * Paginated list of deliveries generated for a specific published event.
     *
     * @param conn database connection.
     * @param orgId organisation identifier.
     * @param eventId event identifier.
     * @param limit page size.
     * @param offset pagination offset.
     * @param totalOut 1-element array to receive the total matching row count.
     * @return list of delivery summaries.
     */
    List<SubscriptionDeliverySummary> listEventDeliveries(Connection conn, String orgId, String eventId, int limit, int offset, int[] totalOut);
}
