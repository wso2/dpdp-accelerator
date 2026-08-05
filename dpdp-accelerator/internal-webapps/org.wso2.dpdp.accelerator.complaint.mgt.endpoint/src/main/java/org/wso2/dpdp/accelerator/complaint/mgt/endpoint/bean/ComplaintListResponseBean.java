package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean;

import java.util.List;

public class ComplaintListResponseBean {

    private List<ComplaintRecordBean> data;
    private PageMetadataBean metadata;

    public ComplaintListResponseBean() {
    }

    public ComplaintListResponseBean(List<ComplaintRecordBean> data, PageMetadataBean metadata) {
        this.data = data;
        this.metadata = metadata;
    }

    public List<ComplaintRecordBean> getData() {
        return data;
    }

    public void setData(List<ComplaintRecordBean> data) {
        this.data = data;
    }

    public PageMetadataBean getMetadata() {
        return metadata;
    }

    public void setMetadata(PageMetadataBean metadata) {
        this.metadata = metadata;
    }
}
