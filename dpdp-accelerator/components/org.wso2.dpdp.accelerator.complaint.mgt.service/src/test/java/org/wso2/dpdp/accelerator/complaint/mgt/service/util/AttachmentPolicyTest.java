package org.wso2.dpdp.accelerator.complaint.mgt.service.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachmentPolicyTest {

    private static final String PROP = "CO_MAX_ATTACHMENT_SIZE_BYTES";

    @AfterEach
    void clearSystemProperty() {
        System.clearProperty(PROP);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "image/png",
            "image/jpeg"
    })
    void allowsEachDocumentedContentType(String contentType) {
        assertTrue(AttachmentPolicy.isAllowedContentType(contentType));
    }

    @Test
    void rejectsUnknownOrNullContentType() {
        assertFalse(AttachmentPolicy.isAllowedContentType("application/zip"));
        assertFalse(AttachmentPolicy.isAllowedContentType(null));
    }

    @Test
    void isAllowedContentTypeTrimsWhitespace() {
        assertTrue(AttachmentPolicy.isAllowedContentType("  image/png  "));
    }

    @Test
    void defaultMaxSizeIsTenMegabytes() {
        System.clearProperty(PROP);

        assertEquals(10L * 1024 * 1024, AttachmentPolicy.getMaxSizeBytes());
    }

    @Test
    void usesConfiguredMaxSizeWhenPropertySet() {
        System.setProperty(PROP, "2048");

        assertEquals(2048L, AttachmentPolicy.getMaxSizeBytes());
    }

    @Test
    void fallsBackToDefaultWhenPropertyIsNotAValidNumber() {
        System.setProperty(PROP, "not-a-number");

        assertEquals(10L * 1024 * 1024, AttachmentPolicy.getMaxSizeBytes());
    }
}
