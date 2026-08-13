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

package org.wso2.dpdp.accelerator.complaint.mgt.dao;

import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintAttachment;

import java.util.List;
import java.util.Optional;

public interface ComplaintAttachmentDAO {

    /** Persists a new attachment row. Returns true if a row was inserted. */
    boolean addAttachment(ComplaintAttachment attachment);

    /**
     * Metadata only (no FILE_DATA) - used for list responses.
     *
     * <p>complaintId is taken in addition to attachmentId/orgId even though attachmentId is
     * already unique per orgId: the caller (endpoint layer) has both a {complaintId} and an
     * {attachmentId} path segment, and passing both here lets the DAO verify they're
     * consistent - an attachment fetched for the "wrong" complaintId returns empty rather than
     * silently ignoring the mismatch.
     */
    Optional<ComplaintAttachment> getAttachmentMetadataById(String attachmentId, String orgId, String complaintId);

    /** Full row including FILE_DATA - used for the download endpoint. Same complaintId scoping as above. */
    Optional<ComplaintAttachment> getAttachmentWithDataById(String attachmentId, String orgId, String complaintId);

    /** Attachments bound directly to the complaint (COMPLAINT_EVENT_ID IS NULL). */
    List<ComplaintAttachment> listAttachmentsForComplaint(String orgId, String complaintId);

    /** Attachments bound to a specific timeline entry (comment/note). */
    List<ComplaintAttachment> listAttachmentsForEvent(String orgId, String complaintId, String complaintEventId);
}
