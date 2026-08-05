package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ComplaintAttachmentDownloadResponseBeanTest {

    @Test
    void constructorBase64EncodesNonNullContent() {
        byte[] raw = "hello".getBytes();

        ComplaintAttachmentDownloadResponseBean bean =
                new ComplaintAttachmentDownloadResponseBean("a1", "a.pdf", "application/pdf", raw);

        assertEquals("a1", bean.getAttachmentId());
        assertEquals("a.pdf", bean.getFileName());
        assertEquals("application/pdf", bean.getContentType());
        assertEquals(Base64.getEncoder().encodeToString(raw), bean.getContent());
    }

    @Test
    void constructorLeavesContentNullWhenRawContentIsNull() {
        ComplaintAttachmentDownloadResponseBean bean =
                new ComplaintAttachmentDownloadResponseBean("a1", "a.pdf", "application/pdf", null);

        assertNull(bean.getContent());
    }

    @Test
    void noArgsConstructorAndSettersRoundTrip() {
        ComplaintAttachmentDownloadResponseBean bean = new ComplaintAttachmentDownloadResponseBean();
        bean.setAttachmentId("a2");
        bean.setFileName("b.png");
        bean.setContentType("image/png");
        bean.setContent("base64content");

        assertEquals("a2", bean.getAttachmentId());
        assertEquals("b.png", bean.getFileName());
        assertEquals("image/png", bean.getContentType());
        assertEquals("base64content", bean.getContent());
    }
}
