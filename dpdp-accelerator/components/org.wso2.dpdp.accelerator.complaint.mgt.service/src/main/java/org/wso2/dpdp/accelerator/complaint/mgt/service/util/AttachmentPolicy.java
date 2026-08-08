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

package org.wso2.dpdp.accelerator.complaint.mgt.service.util;

import java.util.Set;

/**
 * Matches the encoding.file.contentType list declared in the OpenAPI spec for both attachment
 * upload endpoints. Max size is configurable via the CO_MAX_ATTACHMENT_SIZE_BYTES system property
 * (defaults to 10 MB) since the spec references "the configured max" without stating a number.
 */
public class AttachmentPolicy {

    public static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "image/png",
            "image/jpeg");

    private static final long DEFAULT_MAX_SIZE_BYTES = 10L * 1024 * 1024; // 10 MB

    private AttachmentPolicy() {
    }

    public static long getMaxSizeBytes() {
        String configured = System.getProperty("CO_MAX_ATTACHMENT_SIZE_BYTES");
        if (configured != null) {
            try {
                return Long.parseLong(configured.trim());
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        return DEFAULT_MAX_SIZE_BYTES;
    }

    public static boolean isAllowedContentType(String contentType) {
        return contentType != null && ALLOWED_CONTENT_TYPES.contains(contentType.trim());
    }
}
