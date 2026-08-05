package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean;

import org.junit.jupiter.api.Test;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.util.DateTimeUtil;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintCommentDTO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComplaintCommentCreateResponseBeanTest {

    @Test
    void fromMapsEveryField() {
        ComplaintCommentDTO dto = new ComplaintCommentDTO("e1", "user1", "USER", "hello", true, "OPEN",
                "IN_PROGRESS", 100L);

        ComplaintCommentCreateResponseBean bean = ComplaintCommentCreateResponseBean.from(dto);

        assertEquals("e1", bean.getId());
        assertEquals("user1", bean.getActorUserId());
        assertEquals("USER", bean.getActorRole());
        assertEquals("hello", bean.getMessage());
        assertTrue(bean.isPublic());
        assertEquals("OPEN", bean.getFromStatus());
        assertEquals("IN_PROGRESS", bean.getToStatus());
        assertEquals(DateTimeUtil.toIso(100L), bean.getCreatedTime());
    }

    @Test
    void noArgsConstructorAndSettersRoundTrip() {
        ComplaintCommentCreateResponseBean bean = new ComplaintCommentCreateResponseBean();
        bean.setId("e2");
        bean.setActorUserId("officer1");
        bean.setActorRole("COMPLAINT_OFFICER");
        bean.setMessage("note");
        bean.setPublic(false);
        bean.setFromStatus("IN_PROGRESS");
        bean.setToStatus("RESOLVED");
        bean.setCreatedTime("2026-01-01T00:00:00Z");

        assertEquals("e2", bean.getId());
        assertEquals("officer1", bean.getActorUserId());
        assertEquals("COMPLAINT_OFFICER", bean.getActorRole());
        assertEquals("note", bean.getMessage());
        assertEquals(false, bean.isPublic());
        assertEquals("IN_PROGRESS", bean.getFromStatus());
        assertEquals("RESOLVED", bean.getToStatus());
        assertEquals("2026-01-01T00:00:00Z", bean.getCreatedTime());
    }
}
