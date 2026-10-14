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
import org.wso2.dpdp.accelerator.event.notifications.common.enums.Initiator;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.TopicStatus;
import org.wso2.dpdp.accelerator.event.notifications.common.exception.dao.EventNotificationDaoException;
import org.wso2.dpdp.accelerator.event.notifications.common.exception.dao.EventNotificationDuplicateResourceException;
import org.wso2.dpdp.accelerator.event.notifications.common.exception.dao.EventNotificationInvalidStateException;
import org.wso2.dpdp.accelerator.event.notifications.dao.PaginatedDAOResult;
import org.wso2.dpdp.accelerator.event.notifications.dao.TopicDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.constants.EventNotificationDBColumns;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Topic;
import org.wso2.dpdp.accelerator.event.notifications.dao.queries.EventNotificationCommonDBQueries;
import org.wso2.dpdp.accelerator.event.notifications.dao.queries.EventNotificationQueryFactory;
import org.wso2.dpdp.accelerator.event.notifications.dao.queries.QueryResult;
import org.wso2.dpdp.accelerator.event.notifications.dao.queries.TopicQueryBuilder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class TopicDAOImpl implements TopicDAO {

    private EventNotificationCommonDBQueries getQueries(Connection conn) {
        return EventNotificationQueryFactory.getQueryProvider(conn);
    }

    @Override
    public boolean addTopic(Connection conn, Topic topic) {
        Objects.requireNonNull(conn, "Connection cannot be null.");
        Objects.requireNonNull(topic, EventNotificationCommonConstants.ERROR_TOPIC_NULL);
        try (PreparedStatement checkPs = conn
                .prepareStatement(getQueries(conn).getGetTopicByOrgAndNameQuery())) {
            checkPs.setString(1, topic.getOrgId());
            checkPs.setString(2, topic.getName());
            try (ResultSet rs = checkPs.executeQuery()) {
                if (rs.next() && TopicStatus.ACTIVE.getValue().equalsIgnoreCase(
                        rs.getString(EventNotificationDBColumns.STATUS))) {
                    throw new EventNotificationDuplicateResourceException(
                            String.format(EventNotificationCommonConstants.ERROR_TOPIC_ALREADY_EXISTS,
                                    topic.getName()));
                }
            }
        } catch (SQLException e) {
            if (e.getSQLState() != null && e.getSQLState().startsWith("23")) {
                throw new EventNotificationDuplicateResourceException(
                        String.format(EventNotificationCommonConstants.ERROR_TOPIC_ALREADY_EXISTS, topic.getName()), e);
            }
            throw new EventNotificationDaoException(
                    String.format(EventNotificationCommonConstants.ERROR_ADDING_TOPIC, topic.getName()), e);
        }

        try (PreparedStatement ps = conn.prepareStatement(getQueries(conn).getAddTopicQuery())) {
            ps.setString(1, topic.getTopicId());
            ps.setString(2, topic.getOrgId());
            ps.setString(3, topic.getName());
            ps.setString(4, topic.getDescription());
            ps.setString(5, topic.getStatus());
            ps.setString(6, topic.getInitiatedBy() != null ? topic.getInitiatedBy() : Initiator.USER.getValue());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            if (e.getSQLState() != null && e.getSQLState().startsWith("23")) {
                throw new EventNotificationDuplicateResourceException(
                        String.format(EventNotificationCommonConstants.ERROR_TOPIC_ALREADY_EXISTS, topic.getName()), e);
            }
            throw new EventNotificationDaoException(
                    String.format(EventNotificationCommonConstants.ERROR_ADDING_TOPIC, topic.getName()), e);
        }
    }

    @Override
    public Optional<Topic> getTopicById(Connection conn, String topicId, String orgId) {
        if (conn == null) {
            throw new IllegalArgumentException("Connection cannot be null.");
        }
        try (PreparedStatement ps = conn.prepareStatement(getQueries(conn).getGetTopicByIdQuery())) {
            ps.setString(1, topicId);
            ps.setString(2, orgId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapTopic(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new EventNotificationDaoException(
                    String.format(EventNotificationCommonConstants.ERROR_GETTING_TOPIC_BY_ID, topicId), e);
        }
    }

    @Override
    public Optional<Topic> getTopicByOrgAndName(Connection conn, String orgId, String name) {
        if (conn == null) {
            throw new IllegalArgumentException("Connection cannot be null.");
        }
        try (PreparedStatement ps = conn.prepareStatement(getQueries(conn).getGetTopicByOrgAndNameQuery())) {
            ps.setString(1, orgId);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapTopic(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new EventNotificationDaoException(
                    String.format(EventNotificationCommonConstants.ERROR_GETTING_TOPIC_BY_ORG_AND_NAME, orgId, name),
                    e);
        }
    }

    @Override
    public Optional<Topic> getActiveTopicByOrgAndNameForUpdate(Connection conn, String orgId, String name) {
        if (conn == null) {
            throw new IllegalArgumentException("Connection cannot be null.");
        }
        try (PreparedStatement ps = conn.prepareStatement(
                getQueries(conn).getActiveTopicByOrgAndNameForUpdateQuery())) {
            ps.setString(1, orgId);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapTopic(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new EventNotificationDaoException(
                    String.format(EventNotificationCommonConstants.ERROR_GETTING_TOPIC_BY_ORG_AND_NAME, orgId, name),
                    e);
        }
    }

    @Override
    public boolean updateTopicStatus(Connection conn, String topicId, String orgId, TopicStatus status) {
        Objects.requireNonNull(conn, "Connection cannot be null.");
        Objects.requireNonNull(status, EventNotificationCommonConstants.ERROR_TOPIC_STATUS_NULL);
        try (PreparedStatement ps = conn.prepareStatement(getQueries(conn).getUpdateTopicStatusQuery())) {
            ps.setString(1, status.getValue());
            ps.setString(2, topicId);
            ps.setString(3, orgId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new EventNotificationDaoException(
                    String.format(EventNotificationCommonConstants.ERROR_UPDATING_TOPIC_STATUS, topicId), e);
        }
    }

    @Override
    public boolean deleteTopicAtomic(Connection conn, String topicId, String orgId) {
        Objects.requireNonNull(conn, "Connection cannot be null.");
        try {
            EventNotificationCommonDBQueries queries = getQueries(conn);

            // Lock the topic row so concurrent subscription creates serialize with us.
            try (PreparedStatement topicLockPs = conn
                        .prepareStatement(queries.getLockTopicForSubscriptionQuery())) {
                    topicLockPs.setString(1, topicId);
                    topicLockPs.setString(2, orgId);
                    if (topicLockPs.executeUpdate() == 0) {
                        return false;
                    }
                }

            // Count non-deleted subscriptions under the topic lock.
            long activeCount;
            try (PreparedStatement countPs = conn
                        .prepareStatement(queries.getCountActiveSubscriptionsForTopicQuery())) {
                    countPs.setString(1, orgId);
                    countPs.setString(2, topicId);
                    try (ResultSet rs = countPs.executeQuery()) {
                        activeCount = rs.next() ? rs.getLong(1) : 0;
                    }
                }

            if (activeCount > 0) {
                    throw new EventNotificationInvalidStateException(
                            EventNotificationCommonConstants.ERROR_TOPIC_HAS_ACTIVE_SUBSCRIPTIONS);
                }

            int updated;
            try (PreparedStatement ps = conn.prepareStatement(queries.getUpdateTopicStatusGuardedQuery())) {
                    ps.setString(1, TopicStatus.DELETED.getValue());
                    ps.setString(2, topicId);
                    ps.setString(3, orgId);
                    ps.setString(4, TopicStatus.ACTIVE.getValue());
                    updated = ps.executeUpdate();
                }

            return updated > 0;
        } catch (SQLException e) {
            throw new EventNotificationDaoException(
                    String.format(EventNotificationCommonConstants.ERROR_DELETING_TOPIC, topicId), e);
        }
    }

    @Override
    public boolean deregisterTopicAtomic(Connection conn, String topicId, String orgId) {
        return deleteTopicAtomic(conn, topicId, orgId);
    }

    @Override
    public PaginatedDAOResult<Topic> listTopics(Connection conn, String orgId, String status, String search, int limit, int offset,
            String sort) {
        if (conn == null) {
            throw new IllegalArgumentException("Connection cannot be null.");
        }
        List<Topic> topics = new ArrayList<>();
        TopicQueryBuilder builder = new TopicQueryBuilder(orgId)
                .setStatus(status)
                .setSearch(search)
                .setSort(sort);

        int total = 0;
        try {
            EventNotificationCommonDBQueries queries = getQueries(conn);
            QueryResult countResult = builder.buildCountQuery();
            QueryResult selectResult = builder.buildSelectQuery(
                    queries.getPaginationClause(builder.resolveSortColumn()));

            try (PreparedStatement countPs = conn.prepareStatement(countResult.getSql())) {
                List<Object> countParams = countResult.getParameters();
                for (int i = 0; i < countParams.size(); i++) {
                    countPs.setObject(i + 1, countParams.get(i));
                }
                try (ResultSet rs = countPs.executeQuery()) {
                    if (rs.next()) {
                        total = rs.getInt(1);
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
                        topics.add(mapTopic(rs));
                    }
                }
            }
            return new PaginatedDAOResult<>(topics, total);
        } catch (SQLException e) {
            throw new EventNotificationDaoException(
                    String.format(EventNotificationCommonConstants.ERROR_LISTING_TOPICS, orgId), e);
        }
    }

    @Override
    public List<Topic> getTopicsByIds(Connection conn, List<String> topicIds, String orgId) {
        if (conn == null) {
            throw new IllegalArgumentException("Connection cannot be null.");
        }
        if (topicIds == null || topicIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<Topic> topics = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(getQueries(conn).getTopicsByIdsQuery(topicIds.size()))) {
            ps.setString(1, orgId);
            for (int i = 0; i < topicIds.size(); i++) {
                ps.setString(i + 2, topicIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    topics.add(mapTopic(rs));
                }
            }
        } catch (SQLException e) {
            throw new EventNotificationDaoException(
                    String.format(EventNotificationCommonConstants.ERROR_LISTING_TOPICS, orgId), e);
        }
        return topics;
    }

    @Override
    public Map<String, Topic> getTopicsByOrgAndNames(Connection conn, String orgId, List<String> lowerNames) {
        if (conn == null) {
            throw new IllegalArgumentException("Connection cannot be null.");
        }
        Map<String, Topic> result = new HashMap<>();
        if (lowerNames == null || lowerNames.isEmpty()) {
            return result;
        }
        try (PreparedStatement ps = conn.prepareStatement(
                getQueries(conn).getTopicsByOrgAndNamesQuery(lowerNames.size()))) {
            ps.setString(1, orgId);
            for (int i = 0; i < lowerNames.size(); i++) {
                ps.setString(i + 2, lowerNames.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Topic t = mapTopic(rs);
                    result.put(t.getName().toLowerCase(Locale.ROOT), t);
                }
            }
        } catch (SQLException e) {
            throw new EventNotificationDaoException(
                    String.format(EventNotificationCommonConstants.ERROR_LISTING_TOPICS, orgId), e);
        }
        return result;
    }

    private Topic mapTopic(ResultSet rs) throws SQLException {
        return new Topic(
                rs.getString(EventNotificationDBColumns.TOPIC_ID),
                rs.getString(EventNotificationDBColumns.ORG_ID),
                rs.getString(EventNotificationDBColumns.NAME),
                rs.getString(EventNotificationDBColumns.DESCRIPTION),
                rs.getString(EventNotificationDBColumns.STATUS),
                rs.getString(EventNotificationDBColumns.INITIATED_BY));
    }
}
