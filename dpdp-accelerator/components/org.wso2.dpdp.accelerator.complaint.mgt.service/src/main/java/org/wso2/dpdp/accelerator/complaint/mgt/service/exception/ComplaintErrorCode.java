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
 * The (code, HTTP status) pairs documented in complaint-server-API.yaml for each error response -
 * kept here once instead of repeating the same literals at every throw site. No message text
 * lives on this enum - see ComplaintServiceConstants for that, mirroring the
 * ConsentMgtErrorCodes/ConsentCoreServiceConstants split in financial-services-accelerator's
 * ConsentCoreServiceImpl.
 */
public enum ComplaintErrorCode {

    INVALID_REQUEST_BODY("CO-4001", 400),
    VALIDATION_FAILED("CO-4002", 422),
    FORBIDDEN("CO-4030", 403),
    COMPLAINT_NOT_FOUND("CO-4040", 404),
    ATTACHMENT_NOT_FOUND("CO-4040", 404),
    COMMENT_NOT_FOUND("CO-4040", 404),
    INVALID_STATE_TRANSITION("CO-4090", 409),
    INTERNAL_ERROR("CO-5000", 500);

    private final String code;
    private final int httpStatus;

    ComplaintErrorCode(String code, int httpStatus) {
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
