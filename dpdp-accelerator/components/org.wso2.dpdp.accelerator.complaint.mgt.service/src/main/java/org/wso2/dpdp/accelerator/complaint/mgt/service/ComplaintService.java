package org.wso2.dpdp.accelerator.complaint.mgt.service;

import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintDTO;

import java.util.List;


public interface ComplaintService {

    /** Lean result for POST /complaints - no attachments field (there can't be any yet). */
    ComplaintDTO createComplaint(String orgId, String userId, String subjectCategory, String description);

    /** Core complaint fields for GET /complaints/{complaintId} (attachments are composed in by the handler). */
    ComplaintDTO getComplaint(String orgId, String complaintId);

    /**
     * Fetches core complaint fields, throwing a 404 ComplaintException if it doesn't exist for this org.
     * Used by other services (events, attachments) that need to confirm a complaint exists/belongs to the
     * org before acting on it, without duplicating that existence check in every DAO.
     */
    ComplaintDTO requireComplaint(String orgId, String complaintId);

    List<ComplaintDTO> listComplaints(String orgId, String status, String priority, String userId, int limit,
            int offset, String sort, int[] totalOut);
}
