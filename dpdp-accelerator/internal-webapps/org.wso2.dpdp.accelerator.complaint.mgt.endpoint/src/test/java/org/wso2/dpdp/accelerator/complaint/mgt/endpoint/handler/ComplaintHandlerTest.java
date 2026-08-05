package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintCreateRequestBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintCreateResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintListResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintRecordBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintStatusUpdateRequestBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintStatusUpdateResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintAttachmentService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintEventService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintDTO;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplaintHandlerTest {

    @Mock
    private ComplaintService complaintService;
    @Mock
    private ComplaintEventService complaintEventService;
    @Mock
    private ComplaintAttachmentService complaintAttachmentService;

    private ComplaintHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ComplaintHandler(complaintService, complaintEventService, complaintAttachmentService);
    }

    private ComplaintDTO sampleDto(String id, String status) {
        return new ComplaintDTO(id, "CMP-2026-00001", "DATA_BREACH", "CRITICAL", status, "user1", "desc", 1L, 2L, 3L);
    }

    @Test
    void createComplaintPassesRequestFieldsThroughToService() {
        ComplaintCreateRequestBean request = new ComplaintCreateRequestBean();
        request.setUserId("user1");
        request.setSubjectCategory("DATA_BREACH");
        request.setDescription("desc");
        when(complaintService.createComplaint("org1", "user1", "DATA_BREACH", "desc"))
                .thenReturn(sampleDto("c1", "OPEN"));

        ComplaintCreateResponseBean response = handler.createComplaint("org1", request);

        assertEquals("c1", response.getId());
        assertEquals("OPEN", response.getStatus());
    }

    @Test
    void createComplaintToleratesNullRequestBody() {
        when(complaintService.createComplaint(eq("org1"), eq(null), eq(null), eq(null)))
                .thenReturn(sampleDto("c1", "OPEN"));

        ComplaintCreateResponseBean response = handler.createComplaint("org1", null);

        assertEquals("c1", response.getId());
    }

    @Test
    void getComplaintComposesRecordWithAttachments() {
        when(complaintService.getComplaint("org1", "c1")).thenReturn(sampleDto("c1", "OPEN"));
        when(complaintAttachmentService.listAttachmentsForComplaint("org1", "c1")).thenReturn(List.of());

        ComplaintRecordBean bean = handler.getComplaint("org1", "c1");

        assertEquals("c1", bean.getId());
        assertEquals(0, bean.getAttachments().size());
    }

    @Test
    void listComplaintsDefaultsLimitTo10AndOffsetTo0WhenNotProvided() {
        when(complaintService.listComplaints(eq("org1"), any(), any(), any(), eq(10), eq(0), any(), any()))
                .thenReturn(List.of());

        ComplaintListResponseBean response = handler.listComplaints("org1", null, null, null, null, null, null);

        assertEquals(10, response.getMetadata().getLimit());
        assertEquals(0, response.getMetadata().getOffset());
    }

    @Test
    void listComplaintsCapsLimitAt100() {
        when(complaintService.listComplaints(eq("org1"), any(), any(), any(), eq(100), eq(0), any(), any()))
                .thenReturn(List.of());

        ComplaintListResponseBean response = handler.listComplaints("org1", null, null, null, 500, null, null);

        assertEquals(100, response.getMetadata().getLimit());
    }

    @Test
    void listComplaintsIgnoresNonPositiveLimitAndFallsBackToDefault() {
        when(complaintService.listComplaints(eq("org1"), any(), any(), any(), eq(10), eq(0), any(), any()))
                .thenReturn(List.of());

        handler.listComplaints("org1", null, null, null, 0, null, null);

        verify(complaintService).listComplaints(eq("org1"), any(), any(), any(), eq(10), eq(0), any(), any());
    }

    @Test
    void listComplaintsIgnoresNegativeOffsetAndFallsBackToZero() {
        when(complaintService.listComplaints(eq("org1"), any(), any(), any(), eq(10), eq(0), any(), any()))
                .thenReturn(List.of());

        handler.listComplaints("org1", null, null, null, null, -5, null);

        verify(complaintService).listComplaints(eq("org1"), any(), any(), any(), eq(10), eq(0), any(), any());
    }

    @Test
    void listComplaintsAttachesAttachmentsAndReportsAccuratePageMetadata() {
        when(complaintService.listComplaints(eq("org1"), any(), any(), any(), eq(10), eq(0), any(), any()))
                .thenAnswer(invocation -> {
                    int[] totalOut = invocation.getArgument(7);
                    totalOut[0] = 42;
                    return List.of(sampleDto("c1", "OPEN"), sampleDto("c2", "IN_PROGRESS"));
                });
        when(complaintAttachmentService.listAttachmentsForComplaint(eq("org1"), anyString())).thenReturn(List.of());

        ComplaintListResponseBean response = handler.listComplaints("org1", null, null, null, null, null, null);

        assertEquals(2, response.getData().size());
        assertEquals(42, response.getMetadata().getTotal());
        assertEquals(2, response.getMetadata().getCount());
    }

    @Test
    void updateStatusPassesRequestFieldsThroughToEventService() {
        ComplaintStatusUpdateRequestBean request = new ComplaintStatusUpdateRequestBean();
        request.setActorUserId("officer1");
        request.setActorRole("COMPLAINT_OFFICER");
        request.setToStatus("IN_PROGRESS");
        request.setNote("note");
        when(complaintEventService.updateStatus("org1", "c1", "officer1", "COMPLAINT_OFFICER", "IN_PROGRESS", "note"))
                .thenReturn(sampleDto("c1", "IN_PROGRESS"));

        ComplaintStatusUpdateResponseBean response = handler.updateStatus("org1", "c1", request);

        assertEquals("IN_PROGRESS", response.getToStatus());
    }

    @Test
    void noArgsConstructorWiresRealServiceImplementations() {
        assertNotNull(new ComplaintHandler());
    }
}
