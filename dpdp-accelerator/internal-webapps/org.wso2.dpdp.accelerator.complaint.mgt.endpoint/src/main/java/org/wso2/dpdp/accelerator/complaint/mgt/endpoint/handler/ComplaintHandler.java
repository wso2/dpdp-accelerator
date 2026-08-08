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

package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.handler;

import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintAttachment;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.CategoryListResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintCategoryBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintCreateRequestBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintCreateResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintListResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintRecordBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintStatusUpdateRequestBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintStatusUpdateResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.PageMetadataBean;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintAttachmentService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintEventService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.impl.ComplaintAttachmentServiceImpl;
import org.wso2.dpdp.accelerator.complaint.mgt.service.impl.ComplaintEventServiceImpl;
import org.wso2.dpdp.accelerator.complaint.mgt.service.impl.ComplaintServiceImpl;
import org.wso2.dpdp.accelerator.complaint.mgt.service.util.PriorityMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ComplaintHandler {

    private final ComplaintService complaintService;
    private final ComplaintEventService complaintEventService;
    private final ComplaintAttachmentService complaintAttachmentService;

    public ComplaintHandler() {
        this.complaintService = new ComplaintServiceImpl();
        this.complaintEventService = new ComplaintEventServiceImpl();
        this.complaintAttachmentService = new ComplaintAttachmentServiceImpl(complaintService, complaintEventService);
    }

    public ComplaintHandler(ComplaintService complaintService, ComplaintEventService complaintEventService,
            ComplaintAttachmentService complaintAttachmentService) {
        this.complaintService = complaintService;
        this.complaintEventService = complaintEventService;
        this.complaintAttachmentService = complaintAttachmentService;
    }

    public ComplaintCreateResponseBean createComplaint(String orgId, ComplaintCreateRequestBean request) {
        String userId = request != null ? request.getUserId() : null;
        String subjectCategory = request != null ? request.getSubjectCategory() : null;
        String description = request != null ? request.getDescription() : null;
        Complaint complaint = complaintService.createComplaint(orgId, userId, subjectCategory, description);
        return ComplaintCreateResponseBean.from(complaint);
    }

    public ComplaintRecordBean getComplaint(String orgId, String complaintId) {
        Complaint complaint = complaintService.getComplaint(orgId, complaintId);
        List<ComplaintAttachment> attachments =
                complaintAttachmentService.listAttachmentsForComplaint(orgId, complaintId);
        return ComplaintRecordBean.from(complaint, attachments);
    }

    public ComplaintListResponseBean listComplaints(String orgId, String status, String priority, String userId,
            Integer limit, Integer offset, String sort) {
        int lim = limit != null && limit > 0 ? Math.min(limit, 100) : 10;
        int off = offset != null && offset >= 0 ? offset : 0;
        int[] totalOut = new int[]{0};

        List<Complaint> list = complaintService.listComplaints(orgId, status, priority, userId, lim, off, sort,
                totalOut);

        List<ComplaintRecordBean> beanList = new ArrayList<>();
        for (Complaint complaint : list) {
            List<ComplaintAttachment> attachments =
                    complaintAttachmentService.listAttachmentsForComplaint(orgId, complaint.getComplaintId());
            beanList.add(ComplaintRecordBean.from(complaint, attachments));
        }

        PageMetadataBean metadata = new PageMetadataBean(totalOut[0], off, beanList.size(), lim);
        return new ComplaintListResponseBean(beanList, metadata);
    }

    public CategoryListResponseBean getCategories() {
        Map<String, String> categoryPriorities = new TreeMap<>(PriorityMapper.getCategoryPriorities());
        List<ComplaintCategoryBean> beanList = new ArrayList<>();
        for (Map.Entry<String, String> entry : categoryPriorities.entrySet()) {
            beanList.add(new ComplaintCategoryBean(entry.getKey(), entry.getValue()));
        }
        return new CategoryListResponseBean(beanList);
    }

    public ComplaintStatusUpdateResponseBean updateStatus(String orgId, String complaintId,
            ComplaintStatusUpdateRequestBean request) {
        String actorUserId = request != null ? request.getActorUserId() : null;
        String actorRole = request != null ? request.getActorRole() : null;
        String toStatus = request != null ? request.getToStatus() : null;
        String note = request != null ? request.getNote() : null;

        Complaint complaint = complaintEventService.updateStatus(orgId, complaintId, actorUserId, actorRole, toStatus,
                note);
        return ComplaintStatusUpdateResponseBean.from(complaint);
    }
}
