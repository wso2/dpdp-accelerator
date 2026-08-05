package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean;

import java.util.Base64;

public class ComplaintAttachmentDownloadResponseBean {

    private String attachmentId;
    private String fileName;
    private String contentType;
    private String content; // base64-encoded file bytes, per the API spec

    public ComplaintAttachmentDownloadResponseBean() {
    }

    public ComplaintAttachmentDownloadResponseBean(String attachmentId, String fileName, String contentType,
            byte[] rawContent) {
        this.attachmentId = attachmentId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.content = rawContent != null ? Base64.getEncoder().encodeToString(rawContent) : null;
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
