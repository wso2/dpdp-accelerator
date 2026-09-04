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

package org.wso2.dpdp.accelerator.event.notifications.dao.impl;

import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.event.notifications.common.constants.EventNotificationCommonConstants;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.PollStatus;
import org.wso2.dpdp.accelerator.event.notifications.common.exception.EventNotificationDataAccessException;
import org.wso2.dpdp.accelerator.event.notifications.dao.constants.EventNotificationDBColumns;
import org.wso2.dpdp.accelerator.common.util.DatabaseUtils;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.PollDelivery;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.PollDeliveryError;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.SubscriptionDeliverySummary;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDelivery;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDeliveryAudit;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDeliveryDispatchContext;
import org.wso2.dpdp.accelerator.event.notifications.dao.queries.EventNotificationCommonDBQueries;
import org.wso2.dpdp.accelerator.event.notifications.dao.queries.EventNotificationQueryFactory;
import org.wso2.dpdp.accelerator.event.notifications.dao.queries.QueryBuilderUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public class DeliveryDAOImpl implements DeliveryDAO {

    private DPDPConfigurationService configurationService;

    public DeliveryDAOImpl() {
    }

    public DeliveryDAOImpl(DPDPConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    private EventNotificationCommonDBQueries getQueries(Connection conn) {
        return EventNotificationQueryFactory.getQueryProvider(conn);
    }

    private EventNotificationCommonDBQueries getQueries() {
        return EventNotificationQueryFactory.getQueryProvider();
    }

    @Override
    public boolean addWebhookDelivery(Connection conn, WebhookDelivery delivery) {
        if (conn == null) {
            throw new IllegalArgumentException("Connection cannot be null.");
        }
        try (PreparedStatement ps = conn.prepareStatement(getQueries(conn).getAddWebhookDeliveryQuery())) {
            ps.setString(1, delivery.getDeliveryId());
            ps.setString(2, delivery.getSubscriptionId());
            ps.setString(3, delivery.getEventId());
            ps.setString(4, delivery.getStatus());
            ps.setInt(5, delivery.getAttemptCount());
            ps.setTimestamp(6, delivery.getNextRetryAt());
            ps.setTimestamp(7, delivery.getCreatedAt());
            ps.setTimestamp(8, delivery.getUpdatedAt());
            ps.setTimestamp(9, delivery.getDeliveredAt());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_ADDING_WEBHOOK_DELIVERY,
                            (delivery != null ? delivery.getDeliveryId() : "null")),
                    e);
        }
    }

    @Override
    public Optional<WebhookDelivery> getWebhookDeliveryById(String deliveryId, String orgId) {
        Connection conn = DatabaseUtils.getDBConnection();
        try {
            try (PreparedStatement ps = conn.prepareStatement(getQueries(conn).getGetWebhookDeliveryByIdAndOrgQuery())) {
                ps.setString(1, deliveryId);
                ps.setString(2, orgId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        WebhookDelivery delivery = new WebhookDelivery(
                                rs.getString(EventNotificationDBColumns.DELIVERY_ID),
                                rs.getString(EventNotificationDBColumns.SUBSCRIPTION_ID),
                                rs.getString(EventNotificationDBColumns.EVENT_ID),
                                rs.getString(EventNotificationDBColumns.STATUS),
                                rs.getInt(EventNotificationDBColumns.ATTEMPT_COUNT),
                                rs.getTimestamp(EventNotificationDBColumns.NEXT_RETRY_AT),
                                rs.getTimestamp(EventNotificationDBColumns.CREATED_AT),
                                rs.getTimestamp(EventNotificationDBColumns.UPDATED_AT),
                                rs.getTimestamp(EventNotificationDBColumns.DELIVERED_AT));
                        DatabaseUtils.commitTransaction(conn);
                        return Optional.of(delivery);
                    }
                }
                DatabaseUtils.commitTransaction(conn);
                return Optional.empty();
            } catch (SQLException e) {
                DatabaseUtils.rollbackTransaction(conn);
                throw new EventNotificationDataAccessException(
                        String.format(EventNotificationCommonConstants.ERROR_GETTING_WEBHOOK_DELIVERY, deliveryId), e);
            }
        } catch (RuntimeException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw e;
        } finally {
            DatabaseUtils.closeConnection(conn);
        }
    }

    @Override
    public List<WebhookDeliveryDispatchContext> getPendingWebhookDispatchContexts(int limit) {
        return loadDispatchContexts(getQueries().getGetPendingWebhookDispatchContextsQuery(), limit);
    }

    @Override
    public List<WebhookDeliveryDispatchContext> getStuckInFlightWebhookDispatchContexts(int limit) {
        int threshold = getConfiguration().getEventNotificationStuckInFlightThresholdSeconds();
        Timestamp cutoff = new Timestamp(System.currentTimeMillis() - threshold * 1000L);
        return getStuckInFlightWebhookDispatchContexts(limit, cutoff);
    }

    @Override
    public List<WebhookDeliveryDispatchContext> getStuckInFlightWebhookDispatchContexts(int limit, Timestamp updatedBefore) {
        return loadDispatchContextsWithCutoff(getQueries().getGetStuckInFlightWebhookDispatchContextsQuery(), limit, updatedBefore);
    }

    private List<WebhookDeliveryDispatchContext> loadDispatchContexts(String sql, int limit) {
        Connection conn = DatabaseUtils.getDBConnection();
        try {
            List<WebhookDeliveryDispatchContext> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        WebhookDelivery delivery = new WebhookDelivery(
                                rs.getString(EventNotificationDBColumns.DELIVERY_ID),
                                rs.getString(EventNotificationDBColumns.SUBSCRIPTION_ID),
                                rs.getString(EventNotificationDBColumns.EVENT_ID),
                                rs.getString(EventNotificationDBColumns.STATUS),
                                rs.getInt(EventNotificationDBColumns.ATTEMPT_COUNT),
                                rs.getTimestamp(EventNotificationDBColumns.NEXT_RETRY_AT),
                                rs.getTimestamp(EventNotificationDBColumns.CREATED_AT),
                                rs.getTimestamp(EventNotificationDBColumns.UPDATED_AT),
                                rs.getTimestamp(EventNotificationDBColumns.DELIVERED_AT));
                        list.add(new WebhookDeliveryDispatchContext(
                                delivery,
                                rs.getString(EventNotificationDBColumns.ORG_ID),
                                rs.getString(EventNotificationDBColumns.GROUP_ID),
                                rs.getString(EventNotificationDBColumns.CALLBACK_URL),
                                rs.getString(EventNotificationDBColumns.SHARED_SECRET),
                                rs.getString(EventNotificationDBColumns.PAYLOAD),
                                rs.getTimestamp(EventNotificationDBColumns.UPDATED_AT),
                                rs.getString(EventNotificationDBColumns.TOPIC_NAME)));
                    }
                }
                DatabaseUtils.commitTransaction(conn);
                return list;
            } catch (SQLException e) {
                DatabaseUtils.rollbackTransaction(conn);
                throw new EventNotificationDataAccessException(
                        EventNotificationCommonConstants.ERROR_GETTING_PENDING_WEBHOOK_DELIVERIES, e);
            }
        } catch (RuntimeException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw e;
        } finally {
            DatabaseUtils.closeConnection(conn);
        }
    }

    private List<WebhookDeliveryDispatchContext> loadDispatchContextsWithCutoff(String sql, int limit, Timestamp cutoff) {
        Connection conn = DatabaseUtils.getDBConnection();
        try {
            List<WebhookDeliveryDispatchContext> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setTimestamp(1, cutoff != null ? cutoff : new Timestamp(System.currentTimeMillis()));
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        WebhookDelivery delivery = new WebhookDelivery(
                                rs.getString(EventNotificationDBColumns.DELIVERY_ID),
                                rs.getString(EventNotificationDBColumns.SUBSCRIPTION_ID),
                                rs.getString(EventNotificationDBColumns.EVENT_ID),
                                rs.getString(EventNotificationDBColumns.STATUS),
                                rs.getInt(EventNotificationDBColumns.ATTEMPT_COUNT),
                                rs.getTimestamp(EventNotificationDBColumns.NEXT_RETRY_AT),
                                rs.getTimestamp(EventNotificationDBColumns.CREATED_AT),
                                rs.getTimestamp(EventNotificationDBColumns.UPDATED_AT),
                                rs.getTimestamp(EventNotificationDBColumns.DELIVERED_AT));
                        list.add(new WebhookDeliveryDispatchContext(
                                delivery,
                                rs.getString(EventNotificationDBColumns.ORG_ID),
                                rs.getString(EventNotificationDBColumns.GROUP_ID),
                                rs.getString(EventNotificationDBColumns.CALLBACK_URL),
                                rs.getString(EventNotificationDBColumns.SHARED_SECRET),
                                rs.getString(EventNotificationDBColumns.PAYLOAD),
                                rs.getTimestamp(EventNotificationDBColumns.UPDATED_AT),
                                rs.getString(EventNotificationDBColumns.TOPIC_NAME)));
                    }
                }
                DatabaseUtils.commitTransaction(conn);
                return list;
            } catch (SQLException e) {
                DatabaseUtils.rollbackTransaction(conn);
                throw new EventNotificationDataAccessException(
                        EventNotificationCommonConstants.ERROR_GETTING_PENDING_WEBHOOK_DELIVERIES, e);
            }
        } catch (RuntimeException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw e;
        } finally {
            DatabaseUtils.closeConnection(conn);
        }
    }

    @Override
    public boolean updateWebhookDeliveryStatus(WebhookDelivery delivery) {
        Connection conn = DatabaseUtils.getDBConnection();
        try {
            boolean result = updateWebhookDeliveryStatus(conn, delivery);
            DatabaseUtils.commitTransaction(conn);
            return result;
        } catch (RuntimeException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw e;
        } finally {
            DatabaseUtils.closeConnection(conn);
        }
    }

    public boolean updateWebhookDeliveryStatus(Connection conn, WebhookDelivery delivery) {
        try (PreparedStatement ps = conn.prepareStatement(getQueries(conn).getUpdateWebhookDeliveryStatusQuery())) {
            ps.setString(1, delivery.getStatus());
            ps.setInt(2, delivery.getAttemptCount());
            ps.setTimestamp(3, delivery.getNextRetryAt());
            ps.setTimestamp(4, delivery.getDeliveredAt());
            ps.setString(5, delivery.getDeliveryId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_UPDATING_WEBHOOK_DELIVERY_STATUS,
                            (delivery != null ? delivery.getDeliveryId() : "null")),
                    e);
        }
    }

    @Override
    public boolean recordSuccessfulAttempt(WebhookDeliveryAudit audit, WebhookDelivery delivery) {
        Connection conn = DatabaseUtils.getDBConnection();
        try {
            boolean updated = updateWebhookDeliveryStatus(conn, delivery);
            if (updated) {
                addWebhookDeliveryAudit(conn, audit);
            }
            DatabaseUtils.commitTransaction(conn);
            return updated;
        } catch (RuntimeException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw e;
        } finally {
            DatabaseUtils.closeConnection(conn);
        }
    }

    @Override
    public boolean recordRetryableFailure(WebhookDeliveryAudit audit, String deliveryId, int attemptCount, Timestamp nextRetryAt) {
        Connection conn = DatabaseUtils.getDBConnection();
        try {
            boolean released = releaseWebhookDelivery(conn, deliveryId, attemptCount, nextRetryAt);
            if (released) {
                addWebhookDeliveryAudit(conn, audit);
            }
            DatabaseUtils.commitTransaction(conn);
            return released;
        } catch (RuntimeException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw e;
        } finally {
            DatabaseUtils.closeConnection(conn);
        }
    }

    @Override
    public boolean recordPermanentFailure(WebhookDeliveryAudit audit, WebhookDelivery delivery) {
        Connection conn = DatabaseUtils.getDBConnection();
        try {
            boolean updated = updateWebhookDeliveryStatus(conn, delivery);
            if (updated) {
                addWebhookDeliveryAudit(conn, audit);
            }
            DatabaseUtils.commitTransaction(conn);
            return updated;
        } catch (RuntimeException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw e;
        } finally {
            DatabaseUtils.closeConnection(conn);
        }
    }

    @Override
    public boolean addWebhookDeliveryAudit(WebhookDeliveryAudit audit) {
        Connection conn = DatabaseUtils.getDBConnection();
        try {
            boolean result = addWebhookDeliveryAudit(conn, audit);
            DatabaseUtils.commitTransaction(conn);
            return result;
        } catch (RuntimeException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw e;
        } finally {
            DatabaseUtils.closeConnection(conn);
        }
    }

    public boolean addWebhookDeliveryAudit(Connection conn, WebhookDeliveryAudit audit) {
        try (PreparedStatement ps = conn.prepareStatement(getQueries(conn).getAddWebhookDeliveryAuditQuery())) {
            ps.setString(1, audit.getAuditId());
            ps.setString(2, audit.getEventId());
            ps.setString(3, audit.getDeliveryId());
            ps.setString(4, audit.getOrgId());
            ps.setString(5, audit.getResponseCode());
            ps.setTimestamp(6, audit.getCreatedAt());
            ps.setTimestamp(7, audit.getAttemptAt());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_ADDING_WEBHOOK_DELIVERY_AUDIT,
                            (audit != null ? audit.getDeliveryId() : "null")),
                    e);
        }
    }

    @Override
    public List<WebhookDeliveryAudit> getWebhookDeliveryAudits(String deliveryId, String orgId) {
        Connection conn = DatabaseUtils.getDBConnection();
        try {
            List<WebhookDeliveryAudit> list = new ArrayList<>();
            try (PreparedStatement ps = conn
                    .prepareStatement(getQueries(conn).getGetWebhookDeliveryAuditsByDeliveryIdQuery())) {
                ps.setString(1, deliveryId);
                ps.setString(2, orgId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new WebhookDeliveryAudit(
                                rs.getString(EventNotificationDBColumns.AUDIT_ID),
                                rs.getString(EventNotificationDBColumns.EVENT_ID),
                                rs.getString(EventNotificationDBColumns.DELIVERY_ID),
                                rs.getString(EventNotificationDBColumns.ORG_ID),
                                rs.getString(EventNotificationDBColumns.RESPONSE_CODE),
                                rs.getTimestamp(EventNotificationDBColumns.CREATED_AT),
                                rs.getTimestamp(EventNotificationDBColumns.ATTEMPT_AT)));
                    }
                }
                DatabaseUtils.commitTransaction(conn);
                return list;
            } catch (SQLException e) {
                DatabaseUtils.rollbackTransaction(conn);
                throw new EventNotificationDataAccessException(
                        String.format(EventNotificationCommonConstants.ERROR_GETTING_WEBHOOK_DELIVERY_AUDITS, deliveryId),
                        e);
            }
        } catch (RuntimeException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw e;
        } finally {
            DatabaseUtils.closeConnection(conn);
        }
    }

    @Override
    public boolean addPollDelivery(PollDelivery delivery) {
        Connection conn = DatabaseUtils.getDBConnection();
        try {
            boolean result = addPollDelivery(conn, delivery);
            DatabaseUtils.commitTransaction(conn);
            return result;
        } catch (RuntimeException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw e;
        } finally {
            DatabaseUtils.closeConnection(conn);
        }
    }

    @Override
    public boolean addPollDelivery(Connection conn, PollDelivery delivery) {
        try (PreparedStatement ps = conn.prepareStatement(getQueries(conn).getAddPollDeliveryQuery())) {
            ps.setString(1, delivery.getDeliveryId());
            ps.setString(2, delivery.getSubscriptionId());
            ps.setString(3, delivery.getEventId());
            ps.setString(4, delivery.getStatus());
            ps.setString(5, delivery.getErrorCode());
            ps.setString(6, delivery.getErrorDetail());
            ps.setTimestamp(7, delivery.getCreatedAt());
            ps.setTimestamp(8, delivery.getCompletedAt());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_ADDING_POLL_DELIVERY,
                            (delivery != null ? delivery.getDeliveryId() : "null")),
                    e);
        }
    }

    @Override
    public Optional<PollDelivery> getPollDeliveryById(String deliveryId, String orgId) {
        Connection conn = DatabaseUtils.getDBConnection();
        try {
            try (PreparedStatement ps = conn.prepareStatement(getQueries(conn).getGetPollDeliveryByIdAndOrgQuery())) {
                ps.setString(1, deliveryId);
                ps.setString(2, orgId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        PollDelivery delivery = new PollDelivery(
                                rs.getString(EventNotificationDBColumns.DELIVERY_ID),
                                rs.getString(EventNotificationDBColumns.SUBSCRIPTION_ID),
                                rs.getString(EventNotificationDBColumns.EVENT_ID),
                                rs.getString(EventNotificationDBColumns.STATUS),
                                rs.getString(EventNotificationDBColumns.ERROR_CODE),
                                rs.getString(EventNotificationDBColumns.ERROR_DETAIL),
                                rs.getTimestamp(EventNotificationDBColumns.CREATED_AT),
                                rs.getTimestamp(EventNotificationDBColumns.COMPLETED_AT));
                        DatabaseUtils.commitTransaction(conn);
                        return Optional.of(delivery);
                    }
                }
                DatabaseUtils.commitTransaction(conn);
                return Optional.empty();
            } catch (SQLException e) {
                DatabaseUtils.rollbackTransaction(conn);
                throw new EventNotificationDataAccessException(
                        String.format(EventNotificationCommonConstants.ERROR_GETTING_POLL_DELIVERY, deliveryId), e);
            }
        } catch (RuntimeException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw e;
        } finally {
            DatabaseUtils.closeConnection(conn);
        }
    }

    @Override
    public List<PollDelivery> getPendingPollDeliveries(String orgId, String groupId, String subscriptionId,
            int limit) {
        if (orgId == null || orgId.trim().isEmpty() || groupId == null || groupId.trim().isEmpty()
                || subscriptionId == null || subscriptionId.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Organization ID, Group ID, and Subscription ID are required for polling.");
        }
        Connection conn = DatabaseUtils.getDBConnection();
        try {
            List<PollDelivery> candidates = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    getQueries(conn).getGetPendingPollDeliveriesBySubscriptionQuery())) {
                ps.setString(1, orgId.trim());
                ps.setString(2, groupId.trim());
                ps.setString(3, subscriptionId.trim());
                ps.setInt(4, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        candidates.add(new PollDelivery(
                                rs.getString(EventNotificationDBColumns.DELIVERY_ID),
                                rs.getString(EventNotificationDBColumns.SUBSCRIPTION_ID),
                                rs.getString(EventNotificationDBColumns.EVENT_ID),
                                rs.getString(EventNotificationDBColumns.STATUS),
                                rs.getString(EventNotificationDBColumns.ERROR_CODE),
                                rs.getString(EventNotificationDBColumns.ERROR_DETAIL),
                                rs.getTimestamp(EventNotificationDBColumns.CREATED_AT),
                                rs.getTimestamp(EventNotificationDBColumns.COMPLETED_AT)));
                    }
                }
                DatabaseUtils.commitTransaction(conn);
                return candidates;
            } catch (SQLException e) {
                DatabaseUtils.rollbackTransaction(conn);
                throw new EventNotificationDataAccessException(
                        String.format(EventNotificationCommonConstants.ERROR_GETTING_PENDING_POLL_DELIVERIES,
                                subscriptionId), e);
            }
        } catch (RuntimeException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw e;
        } finally {
            DatabaseUtils.closeConnection(conn);
        }
    }

    @Override
    public void updatePollDeliveryStatusesByDeliveryIds(String orgId, String groupId, String subscriptionId,
            List<String> ackDeliveryIds, Map<String, PollDeliveryError> errors) {
        Connection conn = DatabaseUtils.getDBConnection();
        try {
            updatePollDeliveryStatusesByDeliveryIds(conn, orgId, groupId, subscriptionId, ackDeliveryIds, errors);
            DatabaseUtils.commitTransaction(conn);
        } catch (RuntimeException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw e;
        } finally {
            DatabaseUtils.closeConnection(conn);
        }
    }

    @Override
    public void updatePollDeliveryStatusesByDeliveryIds(Connection conn, String orgId, String groupId,
            String subscriptionId, List<String> ackDeliveryIds, Map<String, PollDeliveryError> errors) {
        if (orgId == null || orgId.trim().isEmpty() || groupId == null || groupId.trim().isEmpty()
                || subscriptionId == null || subscriptionId.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Organization ID, Group ID, and Subscription ID are required for polling updates.");
        }
        Set<String> ackIds = normalizeEventIds(ackDeliveryIds);
        Map<String, PollDeliveryError> safeErrors = errors == null ? Collections.emptyMap() : errors;
        Set<String> errorIds = normalizeEventIds(new ArrayList<>(safeErrors.keySet()));
        if (!Collections.disjoint(ackIds, errorIds)) {
            throw new IllegalArgumentException(
                    EventNotificationCommonConstants.ERROR_OVERLAPPING_POLL_COMPLETION_DELIVERY_IDS);
        }
        if (ackIds.isEmpty() && errorIds.isEmpty()) {
            return;
        }

        try {
            try (PreparedStatement updatePs = conn.prepareStatement(
                    getQueries(conn).getUpdatePollDeliveryStatusByDeliveryAndSubscriptionQuery())) {
                addSubscriptionScopedPollUpdates(updatePs, ackIds, PollStatus.ACKNOWLEDGED.getValue(),
                        Collections.emptyMap(), subscriptionId, orgId, groupId);
                addSubscriptionScopedPollUpdates(updatePs, errorIds, PollStatus.ERR.getValue(), safeErrors,
                        subscriptionId, orgId, groupId);
            }
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_UPDATING_POLL_DELIVERY_STATUSES,
                            subscriptionId), e);
        }
    }

    private static void addSubscriptionScopedPollUpdates(PreparedStatement ps, Set<String> deliveryIds,
            String status, Map<String, PollDeliveryError> errors, String subscriptionId, String orgId,
            String groupId) throws SQLException {
        for (String deliveryId : deliveryIds) {
            PollDeliveryError error = errors.get(deliveryId);
            ps.setString(1, status);
            ps.setString(2, error == null ? null : error.getCode());
            ps.setString(3, error == null ? null : error.getDescription());
            ps.setString(4, deliveryId);
            ps.setString(5, subscriptionId.trim());
            ps.setString(6, orgId.trim());
            ps.setString(7, groupId.trim());
            ps.addBatch();
        }
        if (!deliveryIds.isEmpty()) {
            ps.executeBatch();
            ps.clearBatch();
        }
    }

    private static Set<String> normalizeEventIds(List<String> eventIds) {
        Set<String> normalized = new LinkedHashSet<>();
        if (eventIds == null) {
            return normalized;
        }
        for (String eventId : eventIds) {
            if (eventId != null && !eventId.trim().isEmpty()) {
                normalized.add(eventId.trim());
            }
        }
        return normalized;
    }

    @Override
    public boolean claimWebhookDelivery(String deliveryId) {
        Connection conn = DatabaseUtils.getDBConnection();
        try {
            boolean result = claimWebhookDelivery(conn, deliveryId);
            DatabaseUtils.commitTransaction(conn);
            return result;
        } catch (RuntimeException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw e;
        } finally {
            DatabaseUtils.closeConnection(conn);
        }
    }

    @Override
    public boolean claimWebhookDelivery(Connection conn, String deliveryId) {
        try (PreparedStatement ps = conn.prepareStatement(getQueries(conn).getClaimWebhookDeliveryQuery())) {
            ps.setString(1, deliveryId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_UPDATING_WEBHOOK_DELIVERY_STATUS, deliveryId), e);
        }
    }

    @Override
    public boolean claimStuckWebhookDelivery(String deliveryId, Timestamp updatedBefore) {
        Connection conn = DatabaseUtils.getDBConnection();
        try {
            boolean result = claimStuckWebhookDelivery(conn, deliveryId, updatedBefore);
            DatabaseUtils.commitTransaction(conn);
            return result;
        } catch (RuntimeException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw e;
        } finally {
            DatabaseUtils.closeConnection(conn);
        }
    }

    @Override
    public boolean claimStuckWebhookDelivery(Connection conn, String deliveryId, Timestamp updatedBefore) {
        try (PreparedStatement ps = conn.prepareStatement(getQueries(conn).getClaimStuckWebhookDeliveryQuery())) {
            ps.setString(1, deliveryId);
            ps.setTimestamp(2, updatedBefore != null ? updatedBefore : new Timestamp(System.currentTimeMillis()));
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_UPDATING_WEBHOOK_DELIVERY_STATUS, deliveryId), e);
        }
    }

    @Override
    public boolean releaseWebhookDelivery(String deliveryId, int attemptCount, Timestamp nextRetryAt) {
        Connection conn = DatabaseUtils.getDBConnection();
        try {
            boolean result = releaseWebhookDelivery(conn, deliveryId, attemptCount, nextRetryAt);
            DatabaseUtils.commitTransaction(conn);
            return result;
        } catch (RuntimeException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw e;
        } finally {
            DatabaseUtils.closeConnection(conn);
        }
    }

    @Override
    public boolean releaseWebhookDelivery(Connection conn, String deliveryId, int attemptCount, Timestamp nextRetryAt) {
        if (deliveryId == null || deliveryId.trim().isEmpty()) {
            return false;
        }
        try (PreparedStatement ps = conn.prepareStatement(getQueries(conn).getReleaseWebhookDeliveryQuery())) {
            ps.setInt(1, attemptCount);
            ps.setTimestamp(2, nextRetryAt);
            ps.setString(3, deliveryId.trim());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_UPDATING_WEBHOOK_DELIVERY_STATUS, deliveryId), e);
        }
    }

    @Override
    public boolean claimPollDelivery(String deliveryId) {
        Connection conn = DatabaseUtils.getDBConnection();
        try {
            boolean result = claimPollDelivery(conn, deliveryId);
            DatabaseUtils.commitTransaction(conn);
            return result;
        } catch (RuntimeException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw e;
        } finally {
            DatabaseUtils.closeConnection(conn);
        }
    }

    @Override
    public boolean claimPollDelivery(Connection conn, String deliveryId) {
        try (PreparedStatement ps = conn.prepareStatement(getQueries(conn).getClaimPollDeliveryQuery())) {
            ps.setString(1, deliveryId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_UPDATING_POLL_DELIVERY_STATUS, deliveryId), e);
        }
    }

    @Override
    public boolean updatePollDeliveryStatus(String deliveryId, String status) {
        Connection conn = DatabaseUtils.getDBConnection();
        try {
            boolean result = updatePollDeliveryStatus(conn, deliveryId, status);
            DatabaseUtils.commitTransaction(conn);
            return result;
        } catch (RuntimeException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw e;
        } finally {
            DatabaseUtils.closeConnection(conn);
        }
    }

    @Override
    public boolean updatePollDeliveryStatus(Connection conn, String deliveryId, String status) {
        try (PreparedStatement ps = conn.prepareStatement(getQueries(conn).getUpdatePollDeliveryStatusQuery())) {
            ps.setString(1, status);
            ps.setTimestamp(2, new java.sql.Timestamp(System.currentTimeMillis()));
            ps.setString(3, deliveryId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_UPDATING_POLL_DELIVERY_STATUS, deliveryId), e);
        }
    }

    @Override
    public boolean updatePollDeliveryStatus(String deliveryId, String expectedStatus, String newStatus) {
        if (expectedStatus == null || expectedStatus.trim().isEmpty()) {
            return updatePollDeliveryStatus(deliveryId, newStatus);
        }
        Connection conn = DatabaseUtils.getDBConnection();
        try {
            boolean result = updatePollDeliveryStatus(conn, deliveryId, expectedStatus, newStatus);
            DatabaseUtils.commitTransaction(conn);
            return result;
        } catch (RuntimeException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw e;
        } finally {
            DatabaseUtils.closeConnection(conn);
        }
    }

    @Override
    public boolean updatePollDeliveryStatus(Connection conn, String deliveryId, String expectedStatus, String newStatus) {
        try (PreparedStatement ps = conn.prepareStatement(getQueries(conn).getUpdatePollDeliveryStatusGuardedQuery())) {
            ps.setString(1, newStatus);
            ps.setTimestamp(2, new java.sql.Timestamp(System.currentTimeMillis()));
            ps.setString(3, deliveryId);
            ps.setString(4, expectedStatus);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_UPDATING_POLL_DELIVERY_STATUS, deliveryId), e);
        }
    }

    @Override
    public List<SubscriptionDeliverySummary> listSubscriptionDeliveries(String orgId, String subscriptionId, int limit,
            int offset, int[] totalOut) {
        List<SubscriptionDeliverySummary> list = new ArrayList<>();

        Connection conn = DatabaseUtils.getDBConnection();
        try {
            try {
                EventNotificationCommonDBQueries queries = getQueries(conn);
                String baseSql = queries.getGetSubscriptionDeliveriesUnionBaseQuery();
                String countSql = "SELECT COUNT(*) FROM (" + baseSql + ") AS u";
                String pageSql = baseSql + queries.getPaginationClause("DELIVERY_CREATED_AT DESC");
                try (PreparedStatement countPs = conn.prepareStatement(countSql)) {
                    countPs.setString(1, subscriptionId);
                    countPs.setString(2, orgId);
                    countPs.setString(3, subscriptionId);
                    countPs.setString(4, orgId);
                    try (ResultSet rs = countPs.executeQuery()) {
                        if (rs.next()) {
                            totalOut[0] = rs.getInt(1);
                        }
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(pageSql)) {
                    ps.setString(1, subscriptionId);
                    ps.setString(2, orgId);
                    ps.setString(3, subscriptionId);
                    ps.setString(4, orgId);
                    ps.setInt(5, limit);
                    ps.setInt(6, offset);

                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            list.add(mapSummary(rs));
                        }
                    }
                }
                DatabaseUtils.commitTransaction(conn);
                return list;
            } catch (SQLException e) {
                DatabaseUtils.rollbackTransaction(conn);
                throw new EventNotificationDataAccessException(
                        String.format(EventNotificationCommonConstants.ERROR_LISTING_DELIVERIES_FOR_SUBSCRIPTION,
                                subscriptionId),
                        e);
            }
        } catch (RuntimeException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw e;
        } finally {
            DatabaseUtils.closeConnection(conn);
        }
    }

    @Override
    public Optional<SubscriptionDeliverySummary> getSubscriptionDeliveryById(String orgId, String subscriptionId, String deliveryId) {
        Connection conn = DatabaseUtils.getDBConnection();
        try {
            try (PreparedStatement ps = conn.prepareStatement(getQueries(conn).getGetSubscriptionDeliveryByIdQuery())) {
                ps.setString(1, subscriptionId);
                ps.setString(2, deliveryId);
                ps.setString(3, orgId);
                ps.setString(4, subscriptionId);
                ps.setString(5, deliveryId);
                ps.setString(6, orgId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        SubscriptionDeliverySummary summary = mapSummary(rs);
                        DatabaseUtils.commitTransaction(conn);
                        return Optional.of(summary);
                    }
                }
                DatabaseUtils.commitTransaction(conn);
                return Optional.empty();
            } catch (SQLException e) {
                DatabaseUtils.rollbackTransaction(conn);
                throw new EventNotificationDataAccessException(
                        String.format(EventNotificationCommonConstants.ERROR_GETTING_SUBSCRIPTION_DELIVERY, deliveryId), e);
            }
        } catch (RuntimeException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw e;
        } finally {
            DatabaseUtils.closeConnection(conn);
        }
    }

    @Override
    public List<SubscriptionDeliverySummary> listOrgDeliveries(String orgId, String statusFilter,
            String subscriptionIdFilter, String groupIdFilter, String purposesFilter, String search, int limit, int offset, int[] totalOut) {
        List<SubscriptionDeliverySummary> list = new ArrayList<>();
        StringBuilder outerWhere = new StringBuilder();
        List<Object> outerParams = new ArrayList<>();

        if (statusFilter != null && !statusFilter.trim().isEmpty()) {
            outerWhere.append(outerWhere.length() == 0 ? " WHERE " : " AND ");
            outerWhere.append("LOWER(CURRENT_STATUS) = ?");
            outerParams.add(statusFilter.trim().toLowerCase(Locale.ROOT));
        }

        if (subscriptionIdFilter != null && !subscriptionIdFilter.trim().isEmpty()) {
            outerWhere.append(outerWhere.length() == 0 ? " WHERE " : " AND ");
            outerWhere.append("SUBSCRIPTION_ID = ?");
            outerParams.add(subscriptionIdFilter.trim());
        }

        if (groupIdFilter != null && !groupIdFilter.trim().isEmpty()) {
            outerWhere.append(outerWhere.length() == 0 ? " WHERE " : " AND ");
            outerWhere.append("GROUP_ID = ?");
            outerParams.add(groupIdFilter.trim());
        }

        if (purposesFilter != null && !purposesFilter.trim().isEmpty()) {
            String[] purposeTokens = purposesFilter.split(",");
            List<String> validTokens = new ArrayList<>();
            for (String token : purposeTokens) {
                if (token != null && !token.trim().isEmpty()) {
                    validTokens.add(token.trim().toLowerCase(Locale.ROOT));
                }
            }
            if (!validTokens.isEmpty()) {
                outerWhere.append(outerWhere.length() == 0 ? " WHERE " : " AND ");
                outerWhere.append("EVENT_ID IN (SELECT EVENT_ID FROM EVENT_PURPOSE WHERE LOWER(PURPOSE_NAME) IN (");
                for (int i = 0; i < validTokens.size(); i++) {
                    outerWhere.append(i == 0 ? "?" : ", ?");
                    outerParams.add(validTokens.get(i));
                }
                outerWhere.append("))");
            }
        }

        if (search != null && !search.trim().isEmpty()) {
            outerWhere.append(outerWhere.length() == 0 ? " WHERE " : " AND ");
            outerWhere.append("(")
                    .append(QueryBuilderUtils.buildEscapedLikePredicate("LOWER(DELIVERY_ID)"))
                    .append(" OR ").append(QueryBuilderUtils.buildEscapedLikePredicate("LOWER(EVENT_ID)"))
                    .append(" OR ").append(QueryBuilderUtils.buildEscapedLikePredicate("LOWER(GROUP_ID)"))
                    .append(" OR ").append(QueryBuilderUtils.buildEscapedLikePredicate("LOWER(TOPIC_NAME)"))
                    .append(")");
            String term = QueryBuilderUtils.buildCaseInsensitiveContainsPattern(search);
            outerParams.add(term);
            outerParams.add(term);
            outerParams.add(term);
            outerParams.add(term);
        }

        Connection conn = DatabaseUtils.getDBConnection();
        try {
            try {
                EventNotificationCommonDBQueries queries = getQueries(conn);
                StringBuilder unionSql = new StringBuilder(queries.getGetOrgDeliveriesUnionBaseQuery());
                List<Object> unionParams = new ArrayList<>(Arrays.asList(orgId, orgId));

                String wrappedSql = "SELECT * FROM (" + unionSql + ") AS u" + outerWhere;
                String countSql = "SELECT COUNT(*) FROM (" + wrappedSql + ") AS c";
                String pageSql = wrappedSql + queries.getPaginationClause("DELIVERY_CREATED_AT DESC");

                try (PreparedStatement countPs = conn.prepareStatement(countSql)) {
                    int paramIdx = 1;
                    for (Object p : unionParams) {
                        countPs.setObject(paramIdx++, p);
                    }
                    for (Object p : outerParams) {
                        countPs.setObject(paramIdx++, p);
                    }
                    try (ResultSet rs = countPs.executeQuery()) {
                        if (rs.next()) {
                            totalOut[0] = rs.getInt(1);
                        }
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(pageSql)) {
                    int paramIdx = 1;
                    for (Object p : unionParams) {
                        ps.setObject(paramIdx++, p);
                    }
                    for (Object p : outerParams) {
                        ps.setObject(paramIdx++, p);
                    }
                    ps.setInt(paramIdx++, limit);
                    ps.setInt(paramIdx++, offset);

                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            list.add(mapSummary(rs));
                        }
                    }
                }
                DatabaseUtils.commitTransaction(conn);
                return list;
            } catch (SQLException e) {
                DatabaseUtils.rollbackTransaction(conn);
                throw new EventNotificationDataAccessException(
                        String.format(EventNotificationCommonConstants.ERROR_LISTING_ORG_DELIVERIES, orgId), e);
            }
        } catch (RuntimeException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw e;
        } finally {
            DatabaseUtils.closeConnection(conn);
        }
    }

    @Override
    public Optional<SubscriptionDeliverySummary> getOrgDeliveryById(String orgId, String deliveryId) {
        Connection conn = DatabaseUtils.getDBConnection();
        try {
            try (PreparedStatement ps = conn.prepareStatement(getQueries(conn).getGetOrgDeliveryByIdQuery())) {
                ps.setString(1, orgId);
                ps.setString(2, orgId);
                ps.setString(3, deliveryId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        SubscriptionDeliverySummary summary = mapSummary(rs);
                        DatabaseUtils.commitTransaction(conn);
                        return Optional.of(summary);
                    }
                }
                DatabaseUtils.commitTransaction(conn);
                return Optional.empty();
            } catch (SQLException e) {
                DatabaseUtils.rollbackTransaction(conn);
                throw new EventNotificationDataAccessException(
                        String.format(EventNotificationCommonConstants.ERROR_GETTING_ORG_DELIVERY, deliveryId), e);
            }
        } catch (RuntimeException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw e;
        } finally {
            DatabaseUtils.closeConnection(conn);
        }
    }

    @Override
    public List<SubscriptionDeliverySummary> listEventDeliveries(String orgId, String eventId, int limit, int offset, int[] totalOut) {
        if (orgId == null || orgId.trim().isEmpty() || eventId == null || eventId.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<SubscriptionDeliverySummary> list = new ArrayList<>();
        StringBuilder outerWhere = new StringBuilder(" WHERE EVENT_ID = ?");
        List<Object> outerParams = new ArrayList<>(Collections.singletonList(eventId.trim()));

        Connection conn = DatabaseUtils.getDBConnection();
        try {
            try {
                EventNotificationCommonDBQueries queries = getQueries(conn);
                StringBuilder unionSql = new StringBuilder(queries.getGetOrgDeliveriesUnionBaseQuery());
                List<Object> unionParams = new ArrayList<>(Arrays.asList(orgId, orgId));

                String wrappedSql = "SELECT * FROM (" + unionSql + ") AS u" + outerWhere;
                String countSql = "SELECT COUNT(*) FROM (" + wrappedSql + ") AS c";
                String pageSql = wrappedSql + queries.getPaginationClause("DELIVERY_CREATED_AT DESC");

                if (totalOut != null && totalOut.length > 0) {
                    try (PreparedStatement countPs = conn.prepareStatement(countSql)) {
                        int paramIdx = 1;
                        for (Object p : unionParams) {
                            countPs.setObject(paramIdx++, p);
                        }
                        for (Object p : outerParams) {
                            countPs.setObject(paramIdx++, p);
                        }
                        try (ResultSet rs = countPs.executeQuery()) {
                            if (rs.next()) {
                                totalOut[0] = rs.getInt(1);
                            }
                        }
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(pageSql)) {
                    int paramIdx = 1;
                    for (Object p : unionParams) {
                        ps.setObject(paramIdx++, p);
                    }
                    for (Object p : outerParams) {
                        ps.setObject(paramIdx++, p);
                    }
                    ps.setInt(paramIdx++, limit);
                    ps.setInt(paramIdx++, offset);

                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            list.add(mapSummary(rs));
                        }
                    }
                }
                DatabaseUtils.commitTransaction(conn);
                return list;
            } catch (SQLException e) {
                DatabaseUtils.rollbackTransaction(conn);
                throw new EventNotificationDataAccessException(
                        String.format(EventNotificationCommonConstants.ERROR_LISTING_ORG_DELIVERIES, orgId), e);
            }
        } catch (RuntimeException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw e;
        } finally {
            DatabaseUtils.closeConnection(conn);
        }
    }

    private SubscriptionDeliverySummary mapSummary(ResultSet rs) throws SQLException {
        return new SubscriptionDeliverySummary(
                rs.getString(EventNotificationDBColumns.DELIVERY_ID),
                rs.getString(EventNotificationDBColumns.EVENT_ID),
                rs.getString(EventNotificationDBColumns.SUBSCRIPTION_ID),
                rs.getString(EventNotificationDBColumns.GROUP_ID),
                rs.getString(EventNotificationDBColumns.TOPIC_NAME),
                rs.getString(EventNotificationDBColumns.CURRENT_STATUS),
                rs.getString(EventNotificationDBColumns.DELIVERY_MODE),
                rs.getTimestamp(EventNotificationDBColumns.OCCURRED_AT),
                rs.getTimestamp(EventNotificationDBColumns.DELIVERY_CREATED_AT),
                rs.getString(EventNotificationDBColumns.PAYLOAD));
    }

    private DPDPConfigurationService getConfiguration() {
        if (configurationService == null) {
            return new org.wso2.dpdp.accelerator.common.config.DPDPConfigurationServiceImpl(false);
        }
        return configurationService;
    }
}
