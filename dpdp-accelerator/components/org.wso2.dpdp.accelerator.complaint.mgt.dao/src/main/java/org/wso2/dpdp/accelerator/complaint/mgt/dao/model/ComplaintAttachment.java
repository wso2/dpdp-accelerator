/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.dpdp.accelerator.complaint.mgt.dao.model;

public class ComplaintAttachment {

    private String attachmentId;
    private String orgId;
    private String complaintId;
    private String complaintEventId; // null = attached directly to the complaint, not to a timeline entry
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

    public ComplaintAttachment(String attachmentId, String orgId, String complaintId, String complaintEventId,
            String fileName, String contentType, byte[] fileData, long createdTime) {
        this.attachmentId = attachmentId;
        this.orgId = orgId;
        this.complaintId = complaintId;
        this.complaintEventId = complaintEventId;
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

    public String getComplaintEventId() {
        return complaintEventId;
    }

    public void setComplaintEventId(String complaintEventId) {
        this.complaintEventId = complaintEventId;
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
