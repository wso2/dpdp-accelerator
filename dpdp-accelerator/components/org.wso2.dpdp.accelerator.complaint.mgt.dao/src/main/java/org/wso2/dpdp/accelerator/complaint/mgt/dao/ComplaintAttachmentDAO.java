package org.wso2.dpdp.accelerator.complaint.mgt.dao;

import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintAttachment;

import java.util.List;
import java.util.Optional;

public interface ComplaintAttachmentDAO {

    boolean addAttachment(ComplaintAttachment attachment);

    /** Metadata only (no FILE_DATA) - used for list responses. */
    Optional<ComplaintAttachment> getAttachmentMetadataById(String attachmentId, String orgId, String complaintId);

    /** Full row including FILE_DATA - used for the download endpoint. */
    Optional<ComplaintAttachment> getAttachmentWithDataById(String attachmentId, String orgId, String complaintId);

    /** Attachments bound directly to the complaint (EVENT_ID IS NULL). */
    List<ComplaintAttachment> listAttachmentsForComplaint(String orgId, String complaintId);

    /** Attachments bound to a specific timeline entry (comment/note). */
    List<ComplaintAttachment> listAttachmentsForEvent(String orgId, String complaintId, String eventId);
}
