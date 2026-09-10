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
import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintAttachmentDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintEventDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintActorRole;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintAttachment;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintAttachmentService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintAttachmentDownloadResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintAttachmentResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintErrorCode;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintException;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintServiceConstants;
import org.wso2.dpdp.accelerator.complaint.mgt.service.util.AttachmentPolicy;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ComplaintAttachmentServiceImpl implements ComplaintAttachmentService {

    private final ComplaintAttachmentDAO attachmentDAO;
    private final ComplaintEventDAO complaintEventDAO;
    private final ComplaintService complaintService;

    public ComplaintAttachmentServiceImpl(ComplaintAttachmentDAO attachmentDAO, ComplaintEventDAO complaintEventDAO,
            ComplaintService complaintService) {
        this.attachmentDAO = attachmentDAO;
        this.complaintEventDAO = complaintEventDAO;
        this.complaintService = complaintService;
    }

    @Override
    public List<ComplaintAttachmentResponseDTO> uploadComplaintAttachments(String orgId, String complaintId,
            List<UploadedFile> files, boolean isPublic, String actorUserId, String actorUserName,
            String actorRole) {
        validateFiles(files);
        validateActor(actorUserId, actorRole);
        long now = System.currentTimeMillis();

        // The existence check, the upload event, and every attachment it anchors must all share
        // one transaction - otherwise the complaint could change (or, if a delete path is ever
        // added, disappear) between the check and the write, or a failure partway through a
        // multi-file upload could leave some attachments stored against an event that was never
        // actually committed.
        List<ComplaintAttachment> stored = DatabaseUtils.executeInTransaction(conn -> {
            complaintService.requireComplaint(conn, orgId, complaintId);
            return performUpload(conn, orgId, complaintId, files, isPublic, actorUserId, actorUserName, actorRole,
                    now);
        });
        return toAttachmentDtos(stored);
    }

    @Override
    public List<ComplaintAttachmentResponseDTO> uploadOwnComplaintAttachments(String orgId, String complaintId,
            String ownerUserId, String ownerUserName, List<UploadedFile> files) {
        validateFiles(files);
        validateActor(ownerUserId, ComplaintActorRole.USER.name());
        long now = System.currentTimeMillis();

        // Same reasoning as uploadComplaintAttachments - the ownership check and the writes it
        // gates must share one transaction, not two sequential connections with the ownership
        // check's result no longer guaranteed true by the time the write runs.
        List<ComplaintAttachment> stored = DatabaseUtils.executeInTransaction(conn -> {
            complaintService.requireOwnedComplaint(conn, orgId, complaintId, ownerUserId);
            return performUpload(conn, orgId, complaintId, files, true, ownerUserId, ownerUserName,
                    ComplaintActorRole.USER.name(), now);
        });
        return toAttachmentDtos(stored);
    }

    private List<ComplaintAttachment> performUpload(Connection conn, String orgId, String complaintId,
            List<UploadedFile> files, boolean isPublic, String actorUserId, String actorUserName, String actorRole,
            long now) {
        String complaintEventId = recordUploadEvent(conn, orgId, complaintId, isPublic, actorUserId, actorUserName,
                actorRole, now);
        List<ComplaintAttachment> attachments = new ArrayList<>();
        for (UploadedFile file : files) {
            attachments.add(store(conn, orgId, complaintId, complaintEventId, file, isPublic, now));
        }
        return attachments;
    }

    private List<ComplaintAttachmentResponseDTO> toAttachmentDtos(List<ComplaintAttachment> attachments) {
        List<ComplaintAttachmentResponseDTO> result = new ArrayList<>();
        for (ComplaintAttachment attachment : attachments) {
            result.add(ComplaintAttachmentResponseDTO.from(attachment));
        }
        return result;
    }

    @Override
    public ComplaintAttachmentDownloadResponseDTO downloadOwnAttachment(String orgId, String complaintId,
            String ownerUserId, String attachmentId) {
        // The ownership check and the attachment fetch share one transaction - same reasoning as
        // uploadOwnComplaintAttachments.
        Optional<ComplaintAttachment> attachmentOpt = DatabaseUtils.executeInTransaction(conn -> {
            complaintService.requireOwnedComplaint(conn, orgId, complaintId, ownerUserId);
            return attachmentDAO.getAttachmentWithDataById(conn, attachmentId, orgId, complaintId);
        });
        return toDownloadResponse(attachmentOpt, attachmentId, true);
    }

    private void validateActor(String actorUserId, String actorRole) {
        if (actorUserId == null || actorUserId.trim().isEmpty()) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    ComplaintServiceConstants.ACTOR_USER_ID_REQUIRED_ERROR);
        }
        // SYSTEM is deliberately excluded - only ever written by the server itself, the same
        // restriction ComplaintEventServiceImpl#addComment applies to caller-supplied actor roles.
        if (!ComplaintActorRole.USER.name().equals(actorRole)
                && !ComplaintActorRole.COMPLAINT_OFFICER.name().equals(actorRole)) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    ComplaintServiceConstants.ACTOR_ROLE_INVALID_ERROR);
        }
    }

    private String recordUploadEvent(Connection conn, String orgId, String complaintId, boolean isPublic,
            String actorUserId, String actorUserName, String actorRole, long now) {
        String complaintEventId = UUID.randomUUID().toString();
        // No comment text - this event exists purely to anchor the uploaded attachments on the
        // timeline; the attachments themselves (via ComplaintAttachment#complaintEventId) are what
        // the UI renders under it.
        ComplaintEvent event = new ComplaintEvent(complaintEventId, orgId, complaintId, actorUserId, actorUserName,
                actorRole, isPublic, null, null, null, now);

        boolean added = complaintEventDAO.addEvent(conn, event);
        if (!added) {
            throw new ComplaintException(ComplaintErrorCode.INTERNAL_ERROR,
                    ComplaintServiceConstants.ATTACHMENT_EVENT_STORE_FAILED_ERROR);
        }
        return complaintEventId;
    }

    @Override
    public List<ComplaintAttachmentResponseDTO> listAttachmentsForComplaint(String orgId, String complaintId) {
        List<ComplaintAttachment> attachments = DatabaseUtils.executeInTransaction(
                conn -> attachmentDAO.listAttachmentsForComplaint(conn, orgId, complaintId));
        List<ComplaintAttachmentResponseDTO> beans = new ArrayList<>();
        for (ComplaintAttachment attachment : attachments) {
            beans.add(ComplaintAttachmentResponseDTO.from(attachment));
        }
        return beans;
    }

    @Override
    public ComplaintAttachmentDownloadResponseDTO downloadAttachment(String orgId, String complaintId,
            String attachmentId, boolean restrictToPublicOnly) {
        Optional<ComplaintAttachment> attachmentOpt = DatabaseUtils.executeInTransaction(
                conn -> attachmentDAO.getAttachmentWithDataById(conn, attachmentId, orgId, complaintId));
        return toDownloadResponse(attachmentOpt, attachmentId, restrictToPublicOnly);
    }

    private ComplaintAttachmentDownloadResponseDTO toDownloadResponse(Optional<ComplaintAttachment> attachmentOpt,
            String attachmentId, boolean restrictToPublicOnly) {
        if (attachmentOpt.isEmpty()) {
            throw new ComplaintException(ComplaintErrorCode.ATTACHMENT_NOT_FOUND,
                    String.format(ComplaintServiceConstants.ATTACHMENT_NOT_FOUND_ERROR, attachmentId));
        }
        ComplaintAttachment attachment = attachmentOpt.get();

        if (restrictToPublicOnly && !attachment.isPublic()) {
            throw new ComplaintException(ComplaintErrorCode.FORBIDDEN,
                    ComplaintServiceConstants.INTERNAL_ATTACHMENT_ACCESS_DENIED_ERROR);
        }

        return new ComplaintAttachmentDownloadResponseDTO(attachment.getAttachmentId(), attachment.getFileName(),
                attachment.getContentType(), attachment.getFileData());
    }

    private void validateFiles(List<UploadedFile> files) {
        if (files == null || files.isEmpty()) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    ComplaintServiceConstants.FILE_LIST_REQUIRED_ERROR);
        }
        int maxFiles = AttachmentPolicy.getMaxFilesPerUpload();
        if (files.size() > maxFiles) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    String.format(ComplaintServiceConstants.TOO_MANY_FILES_ERROR, maxFiles, files.size()));
        }
        long maxSize = AttachmentPolicy.getMaxSizeBytes();
        for (UploadedFile file : files) {
            if (file.getData() == null || file.getData().length == 0) {
                throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                        ComplaintServiceConstants.UPLOADED_FILE_EMPTY_ERROR);
            }
            if (!AttachmentPolicy.isAllowedContentType(file.getContentType())) {
                throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                        String.format(ComplaintServiceConstants.UNSUPPORTED_CONTENT_TYPE_ERROR,
                                file.getContentType()));
            }
            if (file.getData().length > maxSize) {
                throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                        String.format(ComplaintServiceConstants.FILE_SIZE_EXCEEDED_ERROR, file.getFileName(),
                                maxSize));
            }
        }
    }

    private ComplaintAttachment store(Connection conn, String orgId, String complaintId, String complaintEventId,
            UploadedFile file, boolean isPublic, long now) {
        String attachmentId = UUID.randomUUID().toString();
        ComplaintAttachment attachment = new ComplaintAttachment(attachmentId, orgId, complaintId,
                file.getFileName(), file.getContentType(), file.getData(), isPublic, now);
        attachment.setComplaintEventId(complaintEventId);

        boolean added = attachmentDAO.addAttachment(conn, attachment);
        if (!added) {
            throw new ComplaintException(ComplaintErrorCode.INTERNAL_ERROR,
                    ComplaintServiceConstants.ATTACHMENT_STORE_FAILED_ERROR);
        }
        return attachment;
    }
}
