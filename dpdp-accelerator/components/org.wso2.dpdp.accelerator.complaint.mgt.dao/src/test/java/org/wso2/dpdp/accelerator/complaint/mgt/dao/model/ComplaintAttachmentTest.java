package org.wso2.dpdp.accelerator.complaint.mgt.dao.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ComplaintAttachmentTest {

    @Test
    void getSizeBytesPrefersLoadedFileDataOverOverride() {
        ComplaintAttachment attachment = new ComplaintAttachment("a1", "org1", "c1", null, "file.pdf",
                "application/pdf", new byte[]{1, 2, 3, 4, 5}, 100L);
        attachment.setSizeBytesOverride(999L);

        assertEquals(5L, attachment.getSizeBytes());
    }

    @Test
    void getSizeBytesFallsBackToOverrideWhenFileDataNotLoaded() {
        ComplaintAttachment attachment = new ComplaintAttachment();
        attachment.setSizeBytesOverride(42L);

        assertEquals(42L, attachment.getSizeBytes());
    }

    @Test
    void getSizeBytesReturnsZeroWhenNeitherFileDataNorOverrideIsSet() {
        ComplaintAttachment attachment = new ComplaintAttachment();

        assertEquals(0L, attachment.getSizeBytes());
    }

    @Test
    void allArgsConstructorPopulatesEveryField() {
        byte[] data = {9, 8, 7};
        ComplaintAttachment attachment = new ComplaintAttachment("a1", "org1", "c1", "e1", "file.png", "image/png",
                data, 500L);

        assertEquals("a1", attachment.getAttachmentId());
        assertEquals("org1", attachment.getOrgId());
        assertEquals("c1", attachment.getComplaintId());
        assertEquals("e1", attachment.getEventId());
        assertEquals("file.png", attachment.getFileName());
        assertEquals("image/png", attachment.getContentType());
        assertEquals(data, attachment.getFileData());
        assertEquals(500L, attachment.getCreatedTime());
    }
}
