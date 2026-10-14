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

import org.wso2.dpdp.accelerator.common.util.DatabaseUtils;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.Initiator;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.TopicStatus;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class TopicServiceImpl implements TopicService {

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
        validateTopicCreationParameters(orgId, name);
        String normalizedOrgId = orgId.trim();
        String normalizedName = name.trim();

        try {
            return DatabaseUtils.executeInTransaction(conn -> {
                Optional<Topic> existing = topicDAO.getTopicByOrgAndName(conn, normalizedOrgId, normalizedName);
                if (existing.isPresent()) {
                    return mapExistingSystemTopic(existing.get(), normalizedName);
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
                return new TopicDTO(topicId, topic.getName(), topic.getDescription(), TopicStatus.ACTIVE.getValue(),
                        Initiator.SYSTEM.getValue());
            });
        } catch (EventNotificationDuplicateResourceException e) {
            return recoverConcurrentlyCreatedSystemTopic(normalizedOrgId, normalizedName);
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
        String normalizedStatus = EventNotificationParameterUtils.normalizeStatusFilter(status);

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

        return DatabaseUtils.executeInTransaction(conn -> {
            Optional<Topic> topicOpt = topicDAO.getTopicById(conn, topicIdStr.trim(), orgId.trim());
            if (!topicOpt.isPresent() || !orgId.trim().equalsIgnoreCase(topicOpt.get().getOrgId())) {
                throw new EventNotificationServiceException(
                        EventNotificationServiceConstants.ERROR_CODE_TOPIC_NOT_FOUND,
                        EventNotificationServiceConstants.ERROR_TITLE_TOPIC_NOT_FOUND,
                        String.format(EventNotificationServiceConstants.TOPIC_NOT_FOUND_ERROR_MSG, topicIdStr.trim()),
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

            if (TopicStatus.DELETED.getValue().equalsIgnoreCase(topic.getStatus())
                    || "deregistered".equalsIgnoreCase(topic.getStatus())) {
                throw new EventNotificationServiceException(
                        EventNotificationServiceConstants.ERROR_CODE_TOPIC_NOT_FOUND,
                        EventNotificationServiceConstants.ERROR_TITLE_TOPIC_NOT_FOUND,
                        String.format(EventNotificationServiceConstants.TOPIC_ALREADY_DELETED_ERROR_MSG,
                                topicIdStr.trim()),
                        404);
            }

            boolean updated;
            try {
                updated = topicDAO.deleteTopicAtomic(conn, topic.getTopicId(), orgId.trim());
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
