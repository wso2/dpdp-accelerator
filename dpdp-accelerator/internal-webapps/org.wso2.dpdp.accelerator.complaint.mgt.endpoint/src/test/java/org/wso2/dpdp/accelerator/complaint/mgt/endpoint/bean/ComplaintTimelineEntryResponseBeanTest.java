package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean;

import org.junit.jupiter.api.Test;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.util.DateTimeUtil;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintAttachmentDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintTimelineEntryDTO;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComplaintTimelineEntryResponseBeanTest {

    private ComplaintTimelineEntryDTO sampleDto() {
        ComplaintTimelineEntryDTO dto = new ComplaintTimelineEntryDTO();
        dto.setId("e1");
        dto.setType("STATUS_CHANGE");
        dto.setPublic(true);
        dto.setActorUserId("user1");
        dto.setActorRole("USER");
        dto.setMessage("hello");
        dto.setFromStatus("OPEN");
        dto.setToStatus("IN_PROGRESS");
        dto.setCreatedTime(100L);
        return dto;
    }

    @Test
    void fromMapsEveryFieldAndEachAttachment() {
        ComplaintAttachmentDTO attachment = new ComplaintAttachmentDTO("a1", "a.pdf", "application/pdf", 100L);

        ComplaintTimelineEntryResponseBean bean =
                ComplaintTimelineEntryResponseBean.from(sampleDto(), List.of(attachment));

        assertEquals("e1", bean.getId());
        assertEquals("STATUS_CHANGE", bean.getType());
        assertTrue(bean.isPublic());
        assertEquals("user1", bean.getActorUserId());
        assertEquals("USER", bean.getActorRole());
        assertEquals("hello", bean.getMessage());
        assertEquals("OPEN", bean.getFromStatus());
        assertEquals("IN_PROGRESS", bean.getToStatus());
        assertEquals(DateTimeUtil.toIso(100L), bean.getCreatedTime());
        assertEquals(1, bean.getAttachments().size());
        assertEquals("a1", bean.getAttachments().get(0).getAttachmentId());
    }

    @Test
    void fromProducesAnEmptyAttachmentListWhenGivenNull() {
        ComplaintTimelineEntryResponseBean bean = ComplaintTimelineEntryResponseBean.from(sampleDto(), null);

        assertTrue(bean.getAttachments().isEmpty());
    }

    @Test
    void noArgsConstructorAndSettersRoundTrip() {
        ComplaintTimelineEntryResponseBean bean = new ComplaintTimelineEntryResponseBean();
        bean.setId("e2");
        bean.setType("COMMENT");
        bean.setPublic(false);
        bean.setActorUserId("officer1");
        bean.setActorRole("COMPLAINT_OFFICER");
        bean.setMessage("note");
        bean.setFromStatus(null);
        bean.setToStatus(null);
        bean.setAttachments(List.of());
        bean.setCreatedTime("2026-01-01T00:00:00Z");

        assertEquals("e2", bean.getId());
        assertEquals("COMMENT", bean.getType());
        assertEquals(false, bean.isPublic());
        assertEquals("officer1", bean.getActorUserId());
        assertEquals("COMPLAINT_OFFICER", bean.getActorRole());
        assertEquals("note", bean.getMessage());
        assertEquals(null, bean.getFromStatus());
        assertEquals(null, bean.getToStatus());
        assertTrue(bean.getAttachments().isEmpty());
        assertEquals("2026-01-01T00:00:00Z", bean.getCreatedTime());
    }
}
