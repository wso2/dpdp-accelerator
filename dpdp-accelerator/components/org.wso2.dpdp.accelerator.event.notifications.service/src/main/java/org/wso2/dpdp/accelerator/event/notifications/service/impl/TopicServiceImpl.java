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

package org.wso2.dpdp.accelerator.event.notifications.service.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.dpdp.accelerator.common.util.DatabaseUtils;
import org.wso2.dpdp.accelerator.common.util.LogSanitizer;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.DefaultTopic;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.Initiator;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.TopicStatus;
import org.wso2.dpdp.accelerator.event.notifications.common.exception.dao.EventNotificationDaoException;
import org.wso2.dpdp.accelerator.event.notifications.common.exception.dao.EventNotificationDuplicateResourceException;
import org.wso2.dpdp.accelerator.event.notifications.common.exception.dao.EventNotificationInvalidStateException;
import org.wso2.dpdp.accelerator.event.notifications.common.exception.service.EventNotificationServiceException;
import org.wso2.dpdp.accelerator.event.notifications.dao.PaginatedDAOResult;
import org.wso2.dpdp.accelerator.event.notifications.dao.TopicDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Topic;
import org.wso2.dpdp.accelerator.event.notifications.service.TopicService;
import org.wso2.dpdp.accelerator.event.notifications.service.constants.EventNotificationServiceConstants;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.TopicDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.model.PaginatedResult;
import org.wso2.dpdp.accelerator.event.notifications.service.util.EventNotificationParameterUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class TopicServiceImpl implements TopicService {

    private static final Log LOG = LogFactory.getLog(TopicServiceImpl.class);

    private TopicDAO topicDAO;

    public TopicServiceImpl() {
    }

    public TopicServiceImpl(TopicDAO topicDAO) {
        this.topicDAO = topicDAO;
    }

    @Override
    public TopicDTO createTopic(String orgId, String name, String description) {
        if (orgId == null || orgId.trim().isEmpty() || name == null || name.trim().isEmpty()) {
            throw new EventNotificationServiceException(
                    EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.ORG_ID_OR_TOPIC_NAME_MISSING_ERROR_MSG,
                    400);
        }

        String topicId = UUID.randomUUID().toString();
        Topic topic = new Topic(topicId, orgId.trim(), name.trim(), description != null ? description.trim() : null,
                TopicStatus.ACTIVE.getValue(), Initiator.USER.getValue());

        return DatabaseUtils.executeInTransaction(conn -> {
            try {
                Optional<Topic> existing = topicDAO.getTopicByOrgAndName(conn, orgId.trim(), name.trim());
                if (existing.isPresent()) {
                    throw new EventNotificationServiceException(
                            EventNotificationServiceConstants.ERROR_CODE_RESOURCE_EXISTS,
                            EventNotificationServiceConstants.ERROR_TITLE_TOPIC_ALREADY_EXISTS,
                            EventNotificationServiceConstants.TOPIC_ALREADY_EXISTS_ERROR_MSG,
                            409);
                }

                boolean created = topicDAO.addTopic(conn, topic);
                if (!created) {
                    throw new EventNotificationServiceException(
                            EventNotificationServiceConstants.ERROR_CODE_INTERNAL_ERROR,
                            EventNotificationServiceConstants.ERROR_TITLE_INTERNAL_ERROR,
                            EventNotificationServiceConstants.FAILED_TO_CREATE_TOPIC_ERROR_MSG,
                            500);
                }
            } catch (EventNotificationDuplicateResourceException e) {
                throw new EventNotificationServiceException(
                        EventNotificationServiceConstants.ERROR_CODE_RESOURCE_EXISTS,
                        EventNotificationServiceConstants.ERROR_TITLE_TOPIC_ALREADY_EXISTS,
                        EventNotificationServiceConstants.TOPIC_ALREADY_EXISTS_ERROR_MSG,
                        409);
            }
            return new TopicDTO(topicId, topic.getName(), topic.getDescription(), TopicStatus.ACTIVE.getValue(),
                    Initiator.USER.getValue());
        });
    }

    @Override
    public TopicDTO ensureSystemTopic(String orgId, String name, String description) {
        return provisionSystemTopic(orgId, name, description).topic;
    }

    /**
     * Ensures the system topic exists in a single lookup/insert transaction and reports whether this call
     * created it.
     */
    private SystemTopicResult provisionSystemTopic(String orgId, String name, String description) {
        validateTopicCreationParameters(orgId, name);
        String normalizedOrgId = orgId.trim();
        String normalizedName = name.trim();

        try {
            return DatabaseUtils.executeInTransaction(conn -> {
                Optional<Topic> existing = topicDAO.getTopicByOrgAndName(conn, normalizedOrgId, normalizedName);
                if (existing.isPresent()) {
                    return new SystemTopicResult(mapExistingSystemTopic(existing.get(), normalizedName), false);
                }

                String topicId = UUID.randomUUID().toString();
                Topic topic = new Topic(topicId, normalizedOrgId, normalizedName,
                        description != null ? description.trim() : null,
                        TopicStatus.ACTIVE.getValue(), Initiator.SYSTEM.getValue());
                boolean created = topicDAO.addTopic(conn, topic);
                if (!created) {
                    throw new EventNotificationServiceException(
                            EventNotificationServiceConstants.ERROR_CODE_INTERNAL_ERROR,
                            EventNotificationServiceConstants.ERROR_TITLE_INTERNAL_ERROR,
                            EventNotificationServiceConstants.FAILED_TO_CREATE_TOPIC_ERROR_MSG,
                            500);
                }
                return new SystemTopicResult(new TopicDTO(topicId, topic.getName(), topic.getDescription(),
                        TopicStatus.ACTIVE.getValue(), Initiator.SYSTEM.getValue()), true);
            });
        } catch (EventNotificationDuplicateResourceException e) {
            return new SystemTopicResult(recoverConcurrentlyCreatedSystemTopic(normalizedOrgId, normalizedName),
                    false);
        }
    }

    private static final class SystemTopicResult {

        private final TopicDTO topic;
        private final boolean created;

        private SystemTopicResult(TopicDTO topic, boolean created) {
            this.topic = topic;
            this.created = created;
        }
    }

    @Override
    public void reconcileSystemTopicsAcrossTenants(Collection<String> orgIds) {
        List<String> lowerDefaultTopicNames = new ArrayList<>(DefaultTopic.values().length);
        for (DefaultTopic defaultTopic : DefaultTopic.values()) {
            lowerDefaultTopicNames.add(defaultTopic.getName().toLowerCase(Locale.ROOT));
        }

        Set<String> requestedOrgs = new LinkedHashSet<>();
        if (orgIds != null) {
            for (String orgId : orgIds) {
                if (orgId != null && !orgId.trim().isEmpty()) {
                    requestedOrgs.add(orgId.trim());
                }
            }
        }

        List<Topic> promotedTopics = new ArrayList<>();
        List<String> completeOrgs;
        try {
            completeOrgs = DatabaseUtils.executeInTransaction(conn -> {
                List<Topic> promoted = topicDAO.promoteUserTopicsToSystem(conn, lowerDefaultTopicNames);
                if (promoted != null) {
                    promotedTopics.addAll(promoted);
                }
                return topicDAO.getOrgsWithCompleteSystemTopics(conn, lowerDefaultTopicNames);
            });
        } catch (EventNotificationDaoException e) {
            LOG.error("Failed to promote and look up system topics across organizations.", e);
            throw new EventNotificationServiceException(
                    EventNotificationServiceConstants.ERROR_CODE_INTERNAL_ERROR,
                    EventNotificationServiceConstants.ERROR_TITLE_INTERNAL_ERROR,
                    EventNotificationServiceConstants.FAILED_TO_CREATE_TOPIC_ERROR_MSG,
                    500);
        }

        int promotedCount = promotedTopics.size();
        for (Topic promoted : promotedTopics) {
            LOG.info("Promoted user-created default topic '" + promoted.getName()
                    + "' (ID: " + promoted.getTopicId()
                    + ") for organization '" + LogSanitizer.sanitize(promoted.getOrgId())
                    + "' to system topic.");
        }

        int newlyProvisionedCount = 0;
        int incompleteOrgsCount = 0;
        int failedCount = 0;
        if (completeOrgs != null) {
            requestedOrgs.removeAll(completeOrgs);
        }
        for (String orgId : requestedOrgs) {
            incompleteOrgsCount++;
            String sanitizedOrgId = LogSanitizer.sanitize(orgId);
            for (DefaultTopic defaultTopic : DefaultTopic.values()) {
                try {
                    SystemTopicResult result = provisionSystemTopic(orgId, defaultTopic.getName(),
                            defaultTopic.getDescription());
                    if (result.created) {
                        newlyProvisionedCount++;
                        LOG.info("Provisioned missing system topic '" + defaultTopic.getName()
                                + "' (ID: " + result.topic.getTopicId()
                                + ") for organization '" + sanitizedOrgId + "'.");
                    }
                } catch (EventNotificationServiceException | EventNotificationDaoException e) {
                    failedCount++;
                    LOG.error("Failed to verify and provision system topic '" + defaultTopic.getName()
                            + "' for organization: " + sanitizedOrgId, e);
                }
            }
        }

        if (failedCount > 0) {
            LOG.error("System topic startup verification completed with errors: " + promotedCount
                    + " topic(s) promoted to system, " + newlyProvisionedCount
                    + " missing topic(s) provisioned, " + failedCount
                    + " topic(s) failed across " + incompleteOrgsCount + " organization(s).");
        } else if (promotedCount == 0 && newlyProvisionedCount == 0) {
            LOG.debug("System topic startup verification completed: "
                    + "all existing organizations have up-to-date system topics.");
        } else {
            LOG.info("System topic startup verification and provisioning completed: " + promotedCount
                    + " topic(s) promoted to system, " + newlyProvisionedCount
                    + " missing topic(s) provisioned across " + incompleteOrgsCount + " organization(s).");
        }
    }

    private TopicDTO recoverConcurrentlyCreatedSystemTopic(String orgId, String topicName) {

        return DatabaseUtils.executeInTransaction(conn -> {
            Optional<Topic> concurrentlyCreated = topicDAO.getTopicByOrgAndName(conn, orgId, topicName);
            if (concurrentlyCreated.isPresent()) {
                return mapExistingSystemTopic(concurrentlyCreated.get(), topicName);
            }
            throw new EventNotificationServiceException(
                    EventNotificationServiceConstants.ERROR_CODE_RESOURCE_EXISTS,
                    EventNotificationServiceConstants.ERROR_TITLE_TOPIC_ALREADY_EXISTS,
                    EventNotificationServiceConstants.TOPIC_ALREADY_EXISTS_ERROR_MSG,
                    409);
        });
    }

    private void validateTopicCreationParameters(String orgId, String name) {
        if (orgId == null || orgId.trim().isEmpty() || name == null || name.trim().isEmpty()) {
            throw new EventNotificationServiceException(
                    EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.ORG_ID_OR_TOPIC_NAME_MISSING_ERROR_MSG,
                    400);
        }
    }

    private TopicDTO mapExistingSystemTopic(Topic topic, String topicName) {
        boolean active = TopicStatus.ACTIVE.getValue().equalsIgnoreCase(topic.getStatus());
        boolean systemInitiated = Initiator.SYSTEM.getValue().equalsIgnoreCase(topic.getInitiatedBy());
        if (!active || !systemInitiated) {
            throw new EventNotificationServiceException(
                    EventNotificationServiceConstants.ERROR_CODE_RESOURCE_EXISTS,
                    EventNotificationServiceConstants.ERROR_TITLE_RESOURCE_EXISTS,
                    String.format(EventNotificationServiceConstants.SYSTEM_TOPIC_NAME_CONFLICT_ERROR_MSG, topicName),
                    409);
        }
        return new TopicDTO(topic.getTopicId(), topic.getName(), topic.getDescription(), topic.getStatus(),
                topic.getInitiatedBy());
    }

    @Override
    public PaginatedResult<TopicDTO> listTopics(String orgId, String status, String search, int limit, int offset,
            String sort) {
        if (orgId == null || orgId.trim().isEmpty()) {
            throw new EventNotificationServiceException(
                    EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.ORG_ID_MISSING_ERROR_MSG,
                    400);
        }
        int lim = EventNotificationParameterUtils.normalizeLimit(limit);
        int off = EventNotificationParameterUtils.normalizeOffset(offset);
        String parsedStatus = EventNotificationParameterUtils.normalizeStatusFilter(status);
        String normalizedStatus = TopicStatus.fromValueOrDefault(parsedStatus, null) == TopicStatus.DELETED
                ? TopicStatus.DELETED.getValue()
                : parsedStatus;

        return DatabaseUtils.executeInTransaction(conn -> {
            PaginatedDAOResult<Topic> daoResult = topicDAO.listTopics(
                    conn, orgId.trim(), normalizedStatus, search, lim, off, sort);
            List<TopicDTO> dtoList = new ArrayList<>();
            for (Topic t : daoResult.getItems()) {
                dtoList.add(
                        new TopicDTO(t.getTopicId(), t.getName(), t.getDescription(), t.getStatus(),
                                t.getInitiatedBy()));
            }
            return new PaginatedResult<>(dtoList, daoResult.getTotal());
        });
    }

    @Override
    public TopicDTO deleteTopic(String orgId, String topicIdStr) {
        if (orgId == null || orgId.trim().isEmpty()) {
            throw new EventNotificationServiceException(
                    EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.ORG_ID_MISSING_ERROR_MSG,
                    400);
        }
        if (topicIdStr == null || topicIdStr.trim().isEmpty()) {
            throw new EventNotificationServiceException(
                    EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.TOPIC_ID_MISSING_ERROR_MSG,
                    400);
        }

        String normalizedOrgId = orgId.trim();
        String normalizedTopicId = topicIdStr.trim();

        try {
            return DatabaseUtils.executeInTransaction(conn -> {
                Optional<Topic> topicOpt = topicDAO.getTopicById(conn, normalizedTopicId, normalizedOrgId);
                if (!topicOpt.isPresent() || !normalizedOrgId.equalsIgnoreCase(topicOpt.get().getOrgId())) {
                    throw new EventNotificationServiceException(
                            EventNotificationServiceConstants.ERROR_CODE_TOPIC_NOT_FOUND,
                            EventNotificationServiceConstants.ERROR_TITLE_TOPIC_NOT_FOUND,
                            String.format(EventNotificationServiceConstants.TOPIC_NOT_FOUND_ERROR_MSG,
                                    normalizedTopicId),
                            404);
                }

                Topic topic = topicOpt.get();
                if (Initiator.SYSTEM.getValue().equalsIgnoreCase(topic.getInitiatedBy())) {
                    throw new EventNotificationServiceException(
                            EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                            EventNotificationServiceConstants.ERROR_TITLE_OPERATION_FORBIDDEN,
                            String.format(EventNotificationServiceConstants.SYSTEM_TOPIC_DELETE_FORBIDDEN_ERROR_MSG,
                                    topic.getName()),
                            409);
                }

                if (TopicStatus.fromValueOrDefault(topic.getStatus(), null) == TopicStatus.DELETED) {
                    throw new EventNotificationServiceException(
                            EventNotificationServiceConstants.ERROR_CODE_TOPIC_NOT_FOUND,
                            EventNotificationServiceConstants.ERROR_TITLE_TOPIC_NOT_FOUND,
                            String.format(EventNotificationServiceConstants.TOPIC_ALREADY_DELETED_ERROR_MSG,
                                    normalizedTopicId),
                            404);
                }

                boolean updated;
                try {
                    updated = topicDAO.deleteTopicAtomic(conn, topic.getTopicId(), normalizedOrgId);
                } catch (EventNotificationInvalidStateException e) {
                    throw new EventNotificationServiceException(
                            EventNotificationServiceConstants.ERROR_CODE_RESOURCE_EXISTS,
                            EventNotificationServiceConstants.ERROR_TITLE_RESOURCE_EXISTS,
                            String.format(EventNotificationServiceConstants.TOPIC_HAS_ACTIVE_SUBSCRIPTIONS_ERROR_MSG,
                                    topic.getName()),
                            409);
                }
                if (!updated) {
                    throw new EventNotificationServiceException(
                            EventNotificationServiceConstants.ERROR_CODE_INTERNAL_ERROR,
                            EventNotificationServiceConstants.ERROR_TITLE_INTERNAL_ERROR,
                            EventNotificationServiceConstants.FAILED_TO_DELETE_TOPIC_ERROR_MSG,
                            500);
                }

                return new TopicDTO(topic.getTopicId(), topic.getName(), topic.getDescription(),
                        TopicStatus.DELETED.getValue(), topic.getInitiatedBy());
            });
        } catch (EventNotificationDaoException e) {
            LOG.error("Failed to delete topic [" + LogSanitizer.sanitize(normalizedTopicId)
                    + "] for organization [" + LogSanitizer.sanitize(normalizedOrgId) + "].", e);
            throw new EventNotificationServiceException(
                    EventNotificationServiceConstants.ERROR_CODE_INTERNAL_ERROR,
                    EventNotificationServiceConstants.ERROR_TITLE_INTERNAL_ERROR,
                    EventNotificationServiceConstants.FAILED_TO_DELETE_TOPIC_ERROR_MSG,
                    500);
        }
    }

    public Optional<TopicDTO> getTopic(String orgId, String topicIdStr) {
        if (orgId == null || orgId.trim().isEmpty() || topicIdStr == null || topicIdStr.trim().isEmpty()) {
            return Optional.empty();
        }
        return DatabaseUtils.executeInTransaction(conn -> {
            Optional<Topic> topicOpt = topicDAO.getTopicById(conn, topicIdStr.trim(), orgId.trim());
            if (topicOpt.isPresent() && orgId.trim().equalsIgnoreCase(topicOpt.get().getOrgId())) {
                Topic t = topicOpt.get();
                return Optional.of(
                        new TopicDTO(t.getTopicId(), t.getName(), t.getDescription(), t.getStatus(),
                                t.getInitiatedBy()));
            }
            return Optional.empty();
        });
    }
}
