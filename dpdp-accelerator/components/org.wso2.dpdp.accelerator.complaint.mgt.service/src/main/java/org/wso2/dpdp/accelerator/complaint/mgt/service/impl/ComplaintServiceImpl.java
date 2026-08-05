package org.wso2.dpdp.accelerator.complaint.mgt.service.impl;

import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.impl.ComplaintDAOImpl;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintException;
import org.wso2.dpdp.accelerator.complaint.mgt.service.util.PriorityMapper;
import org.wso2.dpdp.accelerator.complaint.mgt.service.util.ReferenceIdGenerator;
import org.wso2.dpdp.accelerator.complaint.mgt.service.util.StatutoryDuePeriodPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.DAOConstants.STATUS_OPEN;

public class ComplaintServiceImpl implements ComplaintService {

    private final ComplaintDAO complaintDAO;

    public ComplaintServiceImpl() {
        this.complaintDAO = new ComplaintDAOImpl();
    }

    public ComplaintServiceImpl(ComplaintDAO complaintDAO) {
        this.complaintDAO = complaintDAO;
    }

    @Override
    public ComplaintDTO createComplaint(String orgId, String userId, String subjectCategory, String description) {
        if (orgId == null || orgId.trim().isEmpty()) {
            throw new ComplaintException("CO-4001", "Invalid request body", "Header 'org-id' is required.", 400);
        }
        if (userId == null || userId.trim().isEmpty()) {
            throw new ComplaintException("CO-4002", "Validation failed", "Field 'userId' is required and must not be blank.", 422);
        }
        if (subjectCategory == null || subjectCategory.trim().isEmpty()) {
            throw new ComplaintException("CO-4002", "Validation failed", "Field 'subjectCategory' is required.", 422);
        }
        if (!isValidCategory(subjectCategory)) {
            throw new ComplaintException("CO-4002", "Validation failed",
                    "Field 'subjectCategory' must be one of the defined ComplaintCategory enum values; received '"
                            + subjectCategory + "'.", 422);
        }
        if (description == null || description.trim().isEmpty()) {
            throw new ComplaintException("CO-4002", "Validation failed", "Field 'description' is required and must not be blank.", 422);
        }
        if (description.length() > 5000) {
            throw new ComplaintException("CO-4002", "Validation failed", "Field 'description' must not exceed 5000 characters.", 422);
        }

        String complaintId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        String priority = PriorityMapper.derivePriority(subjectCategory.trim());
        String referenceId = ReferenceIdGenerator.generate(complaintDAO, orgId, now);
        long statutoryDueTime = now + StatutoryDuePeriodPolicy.getDuePeriodMillis();

        Complaint complaint = new Complaint(complaintId, orgId, userId.trim(), referenceId, subjectCategory.trim(),
                priority, STATUS_OPEN, description.trim(), now, now, statutoryDueTime);

        boolean created = complaintDAO.addComplaint(complaint);
        if (!created) {
            throw new ComplaintException("CO-5000", "Internal error", "Failed to create complaint.", 500);
        }

        return toDTO(complaint);
    }

    @Override
    public ComplaintDTO getComplaint(String orgId, String complaintId) {
        return requireComplaint(orgId, complaintId);
    }

    @Override
    public ComplaintDTO requireComplaint(String orgId, String complaintId) {
        if (complaintId == null || complaintId.trim().isEmpty() || orgId == null || orgId.trim().isEmpty()) {
            throw new ComplaintException("CO-4040", "Complaint not found",
                    "No complaint exists with the given ID for this organization.", 404);
        }
        Optional<Complaint> complaintOpt = complaintDAO.getComplaintById(complaintId.trim(), orgId.trim());
        if (complaintOpt.isEmpty()) {
            throw new ComplaintException("CO-4040", "Complaint not found",
                    "No complaint exists with id '" + complaintId + "' for this organization.", 404);
        }
        return toDTO(complaintOpt.get());
    }

    @Override
    public List<ComplaintDTO> listComplaints(String orgId, String status, String priority, String userId, int limit,
            int offset, String sort, int[] totalOut) {
        List<Complaint> complaints = complaintDAO.listComplaints(orgId, status, priority, userId, limit, offset,
                sort, totalOut);
        List<ComplaintDTO> dtoList = new ArrayList<>();
        for (Complaint c : complaints) {
            dtoList.add(toDTO(c));
        }
        return dtoList;
    }

    private boolean isValidCategory(String category) {
        return PriorityMapper.isKnownCategory(category);
    }

    private ComplaintDTO toDTO(Complaint c) {
        return new ComplaintDTO(c.getComplaintId(), c.getReferenceId(), c.getCategory(), c.getPriority(),
                c.getStatus(), c.getUserId(), c.getDescription(), c.getCreatedTime(), c.getUpdatedTime(),
                c.getStatutoryDueTime());
    }
}
