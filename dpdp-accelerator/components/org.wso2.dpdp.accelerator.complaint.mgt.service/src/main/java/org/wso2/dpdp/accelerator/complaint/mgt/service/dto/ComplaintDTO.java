package org.wso2.dpdp.accelerator.complaint.mgt.service.dto;

public class ComplaintDTO {

    private String id;
    private String referenceId;
    private String subjectCategory;
    private String priority;
    private String status;
    private String userId;
    private String description;
    private long submittedTime;
    private long updatedTime;
    private long statutoryDueTime;

    public ComplaintDTO() {
    }

    public ComplaintDTO(String id, String referenceId, String subjectCategory, String priority, String status,
            String userId, String description, long submittedTime, long updatedTime, long statutoryDueTime) {
        this.id = id;
        this.referenceId = referenceId;
        this.subjectCategory = subjectCategory;
        this.priority = priority;
        this.status = status;
        this.userId = userId;
        this.description = description;
        this.submittedTime = submittedTime;
        this.updatedTime = updatedTime;
        this.statutoryDueTime = statutoryDueTime;
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

    public long getSubmittedTime() {
        return submittedTime;
    }

    public void setSubmittedTime(long submittedTime) {
        this.submittedTime = submittedTime;
    }

    public long getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(long updatedTime) {
        this.updatedTime = updatedTime;
    }

    public long getStatutoryDueTime() {
        return statutoryDueTime;
    }

    public void setStatutoryDueTime(long statutoryDueTime) {
        this.statutoryDueTime = statutoryDueTime;
    }
}
