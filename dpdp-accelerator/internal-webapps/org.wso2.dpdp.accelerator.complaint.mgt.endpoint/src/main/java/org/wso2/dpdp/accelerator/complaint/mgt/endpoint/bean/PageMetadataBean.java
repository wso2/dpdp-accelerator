package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean;

public class PageMetadataBean {

    private int total;
    private int offset;
    private int count;
    private int limit;

    public PageMetadataBean() {
    }

    public PageMetadataBean(int total, int offset, int count, int limit) {
        this.total = total;
        this.offset = offset;
        this.count = count;
        this.limit = limit;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }
}
