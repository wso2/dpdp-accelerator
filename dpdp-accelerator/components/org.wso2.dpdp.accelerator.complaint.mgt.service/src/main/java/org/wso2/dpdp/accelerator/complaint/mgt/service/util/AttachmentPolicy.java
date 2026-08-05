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
