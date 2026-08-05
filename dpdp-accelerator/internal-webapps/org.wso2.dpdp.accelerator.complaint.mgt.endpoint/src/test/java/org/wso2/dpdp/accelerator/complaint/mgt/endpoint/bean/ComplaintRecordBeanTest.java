package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean;

import org.junit.jupiter.api.Test;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.util.DateTimeUtil;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintAttachmentDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintDTO;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComplaintRecordBeanTest {

    private ComplaintDTO sampleDto() {
        return new ComplaintDTO("c1", "CMP-2026-00001", "DATA_BREACH", "CRITICAL", "OPEN", "user1", "desc", 1L, 2L,
                3L);
    }

    @Test
    void fromMapsEveryFieldAndEachAttachment() {
        ComplaintAttachmentDTO attachment = new ComplaintAttachmentDTO("a1", "a.pdf", "application/pdf", 100L);

        ComplaintRecordBean bean = ComplaintRecordBean.from(sampleDto(), List.of(attachment));

        assertEquals("c1", bean.getId());
        assertEquals("CMP-2026-00001", bean.getReferenceId());
        assertEquals("DATA_BREACH", bean.getSubjectCategory());
        assertEquals("CRITICAL", bean.getPriority());
        assertEquals("OPEN", bean.getStatus());
        assertEquals("user1", bean.getUserId());
        assertEquals("desc", bean.getDescription());
        assertEquals(DateTimeUtil.toIso(1L), bean.getSubmittedAt());
        assertEquals(DateTimeUtil.toIso(2L), bean.getUpdatedAt());
        assertEquals(DateTimeUtil.toIso(3L), bean.getStatutoryDueDate());
        assertEquals(1, bean.getAttachments().size());
        assertEquals("a1", bean.getAttachments().get(0).getAttachmentId());
    }

    @Test
    void fromProducesAnEmptyAttachmentListWhenGivenNull() {
        ComplaintRecordBean bean = ComplaintRecordBean.from(sampleDto(), null);

        assertTrue(bean.getAttachments().isEmpty());
    }

    @Test
    void noArgsConstructorAndSettersRoundTrip() {
        ComplaintRecordBean bean = new ComplaintRecordBean();
        bean.setId("c2");
        bean.setReferenceId("CMP-2026-00002");
        bean.setSubjectCategory("OTHER");
        bean.setPriority("LOW");
        bean.setStatus("RESOLVED");
        bean.setUserId("user2");
        bean.setDescription("desc2");
        bean.setAttachments(List.of());
        bean.setSubmittedAt("2026-01-01T00:00:00Z");
        bean.setUpdatedAt("2026-01-02T00:00:00Z");
        bean.setStatutoryDueDate("2026-04-01T00:00:00Z");

        assertEquals("c2", bean.getId());
        assertEquals("CMP-2026-00002", bean.getReferenceId());
        assertEquals("OTHER", bean.getSubjectCategory());
        assertEquals("LOW", bean.getPriority());
        assertEquals("RESOLVED", bean.getStatus());
        assertEquals("user2", bean.getUserId());
        assertEquals("desc2", bean.getDescription());
        assertTrue(bean.getAttachments().isEmpty());
        assertEquals("2026-01-01T00:00:00Z", bean.getSubmittedAt());
        assertEquals("2026-01-02T00:00:00Z", bean.getUpdatedAt());
        assertEquals("2026-04-01T00:00:00Z", bean.getStatutoryDueDate());
    }
}
