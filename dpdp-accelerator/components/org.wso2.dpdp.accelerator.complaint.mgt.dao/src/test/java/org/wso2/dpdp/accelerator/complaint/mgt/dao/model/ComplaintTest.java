package org.wso2.dpdp.accelerator.complaint.mgt.dao.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ComplaintTest {

    @Test
    void allArgsConstructorPopulatesEveryField() {
        Complaint complaint = new Complaint("c1", "org1", "user1", "CMP-2026-00001", "DATA_BREACH", "CRITICAL",
                "OPEN", "description text", 100L, 200L, 300L);

        assertEquals("c1", complaint.getComplaintId());
        assertEquals("org1", complaint.getOrgId());
        assertEquals("user1", complaint.getUserId());
        assertEquals("CMP-2026-00001", complaint.getReferenceId());
        assertEquals("DATA_BREACH", complaint.getCategory());
        assertEquals("CRITICAL", complaint.getPriority());
        assertEquals("OPEN", complaint.getStatus());
        assertEquals("description text", complaint.getDescription());
        assertEquals(100L, complaint.getCreatedTime());
        assertEquals(200L, complaint.getUpdatedTime());
        assertEquals(300L, complaint.getStatutoryDueTime());
    }

    @Test
    void noArgsConstructorAndSettersRoundTrip() {
        Complaint complaint = new Complaint();
        complaint.setComplaintId("c2");
        complaint.setOrgId("org2");
        complaint.setUserId("user2");
        complaint.setReferenceId("CMP-2026-00002");
        complaint.setCategory("OTHER");
        complaint.setPriority("LOW");
        complaint.setStatus("RESOLVED");
        complaint.setDescription("another description");
        complaint.setCreatedTime(1L);
        complaint.setUpdatedTime(2L);
        complaint.setStatutoryDueTime(3L);

        assertEquals("c2", complaint.getComplaintId());
        assertEquals("org2", complaint.getOrgId());
        assertEquals("user2", complaint.getUserId());
        assertEquals("CMP-2026-00002", complaint.getReferenceId());
        assertEquals("OTHER", complaint.getCategory());
        assertEquals("LOW", complaint.getPriority());
        assertEquals("RESOLVED", complaint.getStatus());
        assertEquals("another description", complaint.getDescription());
        assertEquals(1L, complaint.getCreatedTime());
        assertEquals(2L, complaint.getUpdatedTime());
        assertEquals(3L, complaint.getStatutoryDueTime());
    }
}
