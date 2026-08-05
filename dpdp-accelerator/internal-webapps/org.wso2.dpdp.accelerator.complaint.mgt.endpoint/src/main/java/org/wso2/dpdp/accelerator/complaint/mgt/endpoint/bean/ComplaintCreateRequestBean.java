package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean;

public class ComplaintCreateRequestBean {

    private String userId;
    private String subjectCategory;
    private String description;

    public ComplaintCreateRequestBean() {
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSubjectCategory() {
        return subjectCategory;
    }

    public void setSubjectCategory(String subjectCategory) {
        this.subjectCategory = subjectCategory;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
