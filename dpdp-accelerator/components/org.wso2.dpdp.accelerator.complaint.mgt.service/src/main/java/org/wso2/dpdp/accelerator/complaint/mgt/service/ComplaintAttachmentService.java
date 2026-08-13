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

package org.wso2.dpdp.accelerator.complaint.mgt.service;

import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintAttachment;

import java.util.List;

public interface ComplaintAttachmentService {

    /** Uploads and binds one or more files directly to the complaint (not to a timeline entry). */
    List<ComplaintAttachment> uploadComplaintAttachments(String orgId, String complaintId,
            List<UploadedFile> files);

    /**
     * Uploads and binds one or more files to a specific comment/timeline entry. actorUserId must
     * match the actorUserId that created that timeline entry, or a 403 is raised.
     */
    List<ComplaintAttachment> uploadCommentAttachments(String orgId, String complaintId, String commentId,
            String actorUserId, List<UploadedFile> files);

    /** Metadata (no file content) for attachments bound directly to the complaint, not to a timeline entry. */
    List<ComplaintAttachment> listAttachmentsForComplaint(String orgId, String complaintId);

    /** Metadata (no file content) for attachments bound to a specific timeline entry (comment/note). */
    List<ComplaintAttachment> listAttachmentsForEvent(String orgId, String complaintId, String complaintEventId);

    /**
     * Downloads an attachment including its file content. requesterRole is optional (passed through
     * by the BFF via the "actor-role" header) - when it is "USER", the Data Principal is denied access
     * to attachments bound to an officer-internal (isPublic=false) timeline entry.
     */
    ComplaintAttachment downloadAttachment(String orgId, String complaintId, String attachmentId,
            String requesterRole);

    /** A single uploaded multipart file, decoupled from any particular HTTP framework's bean type. */
    class UploadedFile {
        private final String fileName;
        private final String contentType;
        private final byte[] data;

        public UploadedFile(String fileName, String contentType, byte[] data) {
            this.fileName = fileName;
            this.contentType = contentType;
            this.data = data;
        }

        public String getFileName() {
            return fileName;
        }

        public String getContentType() {
            return contentType;
        }

        public byte[] getData() {
            return data;
        }
    }
}
