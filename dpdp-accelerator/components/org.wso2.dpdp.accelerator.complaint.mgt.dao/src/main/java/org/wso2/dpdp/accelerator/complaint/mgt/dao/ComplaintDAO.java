package org.wso2.dpdp.accelerator.complaint.mgt.dao;

import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;

import java.util.List;
import java.util.Optional;

public interface ComplaintDAO {

    boolean addComplaint(Complaint complaint);

    Optional<Complaint> getComplaintById(String complaintId, String orgId);

    /** Count of complaints for this org whose REFERENCE_ID already uses the given year prefix (e.g. "CMP-2026-%"). */
    int countByReferenceIdPrefix(String orgId, String referenceIdLikePattern);

    boolean updateStatus(String complaintId, String orgId, String newStatus, long updatedTime);

    List<Complaint> listComplaints(String orgId, String status, String priority, String userId, int limit,
            int offset, String sort, int[] totalOut);
}
