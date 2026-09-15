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
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintAttachmentService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintEventService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintAttachmentResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.TimelineListResponseDTO;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ComplaintTimelineHandlerTest {

    private static final String ORG_ID = DAOConstants.DEFAULT_ORG_ID;

    @Mock
    private ComplaintService complaintService;
    @Mock
    private ComplaintEventService complaintEventService;
    @Mock
    private ComplaintAttachmentService complaintAttachmentService;

    private ComplaintTimelineHandler handler;

    @BeforeMethod
    void setUp() {
        MockitoAnnotations.openMocks(this);
        handler = new ComplaintTimelineHandler(complaintService, complaintEventService, complaintAttachmentService);
    }

    private ComplaintEvent entry(String id) {
        ComplaintEvent event = new ComplaintEvent();
        event.setComplaintEventId(id);
        event.setPublic(true);
        event.setActionTime(100L);
        return event;
    }

    // ---- officer/admin ----

    @Test
    void getTimelinePassesFromTimeAndAppliesDefaultLimitAndOffset() {
        when(complaintEventService.getTimeline(eq(ORG_ID), eq("c1"), eq(1000L), isNull(), isNull(), isNull(), eq(20),
                eq(0), any())).thenReturn(List.of());

        TimelineListResponseDTO response = handler.getTimeline(ORG_ID, "c1", 1000L, null, null, null, null);

        assertEquals(20, response.getMetadata().getLimit());
        assertEquals(0, response.getMetadata().getOffset());
    }

    @Test
    void getTimelinePassesToTime() {
        when(complaintEventService.getTimeline(eq(ORG_ID), eq("c1"), isNull(), eq(2000L), isNull(), isNull(), eq(20),
                eq(0), any())).thenReturn(List.of());

        handler.getTimeline(ORG_ID, "c1", null, 2000L, null, null, null);

        verify(complaintEventService).getTimeline(eq(ORG_ID), eq("c1"), isNull(), eq(2000L), isNull(), isNull(),
                eq(20), eq(0), any());
    }

    @Test
    void getTimelineCapsLimitAt100() {
        when(complaintEventService.getTimeline(eq(ORG_ID), eq("c1"), isNull(), isNull(), isNull(), isNull(), eq(100),
                eq(0), any())).thenReturn(List.of());

        handler.getTimeline(ORG_ID, "c1", null, null, null, 500, null);

        verify(complaintEventService).getTimeline(eq(ORG_ID), eq("c1"), isNull(), isNull(), isNull(), isNull(),
                eq(100), eq(0), any());
    }

    @Test
    void getTimelineComposesEveryEntryRegardlessOfIsPublic() {
        when(complaintEventService.getTimeline(eq(ORG_ID), eq("c1"), isNull(), isNull(), isNull(), isNull(), eq(20),
                eq(0), any())).thenReturn(List.of(entry("e1"), entry("e2")));

        TimelineListResponseDTO response = handler.getTimeline(ORG_ID, "c1", null, null, null, null, null);

        assertEquals(2, response.getData().size());
        assertEquals("e1", response.getData().get(0).getId());
    }

    @Test
    void getTimelineGroupsAttachmentsUnderTheEventTheyWereUploadedWith() {
        when(complaintEventService.getTimeline(eq(ORG_ID), eq("c1"), isNull(), isNull(), isNull(), isNull(), eq(20),
                eq(0), any())).thenReturn(List.of(entry("e1"), entry("e2")));
        ComplaintAttachment forE1 = new ComplaintAttachment();
        forE1.setAttachmentId("a1");
        forE1.setComplaintEventId("e1");
        when(complaintAttachmentService.listAttachmentsForComplaint(ORG_ID, "c1"))
                .thenReturn(List.of(ComplaintAttachmentResponseDTO.from(forE1)));

        TimelineListResponseDTO response = handler.getTimeline(ORG_ID, "c1", null, null, null, null, null);

        assertEquals(1, response.getData().get(0).getAttachments().size());
        assertEquals("a1", response.getData().get(0).getAttachments().get(0).getAttachmentId());
        assertEquals(0, response.getData().get(1).getAttachments().size());
    }

    // ---- Data Principal ----

    @Test
    void getOwnTimelineVerifiesOwnershipAndRestrictsToPublicEntries() {
        when(complaintService.getOwnedComplaint(ORG_ID, "c1", "user1"))
                .thenReturn(new Complaint("c1", ORG_ID, "user1", "User One", "CMP-1", "DATA_BREACH", "LOW", "OPEN",
                        "d", 1L, 1L, 1L));
        when(complaintEventService.getTimeline(eq(ORG_ID), eq("c1"), isNull(), isNull(), eq(true), isNull(), eq(20),
                eq(0), any())).thenReturn(List.of(entry("e1")));

        TimelineListResponseDTO response =
                handler.getOwnTimeline(ORG_ID, "c1", "user1", null, null, null, null, null);

        assertEquals(1, response.getData().size());
        verify(complaintService).getOwnedComplaint(ORG_ID, "c1", "user1");
    }

}
