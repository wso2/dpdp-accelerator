package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean;

public class ComplaintAttachmentResponseBean {

    private String attachmentId;
    private String fileName;
    private String contentType;
    private long sizeBytes;

    public ComplaintAttachmentResponseBean() {
    }

    public ComplaintAttachmentResponseBean(String attachmentId, String fileName, String contentType,
            long sizeBytes) {
        this.attachmentId = attachmentId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
    }

    public String getAttachmentId() {
        return attachmentId;
    }

    public void setAttachmentId(String attachmentId) {
        this.attachmentId = attachmentId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }
}
