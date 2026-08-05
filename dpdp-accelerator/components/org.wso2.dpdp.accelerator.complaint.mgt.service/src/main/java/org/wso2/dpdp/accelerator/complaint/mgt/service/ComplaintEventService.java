package org.wso2.dpdp.accelerator.complaint.mgt.service;

import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintCommentDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintTimelineEntryDTO;

import java.util.List;

public interface ComplaintEventService {

    List<ComplaintTimelineEntryDTO> getTimeline(String orgId, String complaintId, Long since, Boolean isPublic,
            String order, int limit, int offset, int[] totalOut);

    /**
     * Adds a COMMENT (isPublic=true) or an officer-internal note (isPublic=false) to the complaint's
     * timeline. Only a COMPLAINT_OFFICER actor may set isPublic=false. If toStatus is non-null, the
     * complaint is transitioned to that status in the same call, subject to the same state-machine
     * rules as updateStatus (an invalid transition throws CO-4090).
     */
    ComplaintCommentDTO addComment(String orgId, String complaintId, String actorUserId, String actorRole,
            String message, boolean isPublic, String toStatus);

    /** Returns the underlying timeline entry, used by the comment-attachment endpoint to verify ownership. */
    ComplaintTimelineEntryDTO getTimelineEntry(String orgId, String complaintId, String eventId);

    ComplaintDTO updateStatus(String orgId, String complaintId, String actorUserId, String actorRole,
            String toStatus, String note);
}
