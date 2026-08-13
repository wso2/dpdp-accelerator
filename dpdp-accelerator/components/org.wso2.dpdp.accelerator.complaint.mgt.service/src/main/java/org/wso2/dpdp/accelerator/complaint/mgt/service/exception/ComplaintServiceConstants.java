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

package org.wso2.dpdp.accelerator.complaint.mgt.service.exception;

/**
 * The ComplaintException "description" text used at each throw site, named here instead of
 * written inline - mirrors the constants-class convention in financial-services-accelerator's
 * ConsentCoreServiceConstants. Entries with a placeholder are String.format templates; the rest
 * are used as-is.
 */
public final class ComplaintServiceConstants {

    private ComplaintServiceConstants() {
    }

    public static final String ORG_ID_HEADER_REQUIRED_ERROR = "Header 'org-id' is required.";
    public static final String USER_ID_REQUIRED_ERROR = "Field 'userId' is required and must not be blank.";
    public static final String SUBJECT_CATEGORY_REQUIRED_ERROR = "Field 'subjectCategory' is required.";
    public static final String INVALID_SUBJECT_CATEGORY_ERROR =
            "Field 'subjectCategory' must be one of the defined ComplaintCategory enum values; received '%s'.";
    public static final String DESCRIPTION_REQUIRED_ERROR =
            "Field 'description' is required and must not be blank.";
    public static final String DESCRIPTION_TOO_LONG_ERROR = "Field 'description' must not exceed 5000 characters.";
    public static final String CREATE_COMPLAINT_FAILED_ERROR = "Failed to create complaint.";
    public static final String COMPLAINT_NOT_FOUND_ERROR =
            "No complaint exists with the given ID for this organization.";
    public static final String COMPLAINT_NOT_FOUND_BY_ID_ERROR =
            "No complaint exists with id '%s' for this organization.";

    public static final String ACTOR_USER_ID_REQUIRED_ERROR =
            "Field 'actorUserId' is required and must not be blank.";
    public static final String ACTOR_USER_ID_MISMATCH_ERROR =
            "actorUserId '%s' does not match the actorUserId that created this comment.";
    public static final String ATTACHMENT_NOT_FOUND_ERROR =
            "No attachment exists with attachmentId '%s' for this organization.";
    public static final String INTERNAL_ATTACHMENT_ACCESS_DENIED_ERROR =
            "Requesting user is not authorized to access an attachment bound to a timeline entry with "
                    + "isPublic=false.";
    public static final String FILE_LIST_REQUIRED_ERROR = "At least one file is required.";
    public static final String UPLOADED_FILE_EMPTY_ERROR = "Uploaded file must not be empty.";
    public static final String UNSUPPORTED_CONTENT_TYPE_ERROR =
            "File contentType '%s' is not one of the supported types.";
    public static final String FILE_SIZE_EXCEEDED_ERROR = "File '%s' exceeds the maximum allowed size of %d bytes.";
    public static final String ATTACHMENT_STORE_FAILED_ERROR = "Failed to store attachment.";
    public static final String FILE_READ_FAILED_ERROR = "Could not read uploaded file content.";

    public static final String MESSAGE_REQUIRED_ERROR = "Field 'message' is required and must not be blank.";
    public static final String ACTOR_ROLE_INVALID_ERROR = "Field 'actorRole' must be one of USER, COMPLAINT_OFFICER.";
    public static final String INTERNAL_NOTE_FORBIDDEN_ERROR =
            "Actor role '%s' cannot set isPublic to false on a timeline entry.";
    public static final String INVALID_STATUS_VALUE_ERROR =
            "Field 'toStatus' must be one of the defined ComplaintStatus enum values; received '%s'.";
    public static final String INVALID_STATUS_TRANSITION_ERROR =
            "Cannot transition complaint from status '%s' to '%s'.";
    public static final String ADD_COMMENT_FAILED_ERROR = "Failed to add comment.";
    public static final String STATUS_UPDATE_FAILED_ERROR = "Failed to update complaint status.";
    public static final String TIMELINE_ENTRY_NOT_FOUND_ERROR =
            "No timeline entry exists with id '%s' under this complaint.";
    public static final String TO_STATUS_REQUIRED_ERROR = "Field 'toStatus' is required.";
    public static final String NOTE_REQUIRED_FOR_RESOLVED_ERROR =
            "Field 'note' is required when transitioning to status 'RESOLVED'.";
}
