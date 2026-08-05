package org.wso2.dpdp.accelerator.complaint.mgt.service;

import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintAttachmentDTO;

import java.util.List;

public interface ComplaintAttachmentService {

    /** Uploads and binds one or more files directly to the complaint (not to a timeline entry). */
    List<ComplaintAttachmentDTO> uploadComplaintAttachments(String orgId, String complaintId,
            List<UploadedFile> files);

    /**
     * Uploads and binds one or more files to a specific comment/timeline entry. actorUserId must
     * match the actorUserId that created that timeline entry, or a 403 is raised.
     */
    List<ComplaintAttachmentDTO> uploadCommentAttachments(String orgId, String complaintId, String commentId,
            String actorUserId, List<UploadedFile> files);

    List<ComplaintAttachmentDTO> listAttachmentsForComplaint(String orgId, String complaintId);

    List<ComplaintAttachmentDTO> listAttachmentsForEvent(String orgId, String complaintId, String eventId);

    /**
     * Downloads an attachment including its file content. requesterRole is optional (passed through
     * by the BFF via the "actor-role" header) - when it is "USER", the Data Principal is denied access
     * to attachments bound to an officer-internal (isPublic=false) timeline entry.
     */
    ComplaintAttachmentDTO downloadAttachment(String orgId, String complaintId, String attachmentId,
            String requesterRole);

    /** A single uploaded multipart file, decoupled from any particular HTTP framework's bean type. */
    class UploadedFile {
        private final String fileName;
        private final String contentType;
        private final byte[] data;

        public UploadedFile(String fileName, String contentType, byte[] data) {
            this.fileName = fileName;
            this.contentType = contentType;
            this.data = data;
        }

        public String getFileName() {
            return fileName;
        }

        public String getContentType() {
            return contentType;
        }

        public byte[] getData() {
            return data;
        }
    }
}
