package org.wso2.dpdp.accelerator.complaint.mgt.service.dto;

public class ComplaintAttachmentDTO {

    private String attachmentId;
    private String fileName;
    private String contentType;
    private long sizeBytes;
    private byte[] content; // only populated for the download-by-id use case

    public ComplaintAttachmentDTO() {
    }

    public ComplaintAttachmentDTO(String attachmentId, String fileName, String contentType, long sizeBytes) {
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

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }
}
