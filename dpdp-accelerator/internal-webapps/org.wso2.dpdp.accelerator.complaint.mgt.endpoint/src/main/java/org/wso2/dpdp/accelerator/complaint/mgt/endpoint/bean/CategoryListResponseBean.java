package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean;

import java.util.List;

public class CategoryListResponseBean {

    private List<ComplaintCategoryBean> data;

    public CategoryListResponseBean() {
    }

    public CategoryListResponseBean(List<ComplaintCategoryBean> data) {
        this.data = data;
    }

    public List<ComplaintCategoryBean> getData() {
        return data;
    }

    public void setData(List<ComplaintCategoryBean> data) {
        this.data = data;
    }
}
