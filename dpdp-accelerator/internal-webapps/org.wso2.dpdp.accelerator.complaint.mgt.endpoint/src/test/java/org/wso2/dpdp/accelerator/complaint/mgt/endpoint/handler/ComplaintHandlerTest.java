/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.handler;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.DAOConstants;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintAttachment;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintQueueStats;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintAttachmentService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintEventService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.CategoryListResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintAttachmentResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintCategoryDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintCreateRequestDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintCreateResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintListResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintQueueStatsResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintRecordDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintStatusUpdateRequestDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintStatusUpdateResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.MeComplaintCreateRequestDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.MeComplaintStatusUpdateRequestDTO;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ComplaintHandlerTest {

    private static final String ORG_ID = DAOConstants.DEFAULT_ORG_ID;

    @Mock
    private ComplaintService complaintService;
    @Mock
    private ComplaintEventService complaintEventService;
    @Mock
    private ComplaintAttachmentService complaintAttachmentService;

    private ComplaintHandler handler;

    @BeforeMethod
    void setUp() {
        MockitoAnnotations.openMocks(this);
        handler = new ComplaintHandler(complaintService, complaintEventService, complaintAttachmentService);
    }

    private Complaint sampleComplaint(String id, String userId, String status) {
        return new Complaint(id, ORG_ID, userId, userId + "-name", "CMP-2026-00001", "DATA_BREACH", "CRITICAL",
                status, "desc", 1L, 2L, 3L);
    }

    private ComplaintAttachment attachment(String id, boolean isPublic) {
        return new ComplaintAttachment(id, ORG_ID, "c1", "f.pdf", "application/pdf", new byte[]{1}, isPublic, 1L);
    }

    private ComplaintAttachmentResponseDTO attachmentBean(String id, boolean isPublic) {
        return ComplaintAttachmentResponseDTO.from(attachment(id, isPublic));
    }

    // ---- officer/admin ----

    @Test
    void createComplaintPassesRequestFieldsThroughToService() {
        ComplaintCreateRequestDTO request = new ComplaintCreateRequestDTO();
        request.setUserId("user1");
        request.setSubjectCategory("DATA_BREACH");
        request.setDescription("desc");
        when(complaintService.createComplaint(ORG_ID, "user1", null, "DATA_BREACH", "desc", "officer1",
                "COMPLAINT_OFFICER")).thenReturn(ComplaintCreateResponseDTO.from(sampleComplaint("c1", "user1", "OPEN")));

        ComplaintCreateResponseDTO response =
                handler.createComplaint(ORG_ID, "officer1", "COMPLAINT_OFFICER", request);

        assertEquals("c1", response.getId());
        assertEquals("OPEN", response.getStatus());
    }

    @Test
    void createComplaintToleratesNullRequestBody() {
        when(complaintService.createComplaint(eq(ORG_ID), eq(null), eq(null), eq(null), eq(null), eq("officer1"),
                eq("COMPLAINT_OFFICER"))).thenReturn(ComplaintCreateResponseDTO.from(sampleComplaint("c1", "user1",
                "OPEN")));

        ComplaintCreateResponseDTO response = handler.createComplaint(ORG_ID, "officer1", "COMPLAINT_OFFICER", null);

        assertEquals("c1", response.getId());
    }

    @Test
    void getComplaintComposesRecordWithAllAttachments() {
        when(complaintService.getComplaint(ORG_ID, "c1")).thenReturn(sampleComplaint("c1", "user1", "OPEN"));
        when(complaintAttachmentService.listAttachmentsForComplaint(ORG_ID, "c1"))
                .thenReturn(List.of(attachmentBean("a1", false)));

        ComplaintRecordDTO bean = handler.getComplaint(ORG_ID, "c1");

        assertEquals("c1", bean.getId());
        assertEquals(1, bean.getAttachments().size());
    }

    @Test
    void listComplaintsDefaultsLimitTo10AndOffsetTo0WhenNotProvided() {
        when(complaintService.listComplaints(eq(ORG_ID), any(), any(), any(), eq(10), eq(0), any(), any()))
                .thenReturn(List.of());

        ComplaintListResponseDTO response = handler.listComplaints(ORG_ID, null, null, null, null, null, null);

        assertEquals(10, response.getMetadata().getLimit());
        assertEquals(0, response.getMetadata().getOffset());
    }

    @Test
    void listComplaintsCapsLimitAt100() {
        when(complaintService.listComplaints(eq(ORG_ID), any(), any(), any(), eq(100), eq(0), any(), any()))
                .thenReturn(List.of());

        ComplaintListResponseDTO response = handler.listComplaints(ORG_ID, null, null, null, 500, null, null);

        assertEquals(100, response.getMetadata().getLimit());
    }

    @Test
    void listComplaintsAttachesAttachmentsAndReportsAccuratePageMetadata() {
        when(complaintService.listComplaints(eq(ORG_ID), any(), any(), any(), eq(10), eq(0), any(), any()))
                .thenAnswer(invocation -> {
                    int[] totalOut = invocation.getArgument(7);
                    totalOut[0] = 42;
                    return List.of(sampleComplaint("c1", "user1", "OPEN"), sampleComplaint("c2", "user1",
                            "IN_PROGRESS"));
                });
        when(complaintAttachmentService.listAttachmentsForComplaint(eq(ORG_ID), anyString())).thenReturn(List.of());

        ComplaintListResponseDTO response = handler.listComplaints(ORG_ID, null, null, null, null, null, null);

        assertEquals(2, response.getData().size());
        assertEquals(42, response.getMetadata().getTotal());
        assertEquals(2, response.getMetadata().getCount());
    }

    @Test
    void getQueueStatsMapsEachCountFromTheServiceResult() {
        when(complaintService.getQueueStats(ORG_ID))
                .thenReturn(ComplaintQueueStatsResponseDTO.from(new ComplaintQueueStats(3, 1, 2, 1)));

        ComplaintQueueStatsResponseDTO response = handler.getQueueStats(ORG_ID);

        assertEquals(3, response.getOpenCount());
        assertEquals(1, response.getAwaitingInternalReviewCount());
        assertEquals(2, response.getResolvedCount());
        assertEquals(1, response.getSlaBreachedCount());
    }

    @Test
    void getCategoriesReturnsEveryKnownCategoryWithItsPriority() {
        CategoryListResponseDTO response = handler.getCategories();

        assertEquals(10, response.getData().size());
        boolean foundDataBreach = false;
        for (ComplaintCategoryDTO bean : response.getData()) {
            if ("DATA_BREACH".equals(bean.getCategory())) {
                assertEquals("CRITICAL", bean.getPriority());
                foundDataBreach = true;
            }
        }
        assertTrue(foundDataBreach);
    }

    @Test
    void updateStatusPassesRequestFieldsThroughToEventService() {
        ComplaintStatusUpdateRequestDTO request = new ComplaintStatusUpdateRequestDTO();
        request.setToStatus("IN_PROGRESS");
        request.setNote("note");
        when(complaintEventService.updateStatus(ORG_ID, "c1", "officer1", "Officer One", "COMPLAINT_OFFICER",
                "IN_PROGRESS", "note")).thenReturn(ComplaintStatusUpdateResponseDTO.from(sampleComplaint("c1",
                "user1", "IN_PROGRESS")));

        ComplaintStatusUpdateResponseDTO response =
                handler.updateStatus(ORG_ID, "c1", "officer1", "Officer One", "COMPLAINT_OFFICER", request);

        assertEquals("IN_PROGRESS", response.getToStatus());
    }

    // ---- Data Principal ----

    @Test
    void createOwnComplaintUsesCallerAsOwnerRegardlessOfRequestBody() {
        MeComplaintCreateRequestDTO request = new MeComplaintCreateRequestDTO();
        request.setSubjectCategory("DATA_BREACH");
        request.setDescription("desc");
        when(complaintService.createComplaint(ORG_ID, "user1", "User One", "DATA_BREACH", "desc"))
                .thenReturn(ComplaintCreateResponseDTO.from(sampleComplaint("c1", "user1", "OPEN")));

        ComplaintCreateResponseDTO response = handler.createOwnComplaint(ORG_ID, "user1", "User One", request);

        assertEquals("c1", response.getId());
    }

    @Test
    void getOwnComplaintFiltersToPublicAttachmentsOnly() {
        when(complaintService.getOwnedComplaint(ORG_ID, "c1", "user1"))
                .thenReturn(sampleComplaint("c1", "user1", "OPEN"));
        when(complaintAttachmentService.listAttachmentsForComplaint(ORG_ID, "c1"))
                .thenReturn(List.of(attachmentBean("a1", true), attachmentBean("a2", false)));

        ComplaintRecordDTO bean = handler.getOwnComplaint(ORG_ID, "c1", "user1");

        assertEquals(1, bean.getAttachments().size());
        assertEquals("a1", bean.getAttachments().get(0).getAttachmentId());
    }

    @Test
    void listOwnComplaintsScopesToOwnerAndFiltersPrivateAttachments() {
        when(complaintService.listComplaints(eq(ORG_ID), any(), any(), eq("user1"), eq(10), eq(0), any(), any()))
                .thenReturn(List.of(sampleComplaint("c1", "user1", "OPEN")));
        when(complaintAttachmentService.listAttachmentsForComplaint(ORG_ID, "c1"))
                .thenReturn(List.of(attachmentBean("a1", false)));

        ComplaintListResponseDTO response = handler.listOwnComplaints(ORG_ID, "user1", null, null, null, null);

        assertEquals(1, response.getData().size());
        assertEquals(0, response.getData().get(0).getAttachments().size());
    }

    @Test
    void updateOwnStatusVerifiesOwnershipAndForcesUserRole() {
        when(complaintService.getOwnedComplaint(ORG_ID, "c1", "user1"))
                .thenReturn(sampleComplaint("c1", "user1", "OPEN"));
        MeComplaintStatusUpdateRequestDTO request = new MeComplaintStatusUpdateRequestDTO();
        request.setToStatus("RESOLVED");
        when(complaintEventService.updateStatus(ORG_ID, "c1", "user1", "User One", "USER", "RESOLVED", null))
                .thenReturn(ComplaintStatusUpdateResponseDTO.from(sampleComplaint("c1", "user1", "RESOLVED")));

        ComplaintStatusUpdateResponseDTO response =
                handler.updateOwnStatus(ORG_ID, "c1", "user1", "User One", request);

        assertEquals("RESOLVED", response.getToStatus());
        verify(complaintService).getOwnedComplaint(ORG_ID, "c1", "user1");
    }

}
