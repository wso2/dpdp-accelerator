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

import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.impl.ComplaintDAOImpl;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintErrorCode;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintException;
import org.wso2.dpdp.accelerator.complaint.mgt.service.util.PriorityMapper;
import org.wso2.dpdp.accelerator.complaint.mgt.service.util.ReferenceIdGenerator;
import org.wso2.dpdp.accelerator.complaint.mgt.service.util.StatutoryDuePeriodPolicy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintStatus.OPEN;

public class ComplaintServiceImpl implements ComplaintService {

    private final ComplaintDAO complaintDAO;

    public ComplaintServiceImpl() {
        this.complaintDAO = new ComplaintDAOImpl();
    }

    public ComplaintServiceImpl(ComplaintDAO complaintDAO) {
        this.complaintDAO = complaintDAO;
    }

    @Override
    public Complaint createComplaint(String orgId, String userId, String subjectCategory, String description) {
        if (orgId == null || orgId.trim().isEmpty()) {
            throw new ComplaintException(ComplaintErrorCode.INVALID_REQUEST_BODY, "Header 'org-id' is required.");
        }
        if (userId == null || userId.trim().isEmpty()) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    "Field 'userId' is required and must not be blank.");
        }
        if (subjectCategory == null || subjectCategory.trim().isEmpty()) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED, "Field 'subjectCategory' is required.");
        }
        if (!isValidCategory(subjectCategory)) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    "Field 'subjectCategory' must be one of the defined ComplaintCategory enum values; received '"
                            + subjectCategory + "'.");
        }
        if (description == null || description.trim().isEmpty()) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    "Field 'description' is required and must not be blank.");
        }
        if (description.length() > 5000) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    "Field 'description' must not exceed 5000 characters.");
        }

        String complaintId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        String priority = PriorityMapper.derivePriority(subjectCategory.trim());
        String referenceId = ReferenceIdGenerator.generate(complaintDAO, orgId, now);
        long statutoryDueTime = now + StatutoryDuePeriodPolicy.getDuePeriodMillis();

        Complaint complaint = new Complaint(complaintId, orgId, userId.trim(), referenceId, subjectCategory.trim(),
                priority, OPEN.name(), description.trim(), now, now, statutoryDueTime);

        boolean created = complaintDAO.addComplaint(complaint);
        if (!created) {
            throw new ComplaintException(ComplaintErrorCode.INTERNAL_ERROR, "Failed to create complaint.");
        }

        return complaint;
    }

    @Override
    public Complaint getComplaint(String orgId, String complaintId) {
        return requireComplaint(orgId, complaintId);
    }

    @Override
    public Complaint requireComplaint(String orgId, String complaintId) {
        if (complaintId == null || complaintId.trim().isEmpty() || orgId == null || orgId.trim().isEmpty()) {
            throw new ComplaintException(ComplaintErrorCode.COMPLAINT_NOT_FOUND,
                    "No complaint exists with the given ID for this organization.");
        }
        Optional<Complaint> complaintOpt = complaintDAO.getComplaintById(complaintId.trim(), orgId.trim());
        if (complaintOpt.isEmpty()) {
            throw new ComplaintException(ComplaintErrorCode.COMPLAINT_NOT_FOUND,
                    "No complaint exists with id '" + complaintId + "' for this organization.");
        }
        return complaintOpt.get();
    }

    @Override
    public List<Complaint> listComplaints(String orgId, String status, String priority, String userId, int limit,
            int offset, String sort, int[] totalOut) {
        return complaintDAO.listComplaints(orgId, status, priority, userId, limit, offset, sort, totalOut);
    }

    private boolean isValidCategory(String category) {
        return PriorityMapper.isKnownCategory(category);
    }
}
