package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ComplaintAttachmentResponseBeanTest {

    @Test
    void allArgsConstructorPopulatesEveryField() {
        ComplaintAttachmentResponseBean bean =
                new ComplaintAttachmentResponseBean("a1", "a.pdf", "application/pdf", 100L);

        assertEquals("a1", bean.getAttachmentId());
        assertEquals("a.pdf", bean.getFileName());
        assertEquals("application/pdf", bean.getContentType());
        assertEquals(100L, bean.getSizeBytes());
    }

    @Test
    void noArgsConstructorAndSettersRoundTrip() {
        ComplaintAttachmentResponseBean bean = new ComplaintAttachmentResponseBean();
        bean.setAttachmentId("a2");
        bean.setFileName("b.png");
        bean.setContentType("image/png");
        bean.setSizeBytes(200L);

        assertEquals("a2", bean.getAttachmentId());
        assertEquals("b.png", bean.getFileName());
        assertEquals("image/png", bean.getContentType());
        assertEquals(200L, bean.getSizeBytes());
    }
}
