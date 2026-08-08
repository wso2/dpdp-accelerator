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
 * The (code, message, HTTP status) triples documented in complaint-server-API.yaml for each error
 * response - kept here once instead of repeating the same literals at every throw site. The
 * per-call dynamic detail still goes through ComplaintException's separate "description" argument.
 */
public enum ComplaintErrorCode {

    INVALID_REQUEST_BODY("CO-4001", "Invalid request body", 400),
    VALIDATION_FAILED("CO-4002", "Validation failed", 422),
    FORBIDDEN("CO-4030", "Forbidden", 403),
    COMPLAINT_NOT_FOUND("CO-4040", "Complaint not found", 404),
    ATTACHMENT_NOT_FOUND("CO-4040", "Attachment not found", 404),
    COMMENT_NOT_FOUND("CO-4040", "Comment not found", 404),
    INVALID_STATE_TRANSITION("CO-4090", "Invalid state transition", 409),
    INTERNAL_ERROR("CO-5000", "Internal error", 500);

    private final String code;
    private final String message;
    private final int httpStatus;

    ComplaintErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
