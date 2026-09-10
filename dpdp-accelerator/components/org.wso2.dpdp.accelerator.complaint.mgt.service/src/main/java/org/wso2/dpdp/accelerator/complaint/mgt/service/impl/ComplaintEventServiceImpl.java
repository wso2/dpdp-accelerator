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

import org.wso2.dpdp.accelerator.common.util.DatabaseUtils;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintEventDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintActorRole;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintStatus;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintEventService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintCommentCreateResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintStatusUpdateResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintErrorCode;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintException;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintServiceConstants;
import org.wso2.dpdp.accelerator.complaint.mgt.service.notification.NotificationClient;
import org.wso2.dpdp.accelerator.complaint.mgt.service.util.StatusTransitionValidator;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintStatus.RESOLVED;

public class ComplaintEventServiceImpl implements ComplaintEventService {

    private final ComplaintEventDAO complaintEventDAO;
    private final ComplaintDAO complaintDAO;
    private final ComplaintService complaintService;
    private final NotificationClient notificationClient;

    public ComplaintEventServiceImpl(ComplaintEventDAO complaintEventDAO, ComplaintDAO complaintDAO,
            ComplaintService complaintService, NotificationClient notificationClient) {
        this.complaintEventDAO = complaintEventDAO;
        this.complaintDAO = complaintDAO;
        this.complaintService = complaintService;
        this.notificationClient = notificationClient;
    }

    @Override
    public List<ComplaintEvent> getTimeline(String orgId, String complaintId, Long since, Long until,
            Boolean isPublic, String order, int limit, int offset, int[] totalOut) {
        // The existence check and the read share one transaction - see ComplaintService -
        // otherwise they could disagree about whether the complaint exists.
        return DatabaseUtils.executeInTransaction(conn -> {
            complaintService.requireComplaint(conn, orgId, complaintId);
            return complaintEventDAO.listEvents(conn, orgId, complaintId, since, until, isPublic, order, limit,
                    offset, totalOut);
        });
    }

    @Override
    public ComplaintCommentCreateResponseDTO addComment(String orgId, String complaintId, String actorUserId,
            String actorUserName, String actorRole, String message, boolean isPublic, String toStatus) {
        if (message == null || message.trim().isEmpty()) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    ComplaintServiceConstants.MESSAGE_REQUIRED_ERROR);
        }
        if (message.length() > ComplaintServiceConstants.MAX_MESSAGE_LENGTH) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    ComplaintServiceConstants.MESSAGE_TOO_LONG_ERROR);
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
        if (hasToStatus && !ComplaintStatus.isValid(toStatus)) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    String.format(ComplaintServiceConstants.INVALID_STATUS_VALUE_ERROR, toStatus));
        }

        String complaintEventId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        // The existence check, the comment write, and its optional status change all share one
        // transaction - so a status-changing comment can never land against a complaint whose
        // status never actually moved (or the reverse), and the existence check can never
        // disagree with the write that follows it.
        AddCommentResult result = DatabaseUtils.executeInTransaction(conn -> {
            Complaint c = complaintService.requireComplaint(conn, orgId, complaintId);

            String fromStatus = null;
            if (hasToStatus) {
                fromStatus = c.getStatus();
                if (!StatusTransitionValidator.isValidTransition(fromStatus, toStatus)) {
                    throw new ComplaintException(ComplaintErrorCode.INVALID_STATE_TRANSITION,
                            String.format(ComplaintServiceConstants.INVALID_STATUS_TRANSITION_ERROR, fromStatus,
                                    toStatus));
                }
            }

            ComplaintEvent event = new ComplaintEvent(complaintEventId, orgId, complaintId, actorUserId.trim(),
                    actorUserName, actorRole, isPublic, message.trim(), fromStatus, hasToStatus ? toStatus : null,
                    now);
            if (!complaintEventDAO.addEvent(conn, event)) {
                throw new ComplaintException(ComplaintErrorCode.INTERNAL_ERROR,
                        ComplaintServiceConstants.ADD_COMMENT_FAILED_ERROR);
            }
            if (hasToStatus && !complaintDAO.updateStatus(conn, complaintId, orgId, toStatus, now)) {
                throw new ComplaintException(ComplaintErrorCode.INTERNAL_ERROR,
                        ComplaintServiceConstants.STATUS_UPDATE_FAILED_ERROR);
            }
            if (hasToStatus) {
                // complaint was fetched before the DB status update above; without this, the
                // notification would carry the complaint's pre-transition status.
                c.setStatus(toStatus);
                c.setUpdatedTime(now);
            }
            return new AddCommentResult(c, event);
        });

        if (isPublic) {
            // An internal note (isPublic=false, officer-only per the check above) is never shown
            // to the citizen in the timeline - notifying them about it would leak its existence.
            notificationClient.notifyCommentAdded(result.complaint, result.event);
        }
        return ComplaintCommentCreateResponseDTO.from(result.event);
    }

    /** Carries both values a transactional {@code addComment} needs to return out of one lambda. */
    private static final class AddCommentResult {

        private final Complaint complaint;
        private final ComplaintEvent event;

        private AddCommentResult(Complaint complaint, ComplaintEvent event) {
            this.complaint = complaint;
            this.event = event;
        }
    }

    @Override
    public ComplaintEvent getTimelineEntry(String orgId, String complaintId, String complaintEventId) {
        Optional<ComplaintEvent> eventOpt = DatabaseUtils.executeInTransaction(conn -> {
            complaintService.requireComplaint(conn, orgId, complaintId);
            return complaintEventDAO.getEventById(conn, complaintEventId, orgId, complaintId);
        });
        if (eventOpt.isEmpty()) {
            throw new ComplaintException(ComplaintErrorCode.COMMENT_NOT_FOUND,
                    String.format(ComplaintServiceConstants.TIMELINE_ENTRY_NOT_FOUND_ERROR, complaintEventId));
        }
        return eventOpt.get();
    }

    @Override
    public ComplaintStatusUpdateResponseDTO updateStatus(String orgId, String complaintId, String actorUserId,
            String actorUserName, String actorRole, String toStatus, String note) {
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

        long now = System.currentTimeMillis();
        String complaintEventId = UUID.randomUUID().toString();

        // The existence check, the status update, and its audit event all share one transaction -
        // both writes are checked and made to fail the whole transaction (not just skip a write)
        // so a partial failure can never leave the status changed with no record of why, or vice
        // versa.
        Complaint complaint = DatabaseUtils.executeInTransaction(conn -> {
            Complaint c = complaintService.requireComplaint(conn, orgId, complaintId);
            String fromStatus = c.getStatus();
            if (!StatusTransitionValidator.isValidTransition(fromStatus, toStatus)) {
                throw new ComplaintException(ComplaintErrorCode.INVALID_STATE_TRANSITION,
                        String.format(ComplaintServiceConstants.INVALID_STATUS_TRANSITION_ERROR, fromStatus,
                                toStatus));
            }

            ComplaintEvent event = new ComplaintEvent(complaintEventId, orgId, complaintId, actorUserId,
                    actorUserName, actorRole, true, note, fromStatus, toStatus, now);
            if (!complaintDAO.updateStatus(conn, complaintId, orgId, toStatus, now)) {
                throw new ComplaintException(ComplaintErrorCode.INTERNAL_ERROR,
                        ComplaintServiceConstants.STATUS_UPDATE_FAILED_ERROR);
            }
            if (!complaintEventDAO.addEvent(conn, event)) {
                throw new ComplaintException(ComplaintErrorCode.INTERNAL_ERROR,
                        ComplaintServiceConstants.ADD_COMMENT_FAILED_ERROR);
            }
            c.setStatus(toStatus);
            c.setUpdatedTime(now);
            return c;
        });

        return ComplaintStatusUpdateResponseDTO.from(complaint);
    }
}
