package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean;

import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.util.DateTimeUtil;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintAttachmentDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintDTO;

import java.util.ArrayList;
import java.util.List;

public class ComplaintRecordBean {

    private String id;
    private String referenceId;
    private String subjectCategory;
    private String priority;
    private String status;
    private String userId;
    private String description;
    private List<ComplaintAttachmentResponseBean> attachments;
    private String submittedAt;
    private String updatedAt;
    private String statutoryDueDate;

    public ComplaintRecordBean() {
    }

    public static ComplaintRecordBean from(ComplaintDTO dto, List<ComplaintAttachmentDTO> attachmentDTOs) {
        ComplaintRecordBean bean = new ComplaintRecordBean();
        bean.id = dto.getId();
        bean.referenceId = dto.getReferenceId();
        bean.subjectCategory = dto.getSubjectCategory();
        bean.priority = dto.getPriority();
        bean.status = dto.getStatus();
        bean.userId = dto.getUserId();
        bean.description = dto.getDescription();
        bean.submittedAt = DateTimeUtil.toIso(dto.getSubmittedTime());
        bean.updatedAt = DateTimeUtil.toIso(dto.getUpdatedTime());
        bean.statutoryDueDate = DateTimeUtil.toIso(dto.getStatutoryDueTime());

        List<ComplaintAttachmentResponseBean> attachmentBeans = new ArrayList<>();
        if (attachmentDTOs != null) {
            for (ComplaintAttachmentDTO a : attachmentDTOs) {
                attachmentBeans.add(new ComplaintAttachmentResponseBean(a.getAttachmentId(), a.getFileName(),
                        a.getContentType(), a.getSizeBytes()));
            }
        }
        bean.attachments = attachmentBeans;
        return bean;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getSubjectCategory() {
        return subjectCategory;
    }

    public void setSubjectCategory(String subjectCategory) {
        this.subjectCategory = subjectCategory;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<ComplaintAttachmentResponseBean> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<ComplaintAttachmentResponseBean> attachments) {
        this.attachments = attachments;
    }

    public String getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(String submittedAt) {
        this.submittedAt = submittedAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getStatutoryDueDate() {
        return statutoryDueDate;
    }

    public void setStatutoryDueDate(String statutoryDueDate) {
        this.statutoryDueDate = statutoryDueDate;
    }
}
