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

import org.wso2.dpdp.accelerator.event.notifications.common.enums.TopicStatus;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Topic;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface TopicDAO {

    boolean addTopic(Connection conn, Topic topic);

    Optional<Topic> getTopicById(Connection conn, String topicId, String orgId);

    Optional<Topic> getTopicByOrgAndName(Connection conn, String orgId, String name);

    Optional<Topic> getActiveTopicByOrgAndNameForUpdate(Connection conn, String orgId, String name);

    boolean updateTopicStatus(Connection conn, String topicId, String orgId, TopicStatus status);

    boolean deleteTopicAtomic(Connection conn, String topicId, String orgId);

    default boolean deregisterTopicAtomic(Connection conn, String topicId, String orgId) {
        return deleteTopicAtomic(conn, topicId, orgId);
    }

    PaginatedDAOResult<Topic> listTopics(Connection conn, String orgId, String status, String search, int limit, int offset,
            String sort);

    /**
     * Batch-fetches topics by ID list. Returns only rows found; missing IDs are silently skipped.
     * Connection first — the caller owns the transaction.
     */
    List<Topic> getTopicsByIds(Connection conn, List<String> topicIds, String orgId);

    /**
     * Batch-resolves active topics by (lowercased) name. Returns only matches;
     * missing names are the caller's responsibility to detect via a set-difference.
     */
    Map<String, Topic> getTopicsByOrgAndNames(Connection conn, String orgId, List<String> lowerNames);
}
