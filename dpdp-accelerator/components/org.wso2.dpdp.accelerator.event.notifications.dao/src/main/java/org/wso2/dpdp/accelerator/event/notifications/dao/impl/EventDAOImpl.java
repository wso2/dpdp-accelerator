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

import org.wso2.dpdp.accelerator.event.notifications.common.constants.EventNotificationCommonConstants;
import org.wso2.dpdp.accelerator.event.notifications.common.exception.EventNotificationDataAccessException;
import org.wso2.dpdp.accelerator.event.notifications.dao.constants.EventNotificationDBColumns;
import org.wso2.dpdp.accelerator.event.notifications.common.exception.EventNotificationDuplicateResourceException;
import org.wso2.dpdp.accelerator.common.util.DatabaseUtils;
import org.wso2.dpdp.accelerator.event.notifications.dao.EventDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.PaginatedDAOResult;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Event;
import org.wso2.dpdp.accelerator.event.notifications.dao.queries.EventNotificationCommonDBQueries;
import org.wso2.dpdp.accelerator.event.notifications.dao.queries.EventNotificationQueryFactory;
import org.wso2.dpdp.accelerator.event.notifications.dao.queries.EventQueryBuilder;
import org.wso2.dpdp.accelerator.event.notifications.dao.queries.QueryResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class EventDAOImpl implements EventDAO {

    private EventNotificationCommonDBQueries getQueries(Connection conn) {
        return EventNotificationQueryFactory.getQueryProvider(conn);
    }

    @Override
    public boolean addEvent(Connection conn, Event event) {
        if (conn == null) {
            throw new IllegalArgumentException("Connection cannot be null.");
        }
        try (PreparedStatement ps = conn.prepareStatement(getQueries(conn).getAddEventQuery())) {
            ps.setString(1, event.getEventId());
            ps.setString(2, event.getOrgId());
            ps.setString(3, event.getGroupId());
            ps.setString(4, event.getTopicId());
            ps.setString(5, event.getPayload());
            ps.setTimestamp(6,
                    event.getCreatedAt() != null ? event.getCreatedAt() : new Timestamp(System.currentTimeMillis()));
            ps.setString(7, event.getTopicId());
            ps.setString(8, event.getOrgId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            if (e.getSQLState() != null && e.getSQLState().startsWith("23")) {
                throw new EventNotificationDuplicateResourceException(
                        String.format(EventNotificationCommonConstants.ERROR_ADDING_EVENT,
                                event != null ? event.getEventId() : "null"),
                        e);
            }
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_ADDING_EVENT,
                            event != null ? event.getEventId() : "null"),
                    e);
        }
    }

    @Override
    public Optional<Event> getEventById(String eventId, String orgId) {
        Connection conn = DatabaseUtils.getDBConnection();
        try {
            try (PreparedStatement ps = conn.prepareStatement(getQueries(conn).getGetEventByIdQuery())) {
                ps.setString(1, eventId);
                ps.setString(2, orgId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Event event = mapEvent(rs);
                        event.setPurposes(getEventPurposes(conn, eventId));
                        DatabaseUtils.commitTransaction(conn);
                        return Optional.of(event);
                    }
                }
                DatabaseUtils.commitTransaction(conn);
                return Optional.empty();
            } catch (SQLException e) {
                DatabaseUtils.rollbackTransaction(conn);
                throw new EventNotificationDataAccessException(
                        String.format(EventNotificationCommonConstants.ERROR_GETTING_EVENT_BY_ID, eventId), e);
            }
        } catch (RuntimeException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw e;
        } finally {
            DatabaseUtils.closeConnection(conn);
        }
    }

    @Override
    public void addEventPurposes(Connection conn, String eventId, List<String> purposes) {
        if (conn == null) {
            throw new IllegalArgumentException("Connection cannot be null.");
        }
        if (purposes == null || purposes.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = conn.prepareStatement(getQueries(conn).getAddEventPurposeQuery())) {
            for (String purpose : purposes) {
                if (purpose != null && !purpose.trim().isEmpty()) {
                    ps.setString(1, eventId);
                    ps.setString(2, purpose.trim());
                    ps.addBatch();
                }
            }
            ps.executeBatch();
        } catch (SQLException e) {
            if (e.getSQLState() != null && e.getSQLState().startsWith("23")) {
                throw new EventNotificationDuplicateResourceException(
                        String.format(EventNotificationCommonConstants.ERROR_ADDING_EVENT_PURPOSES, eventId), e);
            }
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_ADDING_EVENT_PURPOSES, eventId), e);
        }
    }

    @Override
    public List<String> getEventPurposes(String eventId) {
        Connection conn = DatabaseUtils.getDBConnection();
        try {
            List<String> purposes = getEventPurposes(conn, eventId);
            DatabaseUtils.commitTransaction(conn);
            return purposes;
        } catch (RuntimeException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw e;
        } finally {
            DatabaseUtils.closeConnection(conn);
        }
    }

    @Override
    public List<String> getEventPurposes(Connection conn, String eventId) {
        List<String> purposes = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(getQueries(conn).getGetEventPurposesQuery())) {
            ps.setString(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    purposes.add(rs.getString(EventNotificationDBColumns.PURPOSE_NAME));
                }
            }
            return purposes;
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_GETTING_EVENT_PURPOSES, eventId), e);
        }
    }

    @Override
    public boolean hasActiveEventsForTopic(String topicId) {
        Connection conn = DatabaseUtils.getDBConnection();
        try {
            try (PreparedStatement ps = conn.prepareStatement(getQueries(conn).getHasActiveEventsForTopicQuery())) {
                ps.setString(1, topicId);
                boolean hasActive;
                try (ResultSet rs = ps.executeQuery()) {
                    hasActive = rs.next();
                }
                DatabaseUtils.commitTransaction(conn);
                return hasActive;
            } catch (SQLException e) {
                DatabaseUtils.rollbackTransaction(conn);
                throw new EventNotificationDataAccessException(
                        String.format(EventNotificationCommonConstants.ERROR_HAS_ACTIVE_EVENTS_FOR_TOPIC, topicId), e);
            }
        } catch (RuntimeException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw e;
        } finally {
            DatabaseUtils.closeConnection(conn);
        }
    }

    @Override
    public PaginatedDAOResult<Event> searchEvents(String orgId, String topic, String status, String groupId,
            String subscriptionId, String purposes, String search, int limit, int offset) {
        List<Event> events = new ArrayList<>();
        int[] total = {0};
        Connection conn = DatabaseUtils.getDBConnection();
        try {
          try {
            EventNotificationCommonDBQueries queries = getQueries(conn);
            EventQueryBuilder builder = new EventQueryBuilder(orgId, queries)
                    .setTopic(topic)
                    .setStatus(status)
                    .setGroupId(groupId)
                    .setSubscriptionId(subscriptionId)
                    .setPurposes(purposes)
                    .setSearch(search);
            String sortColumn = builder.resolveSortColumn();
            QueryResult countResult = builder.buildCountQuery(queries.getCountEventsBaseQuery());
            QueryResult selectResult = builder.buildSelectQuery(queries.getListEventsBaseQuery(),
                    queries.getPaginationClause(sortColumn));

            try (PreparedStatement countPs = conn.prepareStatement(countResult.getSql())) {
                List<Object> countParams = countResult.getParameters();
                for (int i = 0; i < countParams.size(); i++) {
                    countPs.setObject(i + 1, countParams.get(i));
                }
                try (ResultSet rs = countPs.executeQuery()) {
                    if (rs.next()) {
                        total[0] = rs.getInt(1);
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(selectResult.getSql())) {
                List<Object> selectParams = selectResult.getParameters();
                for (int i = 0; i < selectParams.size(); i++) {
                    ps.setObject(i + 1, selectParams.get(i));
                }
                ps.setInt(selectParams.size() + 1, limit);
                ps.setInt(selectParams.size() + 2, offset);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        events.add(mapEvent(rs));
                    }
                }
            }

            if (!events.isEmpty()) {
                List<String> eventIds = events.stream().map(Event::getEventId).collect(Collectors.toList());
                Map<String, List<String>> purposeMap = getPurposesByEventIds(conn, eventIds);
                for (Event e : events) {
                    e.setPurposes(purposeMap.getOrDefault(e.getEventId(), Collections.emptyList()));
                }
            }

            PaginatedDAOResult<Event> result = new PaginatedDAOResult<>(events, total[0]);
            DatabaseUtils.commitTransaction(conn);
            return result;
          } catch (SQLException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_LISTING_EVENTS, orgId), e);
          }
        } catch (RuntimeException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw e;
        } finally {
            DatabaseUtils.closeConnection(conn);
        }
    }

    private Event mapEvent(ResultSet rs) throws SQLException {
        Event event = new Event(
                rs.getString(EventNotificationDBColumns.EVENT_ID),
                rs.getString(EventNotificationDBColumns.ORG_ID),
                rs.getString(EventNotificationDBColumns.GROUP_ID),
                rs.getString(EventNotificationDBColumns.TOPIC_ID),
                rs.getString(EventNotificationDBColumns.PAYLOAD),
                rs.getTimestamp(EventNotificationDBColumns.CREATED_AT));
        try {
            event.setTopic(rs.getString(EventNotificationDBColumns.TOPIC_NAME));
        } catch (SQLException ignored) {
            // Column may not be present in all query projections
        }
        try {
            event.setDeliveriesCount(rs.getInt(EventNotificationDBColumns.DELIVERIES_COUNT));
        } catch (SQLException ignored) {
            // Column may not be present in all query projections
        }
        return event;
    }

    /**
     * Batched purpose lookup. Mirrors
     * {@code SubscriptionDAOImpl.getPurposesBySubscriptionIds} to avoid N+1 when
     * rendering the search response.
     */
    private Map<String, List<String>> getPurposesByEventIds(Connection conn, List<String> eventIds)
            throws SQLException {
        if (eventIds == null || eventIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, List<String>> purposeMap = new HashMap<>();
        String placeholders = String.join(", ", Collections.nCopies(eventIds.size(), "?"));
        String sql;
        sql = String.format(
                getQueries(conn).getGetPurposesByEventIdsTemplate(), placeholders);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < eventIds.size(); i++) {
                ps.setString(i + 1, eventIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String eventId = rs.getString(EventNotificationDBColumns.EVENT_ID);
                    String purpose = rs.getString(EventNotificationDBColumns.PURPOSE_NAME);
                    purposeMap.computeIfAbsent(eventId, k -> new ArrayList<>()).add(purpose);
                }
            }
        }
        return purposeMap;
    }
}
