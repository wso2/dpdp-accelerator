package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean;

import java.util.List;

public class TimelineListResponseBean {

    private List<ComplaintTimelineEntryResponseBean> data;
    private PageMetadataBean metadata;

    public TimelineListResponseBean() {
    }

    public TimelineListResponseBean(List<ComplaintTimelineEntryResponseBean> data, PageMetadataBean metadata) {
        this.data = data;
        this.metadata = metadata;
    }

    public List<ComplaintTimelineEntryResponseBean> getData() {
        return data;
    }

    public void setData(List<ComplaintTimelineEntryResponseBean> data) {
        this.data = data;
    }

    public PageMetadataBean getMetadata() {
        return metadata;
    }

    public void setMetadata(PageMetadataBean metadata) {
        this.metadata = metadata;
    }
}
