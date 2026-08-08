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

import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintAttachmentDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.impl.ComplaintAttachmentDAOImpl;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintAttachment;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintAttachmentService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintEventService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintErrorCode;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintException;
import org.wso2.dpdp.accelerator.complaint.mgt.service.util.AttachmentPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ComplaintAttachmentServiceImpl implements ComplaintAttachmentService {

    private final ComplaintAttachmentDAO attachmentDAO;
    private final ComplaintService complaintService;
    private final ComplaintEventService complaintEventService;

    public ComplaintAttachmentServiceImpl(ComplaintService complaintService,
            ComplaintEventService complaintEventService) {
        this.attachmentDAO = new ComplaintAttachmentDAOImpl();
        this.complaintService = complaintService;
        this.complaintEventService = complaintEventService;
    }

    public ComplaintAttachmentServiceImpl(ComplaintAttachmentDAO attachmentDAO, ComplaintService complaintService,
            ComplaintEventService complaintEventService) {
        this.attachmentDAO = attachmentDAO;
        this.complaintService = complaintService;
        this.complaintEventService = complaintEventService;
    }

    @Override
    public List<ComplaintAttachment> uploadComplaintAttachments(String orgId, String complaintId,
            List<UploadedFile> files) {
        complaintService.requireComplaint(orgId, complaintId);
        validateFiles(files);

        List<ComplaintAttachment> result = new ArrayList<>();
        for (UploadedFile file : files) {
            result.add(store(orgId, complaintId, null, file));
        }
        return result;
    }

    @Override
    public List<ComplaintAttachment> uploadCommentAttachments(String orgId, String complaintId, String commentId,
            String actorUserId, List<UploadedFile> files) {
        if (actorUserId == null || actorUserId.trim().isEmpty()) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    "Field 'actorUserId' is required and must not be blank.");
        }

        // getTimelineEntry() already 404s (CO-4040) if the complaint or the comment doesn't exist.
        ComplaintEvent entry = complaintEventService.getTimelineEntry(orgId, complaintId, commentId);

        if (entry.getActorUserId() == null || !entry.getActorUserId().equals(actorUserId.trim())) {
            throw new ComplaintException(ComplaintErrorCode.FORBIDDEN,
                    "actorUserId '" + actorUserId + "' does not match the actorUserId that created this comment.");
        }

        validateFiles(files);

        List<ComplaintAttachment> result = new ArrayList<>();
        for (UploadedFile file : files) {
            result.add(store(orgId, complaintId, commentId, file));
        }
        return result;
    }

    @Override
    public List<ComplaintAttachment> listAttachmentsForComplaint(String orgId, String complaintId) {
        return attachmentDAO.listAttachmentsForComplaint(orgId, complaintId);
    }

    @Override
    public List<ComplaintAttachment> listAttachmentsForEvent(String orgId, String complaintId, String eventId) {
        return attachmentDAO.listAttachmentsForEvent(orgId, complaintId, eventId);
    }

    @Override
    public ComplaintAttachment downloadAttachment(String orgId, String complaintId, String attachmentId,
            String requesterRole) {
        Optional<ComplaintAttachment> attachmentOpt =
                attachmentDAO.getAttachmentWithDataById(attachmentId, orgId, complaintId);
        if (attachmentOpt.isEmpty()) {
            throw new ComplaintException(ComplaintErrorCode.ATTACHMENT_NOT_FOUND,
                    "No attachment exists with attachmentId '" + attachmentId + "' for this organization.");
        }
        ComplaintAttachment attachment = attachmentOpt.get();

        // Non-disclosure of officer-internal attachments to Data Principals. requesterRole is optional -
        // supplied by the BFF via the "actor-role" header - and this check is skipped when it is absent,
        // since callers other than the BFF-fronted Data Principal Portal are trusted internal callers.
        if ("USER".equalsIgnoreCase(requesterRole) && attachment.getEventId() != null) {
            ComplaintEvent entry =
                    complaintEventService.getTimelineEntry(orgId, complaintId, attachment.getEventId());
            if (!entry.isPublic()) {
                throw new ComplaintException(ComplaintErrorCode.FORBIDDEN,
                        "Requesting user is not authorized to access an attachment bound to a timeline entry with "
                                + "isPublic=false.");
            }
        }

        return attachment;
    }

    private void validateFiles(List<UploadedFile> files) {
        if (files == null || files.isEmpty()) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED, "At least one file is required.");
        }
        long maxSize = AttachmentPolicy.getMaxSizeBytes();
        for (UploadedFile file : files) {
            if (file.getData() == null || file.getData().length == 0) {
                throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                        "Uploaded file must not be empty.");
            }
            if (!AttachmentPolicy.isAllowedContentType(file.getContentType())) {
                throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                        "File contentType '" + file.getContentType() + "' is not one of the supported types.");
            }
            if (file.getData().length > maxSize) {
                throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                        "File '" + file.getFileName() + "' exceeds the maximum allowed size of " + maxSize
                                + " bytes.");
            }
        }
    }

    private ComplaintAttachment store(String orgId, String complaintId, String eventId, UploadedFile file) {
        String attachmentId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        ComplaintAttachment attachment = new ComplaintAttachment(attachmentId, orgId, complaintId, eventId,
                file.getFileName(), file.getContentType(), file.getData(), now);

        boolean added = attachmentDAO.addAttachment(attachment);
        if (!added) {
            throw new ComplaintException(ComplaintErrorCode.INTERNAL_ERROR, "Failed to store attachment.");
        }
        return attachment;
    }
}
