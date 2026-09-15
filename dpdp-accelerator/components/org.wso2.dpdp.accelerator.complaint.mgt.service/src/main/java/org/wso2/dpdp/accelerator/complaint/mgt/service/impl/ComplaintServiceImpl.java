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
import org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintPriority;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintStatus;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.exception.DuplicateReferenceIdException;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintQueueStats;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintCreateResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintQueueStatsResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintErrorCode;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintException;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintServiceConstants;
import org.wso2.dpdp.accelerator.complaint.mgt.service.notification.EmailNotificationClient;
import org.wso2.dpdp.accelerator.complaint.mgt.service.notification.NotificationClient;
import org.wso2.dpdp.accelerator.complaint.mgt.service.util.ComplaintServiceUtil;

import java.sql.Connection;
import java.util.List;
import java.util.UUID;

import static org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintStatus.OPEN;

public class ComplaintServiceImpl implements ComplaintService {

    // ComplaintServiceUtil's count-then-format sequence is inherently racy under concurrent
    // submissions for the same org/year - see DuplicateReferenceIdException. A handful of attempts
    // is enough to ride out that race without masking a genuinely broken database as a slow request.
    private static final int MAX_REFERENCE_ID_ATTEMPTS = 3;

    private final ComplaintDAO complaintDAO;
    private final ComplaintEventDAO complaintEventDAO;
    private final NotificationClient notificationClient;

    public ComplaintServiceImpl(ComplaintDAO complaintDAO, ComplaintEventDAO complaintEventDAO) {
        this(complaintDAO, complaintEventDAO, new EmailNotificationClient());
    }

    public ComplaintServiceImpl(ComplaintDAO complaintDAO, ComplaintEventDAO complaintEventDAO,
            NotificationClient notificationClient) {
        this.complaintDAO = complaintDAO;
        this.complaintEventDAO = complaintEventDAO;
        this.notificationClient = notificationClient;
    }

    @Override
    public ComplaintCreateResponseDTO createComplaint(String orgId, String userId, String userName,
            String subjectCategory, String description) {
        return createComplaint(orgId, userId, userName, subjectCategory, description, null, null);
    }

    @Override
    public ComplaintCreateResponseDTO createComplaint(String orgId, String userId, String userName,
            String subjectCategory, String description, String actorUserId, String actorRole) {
        if (orgId == null || orgId.trim().isEmpty()) {
            throw new ComplaintException(ComplaintErrorCode.INVALID_REQUEST_BODY,
                    ComplaintServiceConstants.ORG_ID_HEADER_REQUIRED_ERROR);
        }
        if (userId == null || userId.trim().isEmpty()) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    ComplaintServiceConstants.USER_ID_REQUIRED_ERROR);
        }
        if (subjectCategory == null || subjectCategory.trim().isEmpty()) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    ComplaintServiceConstants.SUBJECT_CATEGORY_REQUIRED_ERROR);
        }
        if (!isValidCategory(subjectCategory)) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    String.format(ComplaintServiceConstants.INVALID_SUBJECT_CATEGORY_ERROR, subjectCategory));
        }
        if (description == null || description.trim().isEmpty()) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    ComplaintServiceConstants.DESCRIPTION_REQUIRED_ERROR);
        }
        if (description.length() > ComplaintServiceConstants.MAX_DESCRIPTION_LENGTH) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    ComplaintServiceConstants.DESCRIPTION_TOO_LONG_ERROR);
        }

        // actorUserId/actorRole are only ever passed by the officer-assisted intake path
        // (ComplaintEndpoint), and only ever the resolved, authenticated caller - never anything
        // client-supplied. A blank actorUserId means "no intake event to record" (the citizen
        // self-service path).
        boolean recordIntakeEvent = actorUserId != null && !actorUserId.trim().isEmpty();
        if (recordIntakeEvent && !ComplaintActorRole.COMPLAINT_OFFICER.name().equals(actorRole)
                && !ComplaintActorRole.SYSTEM.name().equals(actorRole)) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    ComplaintServiceConstants.CREATE_COMPLAINT_ACTOR_ROLE_INVALID_ERROR);
        }

        String complaintId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        String priority = ComplaintServiceUtil.derivePriority(subjectCategory.trim());
        long statutoryDueTime = now + ComplaintServiceUtil.getStatutoryDuePeriodMillis();
        String trimmedUserName = userName != null && !userName.trim().isEmpty() ? userName.trim() : null;

        // A fresh reference ID is minted on every attempt (including retries) - see
        // DuplicateReferenceIdException - since retrying with the same one would just collide again.
        // The count and the insert it feeds share one transaction (generateReferenceId takes this
        // same conn) - otherwise a concurrent insert between the two could be counted twice, or
        // not at all.
        DuplicateReferenceIdException lastCollision = null;
        for (int attempt = 1; attempt <= MAX_REFERENCE_ID_ATTEMPTS; attempt++) {
            try {
                Complaint complaint = DatabaseUtils.executeInTransaction(conn -> {
                    String referenceId = ComplaintServiceUtil.generateReferenceId(conn, complaintDAO, orgId, now);
                    Complaint c = new Complaint(complaintId, orgId, userId.trim(), trimmedUserName, referenceId,
                            subjectCategory.trim(), priority, OPEN.name(), description.trim(), now, now,
                            statutoryDueTime);
                    if (recordIntakeEvent) {
                        persistWithIntakeEvent(conn, c, actorUserId.trim(), actorRole, now);
                    } else {
                        persistComplaint(conn, c);
                    }
                    return c;
                });
                notificationClient.notifyComplaintCreated(complaint);
                return ComplaintCreateResponseDTO.from(complaint);
            } catch (DuplicateReferenceIdException e) {
                lastCollision = e;
            }
        }
        throw new ComplaintException(ComplaintErrorCode.INTERNAL_ERROR,
                ComplaintServiceConstants.CREATE_COMPLAINT_FAILED_ERROR, lastCollision);
    }

    /** The citizen self-service path: just the complaint row, no intake event. */
    private void persistComplaint(Connection conn, Complaint complaint) {
        if (!complaintDAO.addComplaint(conn, complaint)) {
            throw new ComplaintException(ComplaintErrorCode.INTERNAL_ERROR,
                    ComplaintServiceConstants.CREATE_COMPLAINT_FAILED_ERROR);
        }
    }

    /**
     * Inserts the complaint and its officer-intake audit event together - so a complaint can
     * never be created with no record of which officer lodged it, or vice versa.
     */
    private void persistWithIntakeEvent(Connection conn, Complaint complaint, String actorUserId, String actorRole,
            long now) {
        if (!complaintDAO.addComplaint(conn, complaint)) {
            throw new ComplaintException(ComplaintErrorCode.INTERNAL_ERROR,
                    ComplaintServiceConstants.CREATE_COMPLAINT_FAILED_ERROR);
        }
        ComplaintEvent event = new ComplaintEvent(UUID.randomUUID().toString(), complaint.getOrgId(),
                complaint.getComplaintId(), actorUserId, null, actorRole, true,
                ComplaintServiceConstants.OFFICER_INTAKE_EVENT_MESSAGE, null, OPEN.name(), now);
        if (!complaintEventDAO.addEvent(conn, event)) {
            throw new ComplaintException(ComplaintErrorCode.INTERNAL_ERROR,
                    ComplaintServiceConstants.CREATE_COMPLAINT_FAILED_ERROR);
        }
    }

    @Override
    public Complaint getComplaint(String orgId, String complaintId) {
        return DatabaseUtils.executeInTransaction(
                conn -> ComplaintServiceUtil.getComplaint(conn, complaintDAO, orgId, complaintId));
    }

    @Override
    public Complaint getOwnedComplaint(String orgId, String complaintId, String ownerUserId) {
        return DatabaseUtils.executeInTransaction(
                conn -> ComplaintServiceUtil.getOwnedComplaint(conn, complaintDAO, orgId, complaintId,
                        ownerUserId));
    }

    @Override
    public List<Complaint> listComplaints(String orgId, String status, String priority, String userId, int limit,
            int offset, String sort, int[] totalOut) {
        // A typo'd/unrecognized filter value must surface as a 400, not silently return an empty
        // page indistinguishable from "no matches" - see complaint-server-API.yaml.
        if (status != null && !status.trim().isEmpty() && !ComplaintStatus.isValid(status)) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    String.format(ComplaintServiceConstants.INVALID_STATUS_FILTER_ERROR, status));
        }
        if (priority != null && !priority.trim().isEmpty() && !ComplaintPriority.isValid(priority)) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    String.format(ComplaintServiceConstants.INVALID_PRIORITY_FILTER_ERROR, priority));
        }
        return DatabaseUtils.executeInTransaction(conn -> complaintDAO.listComplaints(conn, orgId, status, priority,
                userId, limit, offset, sort, totalOut));
    }

    @Override
    public ComplaintQueueStatsResponseDTO getQueueStats(String orgId) {
        ComplaintQueueStats stats = DatabaseUtils.executeInTransaction(
                conn -> complaintDAO.getQueueStats(conn, orgId, System.currentTimeMillis()));
        return ComplaintQueueStatsResponseDTO.from(stats);
    }

    private boolean isValidCategory(String category) {
        return ComplaintServiceUtil.isKnownCategory(category);
    }
}
