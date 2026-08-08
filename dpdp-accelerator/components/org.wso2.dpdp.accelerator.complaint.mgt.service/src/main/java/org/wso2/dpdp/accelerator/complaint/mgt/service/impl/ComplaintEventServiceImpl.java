/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.dpdp.accelerator.complaint.mgt.service.impl;

import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintEventDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintActorRole;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintStatus;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.impl.ComplaintDAOImpl;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.impl.ComplaintEventDAOImpl;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintEventService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintErrorCode;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintException;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintServiceConstants;
import org.wso2.dpdp.accelerator.complaint.mgt.service.util.StatusTransitionValidator;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintStatus.RESOLVED;

public class ComplaintEventServiceImpl implements ComplaintEventService {

    private final ComplaintEventDAO complaintEventDAO;
    private final ComplaintDAO complaintDAO;
    private final ComplaintService complaintService;

    public ComplaintEventServiceImpl() {
        this.complaintEventDAO = new ComplaintEventDAOImpl();
        this.complaintDAO = new ComplaintDAOImpl();
        this.complaintService = new ComplaintServiceImpl(this.complaintDAO);
    }

    public ComplaintEventServiceImpl(ComplaintEventDAO complaintEventDAO, ComplaintDAO complaintDAO,
            ComplaintService complaintService) {
        this.complaintEventDAO = complaintEventDAO;
        this.complaintDAO = complaintDAO;
        this.complaintService = complaintService;
    }

    @Override
    public List<ComplaintEvent> getTimeline(String orgId, String complaintId, Long since,
            Boolean isPublic, String order, int limit, int offset, int[] totalOut) {
        complaintService.requireComplaint(orgId, complaintId);
        return complaintEventDAO.listEvents(orgId, complaintId, since, isPublic, order, limit, offset, totalOut);
    }

    @Override
    public ComplaintEvent addComment(String orgId, String complaintId, String actorUserId, String actorRole,
            String message, boolean isPublic, String toStatus) {
        Complaint complaint = complaintService.requireComplaint(orgId, complaintId);

        if (message == null || message.trim().isEmpty()) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    ComplaintServiceConstants.MESSAGE_REQUIRED_ERROR);
        }
        if (actorUserId == null || actorUserId.trim().isEmpty()) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    ComplaintServiceConstants.ACTOR_USER_ID_REQUIRED_ERROR);
        }
        // SYSTEM is deliberately excluded - only ever written by the server itself, never accepted from a caller.
        if (!ComplaintActorRole.USER.name().equals(actorRole)
                && !ComplaintActorRole.COMPLAINT_OFFICER.name().equals(actorRole)) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    ComplaintServiceConstants.ACTOR_ROLE_INVALID_ERROR);
        }
        if (!isPublic && !ComplaintActorRole.COMPLAINT_OFFICER.name().equals(actorRole)) {
            throw new ComplaintException(ComplaintErrorCode.FORBIDDEN,
                    String.format(ComplaintServiceConstants.INTERNAL_NOTE_FORBIDDEN_ERROR, actorRole));
        }

        boolean hasToStatus = toStatus != null && !toStatus.trim().isEmpty();
        String fromStatus = null;
        if (hasToStatus) {
            if (!ComplaintStatus.isValid(toStatus)) {
                throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                        String.format(ComplaintServiceConstants.INVALID_STATUS_VALUE_ERROR, toStatus));
            }
            fromStatus = complaint.getStatus();
            if (!StatusTransitionValidator.isValidTransition(fromStatus, toStatus)) {
                throw new ComplaintException(ComplaintErrorCode.INVALID_STATE_TRANSITION,
                        String.format(ComplaintServiceConstants.INVALID_STATUS_TRANSITION_ERROR, fromStatus,
                                toStatus));
            }
        }

        String eventId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        ComplaintEvent event = new ComplaintEvent(eventId, orgId, complaintId, actorUserId.trim(), actorRole,
                isPublic, message.trim(), fromStatus, hasToStatus ? toStatus : null, now);

        boolean added = complaintEventDAO.addEvent(event);
        if (!added) {
            throw new ComplaintException(ComplaintErrorCode.INTERNAL_ERROR,
                    ComplaintServiceConstants.ADD_COMMENT_FAILED_ERROR);
        }

        if (hasToStatus) {
            boolean statusUpdated = complaintDAO.updateStatus(complaintId, orgId, toStatus, now);
            if (!statusUpdated) {
                throw new ComplaintException(ComplaintErrorCode.INTERNAL_ERROR,
                        ComplaintServiceConstants.STATUS_UPDATE_FAILED_ERROR);
            }
        }

        return event;
    }

    @Override
    public ComplaintEvent getTimelineEntry(String orgId, String complaintId, String eventId) {
        complaintService.requireComplaint(orgId, complaintId);
        Optional<ComplaintEvent> eventOpt = complaintEventDAO.getEventById(eventId, orgId, complaintId);
        if (eventOpt.isEmpty()) {
            throw new ComplaintException(ComplaintErrorCode.COMMENT_NOT_FOUND,
                    String.format(ComplaintServiceConstants.TIMELINE_ENTRY_NOT_FOUND_ERROR, eventId));
        }
        return eventOpt.get();
    }

    @Override
    public Complaint updateStatus(String orgId, String complaintId, String actorUserId, String actorRole,
            String toStatus, String note) {
        Complaint complaint = complaintService.requireComplaint(orgId, complaintId);

        if (toStatus == null || toStatus.trim().isEmpty()) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    ComplaintServiceConstants.TO_STATUS_REQUIRED_ERROR);
        }
        if (!ComplaintStatus.isValid(toStatus)) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    String.format(ComplaintServiceConstants.INVALID_STATUS_VALUE_ERROR, toStatus));
        }
        if (RESOLVED.name().equals(toStatus) && (note == null || note.trim().isEmpty())) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    ComplaintServiceConstants.NOTE_REQUIRED_FOR_RESOLVED_ERROR);
        }

        String fromStatus = complaint.getStatus();
        if (!StatusTransitionValidator.isValidTransition(fromStatus, toStatus)) {
            throw new ComplaintException(ComplaintErrorCode.INVALID_STATE_TRANSITION,
                    String.format(ComplaintServiceConstants.INVALID_STATUS_TRANSITION_ERROR, fromStatus, toStatus));
        }

        long now = System.currentTimeMillis();
        boolean statusUpdated = complaintDAO.updateStatus(complaintId, orgId, toStatus, now);
        if (!statusUpdated) {
            throw new ComplaintException(ComplaintErrorCode.INTERNAL_ERROR,
                    ComplaintServiceConstants.STATUS_UPDATE_FAILED_ERROR);
        }

        String eventId = UUID.randomUUID().toString();
        ComplaintEvent event = new ComplaintEvent(eventId, orgId, complaintId, actorUserId, actorRole, true, note,
                fromStatus, toStatus, now);
        complaintEventDAO.addEvent(event);

        complaint.setStatus(toStatus);
        complaint.setUpdatedTime(now);
        return complaint;
    }
}
