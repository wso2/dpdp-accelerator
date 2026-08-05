package org.wso2.dpdp.accelerator.complaint.mgt.dao.model;

public class Complaint {

    private String complaintId;
    private String orgId;
    private String userId;
    private String referenceId;
    private String category;
    private String priority;
    private String status;
    private String description;
    private long createdTime;
    private long updatedTime;
    private long statutoryDueTime;

    public Complaint() {
    }

    public Complaint(String complaintId, String orgId, String userId, String referenceId, String category,
            String priority, String status, String description, long createdTime, long updatedTime,
            long statutoryDueTime) {
        this.complaintId = complaintId;
        this.orgId = orgId;
        this.userId = userId;
        this.referenceId = referenceId;
        this.category = category;
        this.priority = priority;
        this.status = status;
        this.description = description;
        this.createdTime = createdTime;
        this.updatedTime = updatedTime;
        this.statutoryDueTime = statutoryDueTime;
    }

    public String getComplaintId() {
        return complaintId;
    }

    public void setComplaintId(String complaintId) {
        this.complaintId = complaintId;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public long getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(long createdTime) {
        this.createdTime = createdTime;
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
