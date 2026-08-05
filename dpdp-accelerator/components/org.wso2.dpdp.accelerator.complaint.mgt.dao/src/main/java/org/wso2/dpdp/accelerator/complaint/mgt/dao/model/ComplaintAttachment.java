package org.wso2.dpdp.accelerator.complaint.mgt.dao.model;

public class ComplaintAttachment {

    private String attachmentId;
    private String orgId;
    private String complaintId;
    private String eventId; // null = attached directly to the complaint, not to a timeline entry
    private String fileName;
    private String contentType;
    private byte[] fileData;
    private long createdTime;

    /**
     * Populated from the SQL-side LENGTH(FILE_DATA) projection when a metadata-only query is used
     * (fileData stays null in that case, so the actual blob is never pulled into memory just to report a size).
     */
    private Long sizeBytesOverride;

    public ComplaintAttachment() {
    }

    public ComplaintAttachment(String attachmentId, String orgId, String complaintId, String eventId,
            String fileName, String contentType, byte[] fileData, long createdTime) {
        this.attachmentId = attachmentId;
        this.orgId = orgId;
        this.complaintId = complaintId;
        this.eventId = eventId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.fileData = fileData;
        this.createdTime = createdTime;
    }

    /** Actual size, from the loaded blob if present, otherwise from the metadata-query override. */
    public long getSizeBytes() {
        if (fileData != null) {
            return fileData.length;
        }
        return sizeBytesOverride != null ? sizeBytesOverride : 0;
    }

    public void setSizeBytesOverride(long sizeBytesOverride) {
        this.sizeBytesOverride = sizeBytesOverride;
    }

    public String getAttachmentId() {
        return attachmentId;
    }

    public void setAttachmentId(String attachmentId) {
        this.attachmentId = attachmentId;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public String getComplaintId() {
        return complaintId;
    }

    public void setComplaintId(String complaintId) {
        this.complaintId = complaintId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
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

    public byte[] getFileData() {
        return fileData;
    }

    public void setFileData(byte[] fileData) {
        this.fileData = fileData;
    }

    public long getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(long createdTime) {
        this.createdTime = createdTime;
    }
}
