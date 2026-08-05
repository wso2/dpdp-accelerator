package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.handler;

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
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintAttachmentDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.impl.ComplaintAttachmentServiceImpl;
import org.wso2.dpdp.accelerator.complaint.mgt.service.impl.ComplaintEventServiceImpl;
import org.wso2.dpdp.accelerator.complaint.mgt.service.impl.ComplaintServiceImpl;

import java.util.ArrayList;
import java.util.List;

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
        ComplaintDTO dto = complaintService.createComplaint(orgId, userId, subjectCategory, description);
        return ComplaintCreateResponseBean.from(dto);
    }

    public ComplaintRecordBean getComplaint(String orgId, String complaintId) {
        ComplaintDTO dto = complaintService.getComplaint(orgId, complaintId);
        List<ComplaintAttachmentDTO> attachments =
                complaintAttachmentService.listAttachmentsForComplaint(orgId, complaintId);
        return ComplaintRecordBean.from(dto, attachments);
    }

    public ComplaintListResponseBean listComplaints(String orgId, String status, String priority, String userId,
            Integer limit, Integer offset, String sort) {
        int lim = limit != null && limit > 0 ? Math.min(limit, 100) : 10;
        int off = offset != null && offset >= 0 ? offset : 0;
        int[] totalOut = new int[]{0};

        List<ComplaintDTO> list = complaintService.listComplaints(orgId, status, priority, userId, lim, off, sort,
                totalOut);

        List<ComplaintRecordBean> beanList = new ArrayList<>();
        for (ComplaintDTO dto : list) {
            List<ComplaintAttachmentDTO> attachments =
                    complaintAttachmentService.listAttachmentsForComplaint(orgId, dto.getId());
            beanList.add(ComplaintRecordBean.from(dto, attachments));
        }

        PageMetadataBean metadata = new PageMetadataBean(totalOut[0], off, beanList.size(), lim);
        return new ComplaintListResponseBean(beanList, metadata);
    }

    public ComplaintStatusUpdateResponseBean updateStatus(String orgId, String complaintId,
            ComplaintStatusUpdateRequestBean request) {
        String actorUserId = request != null ? request.getActorUserId() : null;
        String actorRole = request != null ? request.getActorRole() : null;
        String toStatus = request != null ? request.getToStatus() : null;
        String note = request != null ? request.getNote() : null;

        ComplaintDTO dto = complaintEventService.updateStatus(orgId, complaintId, actorUserId, actorRole, toStatus,
                note);
        return ComplaintStatusUpdateResponseBean.from(dto);
    }
}
