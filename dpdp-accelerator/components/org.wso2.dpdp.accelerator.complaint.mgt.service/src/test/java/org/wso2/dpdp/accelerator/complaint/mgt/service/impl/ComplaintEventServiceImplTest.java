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

import org.h2.jdbcx.JdbcDataSource;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.wso2.dpdp.accelerator.common.persistence.JDBCPersistenceManager;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintEventDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintCommentCreateResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintStatusUpdateResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintException;
import org.wso2.dpdp.accelerator.complaint.mgt.service.notification.NotificationClient;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;
import static org.testng.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ComplaintEventServiceImplTest {

    @Mock
    private ComplaintEventDAO complaintEventDAO;
    @Mock
    private ComplaintDAO complaintDAO;
    @Mock
    private NotificationClient notificationClient;

    private ComplaintEventServiceImpl eventService;

    @BeforeClass
    static void pointPersistenceManagerAtAnInMemoryDatabase() throws Exception {
        // Every method here now runs its DAO calls through DatabaseUtils#executeInTransaction,
        // which opens a real Connection - complaintEventDAO/complaintDAO are still plain Mockito
        // mocks, so no real SQL executes against it, but
        // JDBCPersistenceManager.getConnection() itself needs somewhere real to connect. Same
        // reflection-based DataSource injection as H2TestDbSupport in the dao module, since
        // JDBCPersistenceManager only resolves its DataSource via JNDI.
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:complaint_event_service_test;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        setManagerDataSource(dataSource);
    }

    @AfterClass
    static void clearPersistenceManagerDataSource() throws Exception {
        setManagerDataSource(null);
    }

    private static void setManagerDataSource(Object dataSource) throws Exception {
        Field field = JDBCPersistenceManager.class.getDeclaredField("dataSource");
        field.setAccessible(true);
        field.set(null, dataSource);
    }

    @BeforeMethod
    void setUp() {
        MockitoAnnotations.openMocks(this);
        eventService = new ComplaintEventServiceImpl(complaintEventDAO, complaintDAO, notificationClient);
    }

    private Complaint openComplaint() {
        return new Complaint("c1", "org1", "user1", "User One", "CMP-2026-00001", "DATA_BREACH", "CRITICAL", "OPEN",
                "desc", 1L, 2L, 3L);
    }

    // ---- getTimeline ----

    @Test
    void getTimelineRequiresComplaintToExistFirst() {
        when(complaintDAO.getComplaintById(any(Connection.class), eq("c1"), eq("org1"))).thenReturn(Optional.empty());

        expectThrows(ComplaintException.class,
                () -> eventService.getTimeline("org1", "c1", null, null, null, "asc", 10, 0, new int[1]));

        verifyNoInteractions(complaintEventDAO);
    }

    @Test
    void getTimelineMapsEventsToDtos() {
        when(complaintDAO.getComplaintById(any(Connection.class), eq("c1"), eq("org1"))).thenReturn(Optional.of(openComplaint()));
        ComplaintEvent statusChange = new ComplaintEvent("e1", "org1", "c1", "user1", "User One", "USER", true,
                "note", "OPEN", "IN_PROGRESS", 100L);
        int[] totalOut = new int[1];
        when(complaintEventDAO.listEvents(any(Connection.class), eq("org1"), eq("c1"), isNull(), isNull(), isNull(),
                eq("asc"), eq(10), eq(0), eq(totalOut)))
                .thenReturn(List.of(statusChange));

        List<ComplaintEvent> entries =
                eventService.getTimeline("org1", "c1", null, null, null, "asc", 10, 0, totalOut);

        assertEquals(1, entries.size());
        assertEquals("STATUS_CHANGE", entries.get(0).deriveEntryType());
        assertEquals("OPEN", entries.get(0).getFromStatus());
        assertEquals("IN_PROGRESS", entries.get(0).getToStatus());
    }

    @Test
    void getTimelinePassesIsPublicFilterToDao() {
        when(complaintDAO.getComplaintById(any(Connection.class), eq("c1"), eq("org1"))).thenReturn(Optional.of(openComplaint()));
        int[] totalOut = new int[1];
        when(complaintEventDAO.listEvents(any(Connection.class), eq("org1"), eq("c1"), isNull(), isNull(), eq(false),
                eq("asc"), eq(10), eq(0), eq(totalOut)))
                .thenReturn(List.of());

        eventService.getTimeline("org1", "c1", null, null, false, "asc", 10, 0, totalOut);

        verify(complaintEventDAO).listEvents(any(Connection.class), eq("org1"), eq("c1"), isNull(), isNull(),
                eq(false), eq("asc"), eq(10), eq(0), eq(totalOut));
    }

    @Test
    void getTimelinePassesUntilFilterToDao() {
        when(complaintDAO.getComplaintById(any(Connection.class), eq("c1"), eq("org1"))).thenReturn(Optional.of(openComplaint()));
        int[] totalOut = new int[1];
        when(complaintEventDAO.listEvents(any(Connection.class), eq("org1"), eq("c1"), isNull(), eq(500L), isNull(),
                eq("asc"), eq(10), eq(0), eq(totalOut)))
                .thenReturn(List.of());

        eventService.getTimeline("org1", "c1", null, 500L, null, "asc", 10, 0, totalOut);

        verify(complaintEventDAO).listEvents(any(Connection.class), eq("org1"), eq("c1"), isNull(), eq(500L),
                isNull(), eq("asc"), eq(10), eq(0), eq(totalOut));
    }

    // ---- addComment ----

    @Test
    void addCommentThrowsWhenMessageIsBlank() {
        when(complaintDAO.getComplaintById(any(Connection.class), eq("c1"), eq("org1"))).thenReturn(Optional.of(openComplaint()));

        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> eventService.addComment("org1", "c1", "user1", "User One", "USER", " ", true, null));

        assertEquals("CO-4002", ex.getCode());
    }

    @Test
    void addCommentThrowsWhenMessageExceedsMaxLength() {
        when(complaintDAO.getComplaintById(any(Connection.class), eq("c1"), eq("org1"))).thenReturn(Optional.of(openComplaint()));
        String tooLong = "a".repeat(5001);

        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> eventService.addComment("org1", "c1", "user1", "User One", "USER", tooLong, true, null));

        assertEquals("CO-4002", ex.getCode());
    }

    @Test
    void addCommentAllowsMessageAtExactlyMaxLength() throws Exception {
        Complaint complaint = openComplaint();
        when(complaintDAO.getComplaintById(any(Connection.class), eq("c1"), eq("org1"))).thenReturn(Optional.of(complaint));
        when(complaintEventDAO.addEvent(any(Connection.class), any(ComplaintEvent.class))).thenReturn(true);
        String atLimit = "a".repeat(5000);

        ComplaintCommentCreateResponseDTO event =
                eventService.addComment("org1", "c1", "user1", "User One", "USER", atLimit, true, null);

        assertEquals(atLimit, event.getMessage());
        ArgumentCaptor<ComplaintEvent> notifiedEventCaptor = ArgumentCaptor.forClass(ComplaintEvent.class);
        verify(notificationClient).notifyCommentAdded(eq(complaint), notifiedEventCaptor.capture());
        assertEquals(event.getId(), notifiedEventCaptor.getValue().getComplaintEventId());
    }

    @Test
    void addCommentThrowsWhenActorUserIdIsBlank() {
        when(complaintDAO.getComplaintById(any(Connection.class), eq("c1"), eq("org1"))).thenReturn(Optional.of(openComplaint()));

        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> eventService.addComment("org1", "c1", " ", "User One", "USER", "hello", true, null));

        assertEquals("CO-4002", ex.getCode());
    }

    @Test
    void addCommentThrowsWhenActorRoleIsInvalid() {
        when(complaintDAO.getComplaintById(any(Connection.class), eq("c1"), eq("org1"))).thenReturn(Optional.of(openComplaint()));

        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> eventService.addComment("org1", "c1", "user1", "User One", "SYSTEM", "hello", true, null));

        assertEquals("CO-4002", ex.getCode());
    }

    @Test
    void addCommentThrowsForbiddenWhenUserTriesToSetIsPublicFalse() {
        when(complaintDAO.getComplaintById(any(Connection.class), eq("c1"), eq("org1"))).thenReturn(Optional.of(openComplaint()));

        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> eventService.addComment("org1", "c1", "user1", "User One", "USER", "hello", false, null));

        assertEquals("CO-4030", ex.getCode());
        assertEquals(403, ex.getStatusCode());
    }

    @Test
    void addCommentAllowsComplaintOfficerToSetIsPublicFalse() throws Exception {
        when(complaintDAO.getComplaintById(any(Connection.class), eq("c1"), eq("org1"))).thenReturn(Optional.of(openComplaint()));
        when(complaintEventDAO.addEvent(any(Connection.class), any(ComplaintEvent.class))).thenReturn(true);

        ComplaintCommentCreateResponseDTO event = eventService.addComment("org1", "c1", "officer1", "Officer One",
                "COMPLAINT_OFFICER", "internal note", false, null);

        assertEquals(false, event.isPublic());
        assertEquals("internal note", event.getMessage());
        // An internal note is never shown to the citizen in the timeline - notifying them about it
        // would leak its existence.
        verify(notificationClient, never()).notifyCommentAdded(any(), any());
    }

    @Test
    void addCommentThrowsOnInvalidStatusTransition() throws Exception {
        when(complaintDAO.getComplaintById(any(Connection.class), eq("c1"), eq("org1"))).thenReturn(Optional.of(openComplaint()));

        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> eventService.addComment("org1", "c1", "officer1", "Officer One", "COMPLAINT_OFFICER", "note",
                        true, "RESOLVED"));

        assertEquals("CO-4090", ex.getCode());
        assertEquals(409, ex.getStatusCode());
        verify(complaintEventDAO, never()).addEvent(any(), any());
    }

    @Test
    void addCommentWithValidToStatusUpdatesComplaintStatus() throws Exception {
        Complaint complaint = openComplaint();
        when(complaintDAO.getComplaintById(any(Connection.class), eq("c1"), eq("org1"))).thenReturn(Optional.of(complaint));
        when(complaintEventDAO.addEvent(any(Connection.class), any(ComplaintEvent.class))).thenReturn(true);
        when(complaintDAO.updateStatus(any(Connection.class), eq("c1"), eq("org1"), eq("IN_PROGRESS"), anyLong()))
                .thenReturn(true);

        ComplaintCommentCreateResponseDTO event = eventService.addComment("org1", "c1", "officer1", "Officer One",
                "COMPLAINT_OFFICER", "note", true, "IN_PROGRESS");

        assertEquals("OPEN", event.getFromStatus());
        assertEquals("IN_PROGRESS", event.getToStatus());
        verify(complaintDAO).updateStatus(any(Connection.class), eq("c1"), eq("org1"), eq("IN_PROGRESS"), anyLong());
        // The complaint was fetched with its pre-transition status ("OPEN") - the notification
        // must not carry that stale value now that the transition has actually landed.
        assertEquals("IN_PROGRESS", complaint.getStatus());
        ArgumentCaptor<ComplaintEvent> notifiedEventCaptor = ArgumentCaptor.forClass(ComplaintEvent.class);
        verify(notificationClient).notifyCommentAdded(eq(complaint), notifiedEventCaptor.capture());
        assertEquals(event.getId(), notifiedEventCaptor.getValue().getComplaintEventId());
    }

    @Test
    void addCommentThrowsInternalErrorWhenStatusUpdateFails() throws Exception {
        when(complaintDAO.getComplaintById(any(Connection.class), eq("c1"), eq("org1"))).thenReturn(Optional.of(openComplaint()));
        when(complaintEventDAO.addEvent(any(Connection.class), any(ComplaintEvent.class))).thenReturn(true);
        when(complaintDAO.updateStatus(any(Connection.class), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(false);

        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> eventService.addComment("org1", "c1", "officer1", "Officer One", "COMPLAINT_OFFICER", "note",
                        true, "IN_PROGRESS"));

        assertEquals("CO-5000", ex.getCode());
    }

    @Test
    void addCommentThrowsInternalErrorWhenAddEventFails() throws Exception {
        when(complaintDAO.getComplaintById(any(Connection.class), eq("c1"), eq("org1"))).thenReturn(Optional.of(openComplaint()));
        when(complaintEventDAO.addEvent(any(Connection.class), any(ComplaintEvent.class))).thenReturn(false);

        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> eventService.addComment("org1", "c1", "user1", "User One", "USER", "hello", true, null));

        assertEquals("CO-5000", ex.getCode());
        verify(notificationClient, never()).notifyCommentAdded(any(), any());
    }

    // ---- getTimelineEntry ----

    @Test
    void getTimelineEntryThrows404WhenEventNotFound() {
        when(complaintDAO.getComplaintById(any(Connection.class), eq("c1"), eq("org1"))).thenReturn(Optional.of(openComplaint()));
        when(complaintEventDAO.getEventById(any(Connection.class), eq("e1"), eq("org1"), eq("c1")))
                .thenReturn(Optional.empty());

        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> eventService.getTimelineEntry("org1", "c1", "e1"));

        assertEquals("CO-4040", ex.getCode());
    }

    @Test
    void getTimelineEntryReturnsMappedDtoWhenFound() {
        when(complaintDAO.getComplaintById(any(Connection.class), eq("c1"), eq("org1"))).thenReturn(Optional.of(openComplaint()));
        ComplaintEvent event =
                new ComplaintEvent("e1", "org1", "c1", "user1", "User One", "USER", true, "hi", null, null, 100L);
        when(complaintEventDAO.getEventById(any(Connection.class), eq("e1"), eq("org1"), eq("c1")))
                .thenReturn(Optional.of(event));

        ComplaintEvent result = eventService.getTimelineEntry("org1", "c1", "e1");

        assertEquals("COMMENT", result.deriveEntryType());
        assertEquals("user1", result.getActorUserId());
    }

    // ---- updateStatus ----

    @Test
    void updateStatusThrowsWhenActorUserIdIsBlank() {
        when(complaintDAO.getComplaintById(any(Connection.class), eq("c1"), eq("org1"))).thenReturn(Optional.of(openComplaint()));

        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> eventService.updateStatus("org1", "c1", " ", "User One", "USER", "IN_PROGRESS", null));

        assertEquals("CO-4002", ex.getCode());
    }

    @Test
    void updateStatusThrowsWhenActorRoleIsInvalid() {
        when(complaintDAO.getComplaintById(any(Connection.class), eq("c1"), eq("org1"))).thenReturn(Optional.of(openComplaint()));

        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> eventService.updateStatus("org1", "c1", "user1", "User One", "SYSTEM", "IN_PROGRESS", null));

        assertEquals("CO-4002", ex.getCode());
    }

    @Test
    void updateStatusThrowsWhenToStatusIsBlank() {
        when(complaintDAO.getComplaintById(any(Connection.class), eq("c1"), eq("org1"))).thenReturn(Optional.of(openComplaint()));

        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> eventService.updateStatus("org1", "c1", "user1", "User One", "USER", " ", null));

        assertEquals("CO-4002", ex.getCode());
    }

    @Test
    void updateStatusRequiresNoteWhenTransitioningToResolved() {
        Complaint inProgress = new Complaint("c1", "org1", "user1", "User One", "CMP-2026-00001", "DATA_BREACH",
                "CRITICAL", "IN_PROGRESS", "desc", 1L, 2L, 3L);
        when(complaintDAO.getComplaintById(any(Connection.class), eq("c1"), eq("org1"))).thenReturn(Optional.of(inProgress));

        ComplaintException ex = expectThrows(ComplaintException.class, () -> eventService.updateStatus("org1", "c1",
                "officer1", "Officer One", "COMPLAINT_OFFICER", "RESOLVED", " "));

        assertEquals("CO-4002", ex.getCode());
        assertTrue(ex.getDescription().contains("RESOLVED"));
    }

    @Test
    void updateStatusThrowsOnInvalidTransition() {
        when(complaintDAO.getComplaintById(any(Connection.class), eq("c1"), eq("org1"))).thenReturn(Optional.of(openComplaint()));

        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> eventService.updateStatus("org1", "c1", "officer1", "Officer One", "COMPLAINT_OFFICER",
                        "RESOLVED", "note"));

        assertEquals("CO-4090", ex.getCode());
    }

    @Test
    void updateStatusPersistsNewStatusAndRecordsEvent() throws Exception {
        when(complaintDAO.getComplaintById(any(Connection.class), eq("c1"), eq("org1"))).thenReturn(Optional.of(openComplaint()));
        when(complaintDAO.updateStatus(any(Connection.class), eq("c1"), eq("org1"), eq("IN_PROGRESS"), anyLong()))
                .thenReturn(true);
        when(complaintEventDAO.addEvent(any(Connection.class), any(ComplaintEvent.class))).thenReturn(true);

        ComplaintStatusUpdateResponseDTO result = eventService.updateStatus("org1", "c1", "officer1", "Officer One",
                "COMPLAINT_OFFICER", "IN_PROGRESS", null);

        assertEquals("IN_PROGRESS", result.getToStatus());
        ArgumentCaptor<ComplaintEvent> captor = ArgumentCaptor.forClass(ComplaintEvent.class);
        verify(complaintEventDAO).addEvent(any(Connection.class), captor.capture());
        assertEquals("OPEN", captor.getValue().getFromStatus());
        assertEquals("IN_PROGRESS", captor.getValue().getToStatus());
    }

    @Test
    void updateStatusThrowsInternalErrorWhenDaoUpdateFails() throws Exception {
        when(complaintDAO.getComplaintById(any(Connection.class), eq("c1"), eq("org1"))).thenReturn(Optional.of(openComplaint()));
        when(complaintDAO.updateStatus(any(Connection.class), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(false);

        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> eventService.updateStatus("org1", "c1", "officer1", "Officer One", "COMPLAINT_OFFICER",
                        "IN_PROGRESS", null));

        assertEquals("CO-5000", ex.getCode());
        verify(complaintEventDAO, never()).addEvent(any(Connection.class), any());
    }
}
