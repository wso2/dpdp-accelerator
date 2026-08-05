package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean;

import org.junit.jupiter.api.Test;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.util.DateTimeUtil;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintDTO;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ComplaintStatusUpdateResponseBeanTest {

    @Test
    void fromSetsAFixedConfirmationMessageAndMapsStatusAndUpdatedTime() {
        ComplaintDTO dto = new ComplaintDTO("c1", "CMP-2026-00001", "DATA_BREACH", "CRITICAL", "IN_PROGRESS", "user1",
                "desc", 1L, 2L, 3L);

        ComplaintStatusUpdateResponseBean bean = ComplaintStatusUpdateResponseBean.from(dto);

        assertEquals("Status transition confirmed", bean.getMessage());
        assertEquals("IN_PROGRESS", bean.getToStatus());
        assertEquals(DateTimeUtil.toIso(2L), bean.getUpdatedAt());
    }

    @Test
    void noArgsConstructorAndSettersRoundTrip() {
        ComplaintStatusUpdateResponseBean bean = new ComplaintStatusUpdateResponseBean();
        bean.setMessage("custom message");
        bean.setToStatus("RESOLVED");
        bean.setUpdatedAt("2026-01-01T00:00:00Z");

        assertEquals("custom message", bean.getMessage());
        assertEquals("RESOLVED", bean.getToStatus());
        assertEquals("2026-01-01T00:00:00Z", bean.getUpdatedAt());
    }
}
