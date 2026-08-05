package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean;

import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.util.DateTimeUtil;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintDTO;

public class ComplaintStatusUpdateResponseBean {

    // Note: the API spec lists "message" as required on this schema but never defines it under
    // properties. Included here as a short human-readable confirmation, consistent with the
    // "Status transition confirmed" wording used in the 200 description for this endpoint.
    private String message;
    private String toStatus;
    private String updatedAt;

    public ComplaintStatusUpdateResponseBean() {
    }

    public static ComplaintStatusUpdateResponseBean from(ComplaintDTO dto) {
        ComplaintStatusUpdateResponseBean bean = new ComplaintStatusUpdateResponseBean();
        bean.message = "Status transition confirmed";
        bean.toStatus = dto.getStatus();
        bean.updatedAt = DateTimeUtil.toIso(dto.getUpdatedTime());
        return bean;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getToStatus() {
        return toStatus;
    }

    public void setToStatus(String toStatus) {
        this.toStatus = toStatus;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
