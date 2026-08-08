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
                    "Field 'message' is required and must not be blank.");
        }
        if (actorUserId == null || actorUserId.trim().isEmpty()) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    "Field 'actorUserId' is required and must not be blank.");
        }
        // SYSTEM is deliberately excluded - only ever written by the server itself, never accepted from a caller.
        if (!ComplaintActorRole.USER.name().equals(actorRole)
                && !ComplaintActorRole.COMPLAINT_OFFICER.name().equals(actorRole)) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    "Field 'actorRole' must be one of USER, COMPLAINT_OFFICER.");
        }
        if (!isPublic && !ComplaintActorRole.COMPLAINT_OFFICER.name().equals(actorRole)) {
            throw new ComplaintException(ComplaintErrorCode.FORBIDDEN,
                    "Actor role '" + actorRole + "' cannot set isPublic to false on a timeline entry.");
        }

        boolean hasToStatus = toStatus != null && !toStatus.trim().isEmpty();
        String fromStatus = null;
        if (hasToStatus) {
            if (!ComplaintStatus.isValid(toStatus)) {
                throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                        "Field 'toStatus' must be one of the defined ComplaintStatus enum values; received '"
                                + toStatus + "'.");
            }
            fromStatus = complaint.getStatus();
            if (!StatusTransitionValidator.isValidTransition(fromStatus, toStatus)) {
                throw new ComplaintException(ComplaintErrorCode.INVALID_STATE_TRANSITION,
                        "Cannot transition complaint from status '" + fromStatus + "' to '" + toStatus + "'.");
            }
        }

        String eventId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        ComplaintEvent event = new ComplaintEvent(eventId, orgId, complaintId, actorUserId.trim(), actorRole,
                isPublic, message.trim(), fromStatus, hasToStatus ? toStatus : null, now);

        boolean added = complaintEventDAO.addEvent(event);
        if (!added) {
            throw new ComplaintException(ComplaintErrorCode.INTERNAL_ERROR, "Failed to add comment.");
        }

        if (hasToStatus) {
            boolean statusUpdated = complaintDAO.updateStatus(complaintId, orgId, toStatus, now);
            if (!statusUpdated) {
                throw new ComplaintException(ComplaintErrorCode.INTERNAL_ERROR, "Failed to update complaint status.");
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
                    "No timeline entry exists with id '" + eventId + "' under this complaint.");
        }
        return eventOpt.get();
    }

    @Override
    public Complaint updateStatus(String orgId, String complaintId, String actorUserId, String actorRole,
            String toStatus, String note) {
        Complaint complaint = complaintService.requireComplaint(orgId, complaintId);

        if (toStatus == null || toStatus.trim().isEmpty()) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED, "Field 'toStatus' is required.");
        }
        if (!ComplaintStatus.isValid(toStatus)) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    "Field 'toStatus' must be one of the defined ComplaintStatus enum values; received '"
                            + toStatus + "'.");
        }
        if (RESOLVED.name().equals(toStatus) && (note == null || note.trim().isEmpty())) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    "Field 'note' is required when transitioning to status 'RESOLVED'.");
        }

        String fromStatus = complaint.getStatus();
        if (!StatusTransitionValidator.isValidTransition(fromStatus, toStatus)) {
            throw new ComplaintException(ComplaintErrorCode.INVALID_STATE_TRANSITION,
                    "Cannot transition complaint from status '" + fromStatus + "' to '" + toStatus + "'.");
        }

        long now = System.currentTimeMillis();
        boolean statusUpdated = complaintDAO.updateStatus(complaintId, orgId, toStatus, now);
        if (!statusUpdated) {
            throw new ComplaintException(ComplaintErrorCode.INTERNAL_ERROR, "Failed to update complaint status.");
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
