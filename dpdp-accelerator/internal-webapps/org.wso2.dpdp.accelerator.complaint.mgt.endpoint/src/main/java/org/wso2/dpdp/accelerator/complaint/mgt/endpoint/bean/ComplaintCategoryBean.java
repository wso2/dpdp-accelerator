package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean;

public class ComplaintCategoryBean {

    private String category;
    private String priority;

    public ComplaintCategoryBean() {
    }

    public ComplaintCategoryBean(String category, String priority) {
        this.category = category;
        this.priority = priority;
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
}
