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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplaintServiceImplTest {

    @Mock
    private ComplaintDAO complaintDAO;

    private ComplaintServiceImpl complaintService;

    @BeforeEach
    void setUp() {
        complaintService = new ComplaintServiceImpl(complaintDAO);
    }

    @AfterEach
    void tearDown() {
        // createComplaint() derives priority via PriorityMapper's static mapping - make sure a run
        // that mutated it (there isn't one here, but keeps this class order-independent) doesn't leak.
        System.clearProperty("CO_STATUTORY_DUE_PERIOD_DAYS");
    }

    @Test
    void createComplaintThrowsWhenOrgIdIsMissing() {
        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> complaintService.createComplaint(" ", "user1", "DATA_BREACH", "desc"));

        assertEquals("CO-4001", ex.getCode());
        assertEquals(400, ex.getStatusCode());
    }

    @Test
    void createComplaintThrowsWhenUserIdIsMissing() {
        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> complaintService.createComplaint("org1", " ", "DATA_BREACH", "desc"));

        assertEquals("CO-4002", ex.getCode());
        assertEquals(422, ex.getStatusCode());
    }

    @Test
    void createComplaintThrowsWhenSubjectCategoryIsMissing() {
        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> complaintService.createComplaint("org1", "user1", null, "desc"));

        assertEquals("CO-4002", ex.getCode());
    }

    @Test
    void createComplaintThrowsWhenSubjectCategoryIsUnknown() {
        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> complaintService.createComplaint("org1", "user1", "NOT_A_REAL_CATEGORY", "desc"));

        assertEquals("CO-4002", ex.getCode());
        assertTrue(ex.getDescription().contains("NOT_A_REAL_CATEGORY"));
    }

    @Test
    void createComplaintThrowsWhenDescriptionIsMissing() {
        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> complaintService.createComplaint("org1", "user1", "DATA_BREACH", " "));

        assertEquals("CO-4002", ex.getCode());
    }

    @Test
    void createComplaintThrowsWhenDescriptionExceedsMaxLength() {
        String tooLong = "a".repeat(5001);

        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> complaintService.createComplaint("org1", "user1", "DATA_BREACH", tooLong));

        assertEquals("CO-4002", ex.getCode());
    }

    @Test
    void createComplaintPersistsWithDerivedPriorityAndOpenStatus() {
        when(complaintDAO.countByReferenceIdPrefix(eq("org1"), anyString())).thenReturn(0);
        when(complaintDAO.addComplaint(any(Complaint.class))).thenReturn(true);

        Complaint complaint = complaintService.createComplaint("org1", "user1", "DATA_BREACH", "desc  ");

        assertEquals("CRITICAL", complaint.getPriority());
        assertEquals("OPEN", complaint.getStatus());
        assertEquals("desc", complaint.getDescription());
        assertEquals("user1", complaint.getUserId());

        ArgumentCaptor<Complaint> captor = ArgumentCaptor.forClass(Complaint.class);
        verify(complaintDAO).addComplaint(captor.capture());
        assertEquals("org1", captor.getValue().getOrgId());
        assertEquals("OPEN", captor.getValue().getStatus());
        assertTrue(captor.getValue().getStatutoryDueTime() > captor.getValue().getCreatedTime());
    }

    @Test
    void createComplaintThrowsInternalErrorWhenPersistFails() {
        when(complaintDAO.countByReferenceIdPrefix(anyString(), anyString())).thenReturn(0);
        when(complaintDAO.addComplaint(any(Complaint.class))).thenReturn(false);

        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> complaintService.createComplaint("org1", "user1", "DATA_BREACH", "desc"));

        assertEquals("CO-5000", ex.getCode());
        assertEquals(500, ex.getStatusCode());
    }

    @Test
    void requireComplaintThrows404WhenIdOrOrgIsBlank() {
        ComplaintException ex1 = assertThrows(ComplaintException.class,
                () -> complaintService.requireComplaint("org1", " "));
        ComplaintException ex2 = assertThrows(ComplaintException.class,
                () -> complaintService.requireComplaint(" ", "c1"));

        assertEquals("CO-4040", ex1.getCode());
        assertEquals(404, ex1.getStatusCode());
        assertEquals("CO-4040", ex2.getCode());
        verify(complaintDAO, never()).getComplaintById(anyString(), anyString());
    }

    @Test
    void requireComplaintThrows404WhenDaoReturnsEmpty() {
        when(complaintDAO.getComplaintById("c1", "org1")).thenReturn(Optional.empty());

        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> complaintService.requireComplaint("org1", "c1"));

        assertEquals("CO-4040", ex.getCode());
    }

    @Test
    void requireComplaintReturnsDtoWhenFound() {
        Complaint complaint = new Complaint("c1", "org1", "user1", "CMP-2026-00001", "DATA_BREACH", "CRITICAL",
                "OPEN", "desc", 1L, 2L, 3L);
        when(complaintDAO.getComplaintById("c1", "org1")).thenReturn(Optional.of(complaint));

        Complaint result = complaintService.requireComplaint("org1", "c1");

        assertEquals("c1", result.getComplaintId());
        assertEquals("CMP-2026-00001", result.getReferenceId());
    }

    @Test
    void getComplaintDelegatesToRequireComplaint() {
        Complaint complaint = new Complaint("c1", "org1", "user1", "CMP-2026-00001", "DATA_BREACH", "CRITICAL",
                "OPEN", "desc", 1L, 2L, 3L);
        when(complaintDAO.getComplaintById("c1", "org1")).thenReturn(Optional.of(complaint));

        Complaint result = complaintService.getComplaint("org1", "c1");

        assertEquals("c1", result.getComplaintId());
    }

    @Test
    void listComplaintsMapsDaoResultsToDtosAndPassesThroughParams() {
        Complaint c1 = new Complaint("c1", "org1", "user1", "CMP-2026-00001", "DATA_BREACH", "CRITICAL", "OPEN",
                "desc1", 1L, 2L, 3L);
        Complaint c2 = new Complaint("c2", "org1", "user1", "CMP-2026-00002", "OTHER", "LOW", "OPEN", "desc2", 4L,
                5L, 6L);
        int[] totalOut = new int[1];
        when(complaintDAO.listComplaints("org1", "OPEN", null, "user1", 10, 0, "-updatedTime", totalOut))
                .thenReturn(List.of(c1, c2));

        List<Complaint> results =
                complaintService.listComplaints("org1", "OPEN", null, "user1", 10, 0, "-updatedTime", totalOut);

        assertEquals(2, results.size());
        assertEquals("c1", results.get(0).getComplaintId());
        assertEquals("c2", results.get(1).getComplaintId());
    }

    @Test
    void listComplaintsReturnsEmptyListWhenDaoReturnsNothing() {
        int[] totalOut = new int[1];
        when(complaintDAO.listComplaints(anyString(), any(), any(), any(), anyInt(), anyInt(), any(), eq(totalOut)))
                .thenReturn(List.of());

        List<Complaint> results =
                complaintService.listComplaints("org1", null, null, null, 10, 0, null, totalOut);

        assertTrue(results.isEmpty());
        verify(complaintDAO, times(1)).listComplaints(anyString(), any(), any(), any(), anyInt(), anyInt(), any(),
                eq(totalOut));
    }
}
