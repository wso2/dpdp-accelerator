package org.wso2.dpdp.accelerator.complaint.mgt.service.impl;

import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintAttachmentDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.impl.ComplaintAttachmentDAOImpl;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintAttachment;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintAttachmentService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintEventService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintAttachmentDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintTimelineEntryDTO;
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
    public List<ComplaintAttachmentDTO> uploadComplaintAttachments(String orgId, String complaintId,
            List<UploadedFile> files) {
        complaintService.requireComplaint(orgId, complaintId);
        validateFiles(files);

        List<ComplaintAttachmentDTO> result = new ArrayList<>();
        for (UploadedFile file : files) {
            result.add(store(orgId, complaintId, null, file));
        }
        return result;
    }

    @Override
    public List<ComplaintAttachmentDTO> uploadCommentAttachments(String orgId, String complaintId, String commentId,
            String actorUserId, List<UploadedFile> files) {
        if (actorUserId == null || actorUserId.trim().isEmpty()) {
            throw new ComplaintException("CO-4002", "Validation failed",
                    "Field 'actorUserId' is required and must not be blank.", 422);
        }

        // getTimelineEntry() already 404s (CO-4040) if the complaint or the comment doesn't exist.
        ComplaintTimelineEntryDTO entry = complaintEventService.getTimelineEntry(orgId, complaintId, commentId);

        if (entry.getActorUserId() == null || !entry.getActorUserId().equals(actorUserId.trim())) {
            throw new ComplaintException("CO-4030", "Forbidden",
                    "actorUserId '" + actorUserId + "' does not match the actorUserId that created this comment.",
                    403);
        }

        validateFiles(files);

        List<ComplaintAttachmentDTO> result = new ArrayList<>();
        for (UploadedFile file : files) {
            result.add(store(orgId, complaintId, commentId, file));
        }
        return result;
    }

    @Override
    public List<ComplaintAttachmentDTO> listAttachmentsForComplaint(String orgId, String complaintId) {
        List<ComplaintAttachment> attachments = attachmentDAO.listAttachmentsForComplaint(orgId, complaintId);
        return toMetadataDTOs(attachments);
    }

    @Override
    public List<ComplaintAttachmentDTO> listAttachmentsForEvent(String orgId, String complaintId, String eventId) {
        List<ComplaintAttachment> attachments = attachmentDAO.listAttachmentsForEvent(orgId, complaintId, eventId);
        return toMetadataDTOs(attachments);
    }

    @Override
    public ComplaintAttachmentDTO downloadAttachment(String orgId, String complaintId, String attachmentId,
            String requesterRole) {
        Optional<ComplaintAttachment> attachmentOpt =
                attachmentDAO.getAttachmentWithDataById(attachmentId, orgId, complaintId);
        if (attachmentOpt.isEmpty()) {
            throw new ComplaintException("CO-4040", "Attachment not found",
                    "No attachment exists with attachmentId '" + attachmentId + "' for this organization.", 404);
        }
        ComplaintAttachment attachment = attachmentOpt.get();

        // Non-disclosure of officer-internal attachments to Data Principals. requesterRole is optional -
        // supplied by the BFF via the "actor-role" header - and this check is skipped when it is absent,
        // since callers other than the BFF-fronted Data Principal Portal are trusted internal callers.
        if ("USER".equalsIgnoreCase(requesterRole) && attachment.getEventId() != null) {
            ComplaintTimelineEntryDTO entry =
                    complaintEventService.getTimelineEntry(orgId, complaintId, attachment.getEventId());
            if (!entry.isPublic()) {
                throw new ComplaintException("CO-4030", "Forbidden",
                        "Requesting user is not authorized to access an attachment bound to a timeline entry with "
                                + "isPublic=false.", 403);
            }
        }

        ComplaintAttachmentDTO dto = new ComplaintAttachmentDTO(attachment.getAttachmentId(),
                attachment.getFileName(), attachment.getContentType(), attachment.getSizeBytes());
        dto.setContent(attachment.getFileData());
        return dto;
    }

    private void validateFiles(List<UploadedFile> files) {
        if (files == null || files.isEmpty()) {
            throw new ComplaintException("CO-4002", "Validation failed", "At least one file is required.", 422);
        }
        long maxSize = AttachmentPolicy.getMaxSizeBytes();
        for (UploadedFile file : files) {
            if (file.getData() == null || file.getData().length == 0) {
                throw new ComplaintException("CO-4002", "Validation failed", "Uploaded file must not be empty.", 422);
            }
            if (!AttachmentPolicy.isAllowedContentType(file.getContentType())) {
                throw new ComplaintException("CO-4002", "Validation failed",
                        "File contentType '" + file.getContentType() + "' is not one of the supported types.", 422);
            }
            if (file.getData().length > maxSize) {
                throw new ComplaintException("CO-4002", "Validation failed",
                        "File '" + file.getFileName() + "' exceeds the maximum allowed size of " + maxSize
                                + " bytes.", 422);
            }
        }
    }

    private ComplaintAttachmentDTO store(String orgId, String complaintId, String eventId, UploadedFile file) {
        String attachmentId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        ComplaintAttachment attachment = new ComplaintAttachment(attachmentId, orgId, complaintId, eventId,
                file.getFileName(), file.getContentType(), file.getData(), now);

        boolean added = attachmentDAO.addAttachment(attachment);
        if (!added) {
            throw new ComplaintException("CO-5000", "Internal error", "Failed to store attachment.", 500);
        }
        return new ComplaintAttachmentDTO(attachmentId, file.getFileName(), file.getContentType(),
                file.getData().length);
    }

    private List<ComplaintAttachmentDTO> toMetadataDTOs(List<ComplaintAttachment> attachments) {
        List<ComplaintAttachmentDTO> result = new ArrayList<>();
        for (ComplaintAttachment a : attachments) {
            result.add(new ComplaintAttachmentDTO(a.getAttachmentId(), a.getFileName(), a.getContentType(),
                    a.getSizeBytes()));
        }
        return result;
    }
}
