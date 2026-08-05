package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.handler;

import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintCommentCreateResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintMessageRequestBean;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintEventService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintCommentDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.impl.ComplaintEventServiceImpl;

public class ComplaintCommentHandler {

    private final ComplaintEventService complaintEventService;

    public ComplaintCommentHandler() {
        this.complaintEventService = new ComplaintEventServiceImpl();
    }

    public ComplaintCommentHandler(ComplaintEventService complaintEventService) {
        this.complaintEventService = complaintEventService;
    }

    public ComplaintCommentCreateResponseBean addComment(String orgId, String complaintId,
            ComplaintMessageRequestBean request) {
        String actorUserId = request != null ? request.getActorUserId() : null;
        String actorRole = request != null ? request.getActorRole() : null;
        String message = request != null ? request.getMessage() : null;
        boolean isPublic = request != null && request.isPublic();
        String toStatus = request != null ? request.getToStatus() : null;

        ComplaintCommentDTO dto = complaintEventService.addComment(orgId, complaintId, actorUserId, actorRole,
                message, isPublic, toStatus);
        return ComplaintCommentCreateResponseBean.from(dto);
    }
}
