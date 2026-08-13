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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.TimelineListResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintAttachmentService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintEventService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplaintTimelineHandlerTest {

    @Mock
    private ComplaintEventService complaintEventService;
    @Mock
    private ComplaintAttachmentService complaintAttachmentService;

    private ComplaintTimelineHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ComplaintTimelineHandler(complaintEventService, complaintAttachmentService);
    }

    private ComplaintEvent entry(String id) {
        ComplaintEvent event = new ComplaintEvent();
        event.setComplaintEventId(id);
        event.setPublic(true);
        event.setActionTime(100L);
        return event;
    }

    @Test
    void getTimelineParsesIsoSinceParamAndAppliesDefaultLimitAndOffset() {
        when(complaintEventService.getTimeline(eq("org1"), eq("c1"), eq(1000L), isNull(), isNull(), eq(20), eq(0),
                any())).thenReturn(List.of());

        TimelineListResponseBean response =
                handler.getTimeline("org1", "c1", "1970-01-01T00:00:01Z", null, null, null, null);

        assertEquals(20, response.getMetadata().getLimit());
        assertEquals(0, response.getMetadata().getOffset());
    }

    @Test
    void getTimelineTreatsBlankSinceAsAbsent() {
        when(complaintEventService.getTimeline(eq("org1"), eq("c1"), isNull(), isNull(), isNull(), eq(20), eq(0),
                any())).thenReturn(List.of());

        handler.getTimeline("org1", "c1", "  ", null, null, null, null);

        verify(complaintEventService).getTimeline(eq("org1"), eq("c1"), isNull(), isNull(), isNull(), eq(20), eq(0),
                any());
    }

    @Test
    void getTimelineCapsLimitAt100() {
        when(complaintEventService.getTimeline(eq("org1"), eq("c1"), isNull(), isNull(), isNull(), eq(100), eq(0),
                any())).thenReturn(List.of());

        handler.getTimeline("org1", "c1", null, null, null, 500, null);

        verify(complaintEventService).getTimeline(eq("org1"), eq("c1"), isNull(), isNull(), isNull(), eq(100), eq(0),
                any());
    }

    @Test
    void getTimelinePassesIsPublicFilterToService() {
        when(complaintEventService.getTimeline(eq("org1"), eq("c1"), isNull(), eq(false), isNull(), eq(20), eq(0),
                any())).thenReturn(List.of());

        handler.getTimeline("org1", "c1", null, false, null, null, null);

        verify(complaintEventService).getTimeline(eq("org1"), eq("c1"), isNull(), eq(false), isNull(), eq(20), eq(0),
                any());
    }

    @Test
    void getTimelineComposesEachEntryWithItsAttachments() {
        when(complaintEventService.getTimeline(eq("org1"), eq("c1"), isNull(), isNull(), isNull(), eq(20), eq(0),
                any())).thenReturn(List.of(entry("e1"), entry("e2")));
        when(complaintAttachmentService.listAttachmentsForEvent(eq("org1"), eq("c1"), anyString()))
                .thenReturn(List.of());

        TimelineListResponseBean response = handler.getTimeline("org1", "c1", null, null, null, null, null);

        assertEquals(2, response.getData().size());
        assertEquals("e1", response.getData().get(0).getId());
        verify(complaintAttachmentService).listAttachmentsForEvent("org1", "c1", "e1");
        verify(complaintAttachmentService).listAttachmentsForEvent("org1", "c1", "e2");
    }

    @Test
    void noArgsConstructorWiresRealServiceImplementations() {
        assertNotNull(new ComplaintTimelineHandler());
    }
}
