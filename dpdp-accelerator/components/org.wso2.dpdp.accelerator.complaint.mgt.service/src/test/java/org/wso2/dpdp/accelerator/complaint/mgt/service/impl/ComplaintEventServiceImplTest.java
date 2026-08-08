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

package org.wso2.dpdp.accelerator.complaint.mgt.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintEventDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplaintEventServiceImplTest {

    @Mock
    private ComplaintEventDAO complaintEventDAO;
    @Mock
    private ComplaintDAO complaintDAO;
    @Mock
    private ComplaintService complaintService;

    private ComplaintEventServiceImpl eventService;

    @BeforeEach
    void setUp() {
        eventService = new ComplaintEventServiceImpl(complaintEventDAO, complaintDAO, complaintService);
    }

    private Complaint openComplaint() {
        return new Complaint("c1", "org1", "user1", "CMP-2026-00001", "DATA_BREACH", "CRITICAL", "OPEN", "desc", 1L,
                2L, 3L);
    }

    // ---- getTimeline ----

    @Test
    void getTimelineRequiresComplaintToExistFirst() {
        when(complaintService.requireComplaint("org1", "c1")).thenThrow(
                new ComplaintException("CO-4040", "Complaint not found", "desc", 404));

        assertThrows(ComplaintException.class,
                () -> eventService.getTimeline("org1", "c1", null, null, "asc", 10, 0, new int[1]));

        verifyNoInteractions(complaintEventDAO);
    }

    @Test
    void getTimelineMapsEventsToDtos() {
        when(complaintService.requireComplaint("org1", "c1")).thenReturn(openComplaint());
        ComplaintEvent statusChange = new ComplaintEvent("e1", "org1", "c1", "user1", "USER", true, "note", "OPEN",
                "IN_PROGRESS", 100L);
        int[] totalOut = new int[1];
        when(complaintEventDAO.listEvents("org1", "c1", null, null, "asc", 10, 0, totalOut))
                .thenReturn(List.of(statusChange));

        List<ComplaintEvent> entries =
                eventService.getTimeline("org1", "c1", null, null, "asc", 10, 0, totalOut);

        assertEquals(1, entries.size());
        assertEquals("STATUS_CHANGE", entries.get(0).deriveEntryType());
        assertEquals("OPEN", entries.get(0).getFromStatus());
        assertEquals("IN_PROGRESS", entries.get(0).getToStatus());
    }

    @Test
    void getTimelinePassesIsPublicFilterToDao() {
        when(complaintService.requireComplaint("org1", "c1")).thenReturn(openComplaint());
        int[] totalOut = new int[1];
        when(complaintEventDAO.listEvents("org1", "c1", null, false, "asc", 10, 0, totalOut))
                .thenReturn(List.of());

        eventService.getTimeline("org1", "c1", null, false, "asc", 10, 0, totalOut);

        verify(complaintEventDAO).listEvents("org1", "c1", null, false, "asc", 10, 0, totalOut);
    }

    // ---- addComment ----

    @Test
    void addCommentThrowsWhenMessageIsBlank() {
        when(complaintService.requireComplaint("org1", "c1")).thenReturn(openComplaint());

        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> eventService.addComment("org1", "c1", "user1", "USER", " ", true, null));

        assertEquals("CO-4002", ex.getCode());
    }

    @Test
    void addCommentThrowsWhenActorUserIdIsBlank() {
        when(complaintService.requireComplaint("org1", "c1")).thenReturn(openComplaint());

        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> eventService.addComment("org1", "c1", " ", "USER", "hello", true, null));

        assertEquals("CO-4002", ex.getCode());
    }

    @Test
    void addCommentThrowsWhenActorRoleIsInvalid() {
        when(complaintService.requireComplaint("org1", "c1")).thenReturn(openComplaint());

        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> eventService.addComment("org1", "c1", "user1", "SYSTEM", "hello", true, null));

        assertEquals("CO-4002", ex.getCode());
    }

    @Test
    void addCommentThrowsForbiddenWhenUserTriesToSetIsPublicFalse() {
        when(complaintService.requireComplaint("org1", "c1")).thenReturn(openComplaint());

        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> eventService.addComment("org1", "c1", "user1", "USER", "hello", false, null));

        assertEquals("CO-4030", ex.getCode());
        assertEquals(403, ex.getStatusCode());
    }

    @Test
    void addCommentAllowsComplaintOfficerToSetIsPublicFalse() {
        when(complaintService.requireComplaint("org1", "c1")).thenReturn(openComplaint());
        when(complaintEventDAO.addEvent(any(ComplaintEvent.class))).thenReturn(true);

        ComplaintEvent event =
                eventService.addComment("org1", "c1", "officer1", "COMPLAINT_OFFICER", "internal note", false, null);

        assertEquals(false, event.isPublic());
        assertEquals("internal note", event.getComment());
    }

    @Test
    void addCommentThrowsOnInvalidStatusTransition() {
        when(complaintService.requireComplaint("org1", "c1")).thenReturn(openComplaint());

        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> eventService.addComment("org1", "c1", "officer1", "COMPLAINT_OFFICER", "note", true,
                        "RESOLVED"));

        assertEquals("CO-4090", ex.getCode());
        assertEquals(409, ex.getStatusCode());
        verify(complaintEventDAO, never()).addEvent(any());
    }

    @Test
    void addCommentWithValidToStatusUpdatesComplaintStatus() {
        when(complaintService.requireComplaint("org1", "c1")).thenReturn(openComplaint());
        when(complaintEventDAO.addEvent(any(ComplaintEvent.class))).thenReturn(true);
        when(complaintDAO.updateStatus(eq("c1"), eq("org1"), eq("IN_PROGRESS"), anyLong())).thenReturn(true);

        ComplaintEvent event = eventService.addComment("org1", "c1", "officer1", "COMPLAINT_OFFICER", "note",
                true, "IN_PROGRESS");

        assertEquals("OPEN", event.getFromStatus());
        assertEquals("IN_PROGRESS", event.getToStatus());
        verify(complaintDAO).updateStatus(eq("c1"), eq("org1"), eq("IN_PROGRESS"), anyLong());
    }

    @Test
    void addCommentThrowsInternalErrorWhenStatusUpdateFails() {
        when(complaintService.requireComplaint("org1", "c1")).thenReturn(openComplaint());
        when(complaintEventDAO.addEvent(any(ComplaintEvent.class))).thenReturn(true);
        when(complaintDAO.updateStatus(anyString(), anyString(), anyString(), anyLong())).thenReturn(false);

        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> eventService.addComment("org1", "c1", "officer1", "COMPLAINT_OFFICER", "note", true,
                        "IN_PROGRESS"));

        assertEquals("CO-5000", ex.getCode());
    }

    @Test
    void addCommentThrowsInternalErrorWhenAddEventFails() {
        when(complaintService.requireComplaint("org1", "c1")).thenReturn(openComplaint());
        when(complaintEventDAO.addEvent(any(ComplaintEvent.class))).thenReturn(false);

        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> eventService.addComment("org1", "c1", "user1", "USER", "hello", true, null));

        assertEquals("CO-5000", ex.getCode());
    }

    // ---- getTimelineEntry ----

    @Test
    void getTimelineEntryThrows404WhenEventNotFound() {
        when(complaintService.requireComplaint("org1", "c1")).thenReturn(openComplaint());
        when(complaintEventDAO.getEventById("e1", "org1", "c1")).thenReturn(Optional.empty());

        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> eventService.getTimelineEntry("org1", "c1", "e1"));

        assertEquals("CO-4040", ex.getCode());
    }

    @Test
    void getTimelineEntryReturnsMappedDtoWhenFound() {
        when(complaintService.requireComplaint("org1", "c1")).thenReturn(openComplaint());
        ComplaintEvent event = new ComplaintEvent("e1", "org1", "c1", "user1", "USER", true, "hi", null, null, 100L);
        when(complaintEventDAO.getEventById("e1", "org1", "c1")).thenReturn(Optional.of(event));

        ComplaintEvent result = eventService.getTimelineEntry("org1", "c1", "e1");

        assertEquals("COMMENT", result.deriveEntryType());
        assertEquals("user1", result.getActorUserId());
    }

    // ---- updateStatus ----

    @Test
    void updateStatusThrowsWhenToStatusIsBlank() {
        when(complaintService.requireComplaint("org1", "c1")).thenReturn(openComplaint());

        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> eventService.updateStatus("org1", "c1", "user1", "USER", " ", null));

        assertEquals("CO-4002", ex.getCode());
    }

    @Test
    void updateStatusRequiresNoteWhenTransitioningToResolved() {
        Complaint inProgress = new Complaint("c1", "org1", "user1", "CMP-2026-00001", "DATA_BREACH", "CRITICAL",
                "IN_PROGRESS", "desc", 1L, 2L, 3L);
        when(complaintService.requireComplaint("org1", "c1")).thenReturn(inProgress);

        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> eventService.updateStatus("org1", "c1", "officer1", "COMPLAINT_OFFICER", "RESOLVED", " "));

        assertEquals("CO-4002", ex.getCode());
        assertTrue(ex.getDescription().contains("RESOLVED"));
    }

    @Test
    void updateStatusThrowsOnInvalidTransition() {
        when(complaintService.requireComplaint("org1", "c1")).thenReturn(openComplaint());

        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> eventService.updateStatus("org1", "c1", "officer1", "COMPLAINT_OFFICER", "RESOLVED", "note"));

        assertEquals("CO-4090", ex.getCode());
    }

    @Test
    void updateStatusPersistsNewStatusAndRecordsEvent() {
        when(complaintService.requireComplaint("org1", "c1")).thenReturn(openComplaint());
        when(complaintDAO.updateStatus(eq("c1"), eq("org1"), eq("IN_PROGRESS"), anyLong())).thenReturn(true);

        Complaint result =
                eventService.updateStatus("org1", "c1", "officer1", "COMPLAINT_OFFICER", "IN_PROGRESS", null);

        assertEquals("IN_PROGRESS", result.getStatus());
        ArgumentCaptor<ComplaintEvent> captor = ArgumentCaptor.forClass(ComplaintEvent.class);
        verify(complaintEventDAO).addEvent(captor.capture());
        assertEquals("OPEN", captor.getValue().getFromStatus());
        assertEquals("IN_PROGRESS", captor.getValue().getToStatus());
    }

    @Test
    void updateStatusThrowsInternalErrorWhenDaoUpdateFails() {
        when(complaintService.requireComplaint("org1", "c1")).thenReturn(openComplaint());
        when(complaintDAO.updateStatus(anyString(), anyString(), anyString(), anyLong())).thenReturn(false);

        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> eventService.updateStatus("org1", "c1", "officer1", "COMPLAINT_OFFICER", "IN_PROGRESS", null));

        assertEquals("CO-5000", ex.getCode());
        verify(complaintEventDAO, never()).addEvent(any());
    }
}
