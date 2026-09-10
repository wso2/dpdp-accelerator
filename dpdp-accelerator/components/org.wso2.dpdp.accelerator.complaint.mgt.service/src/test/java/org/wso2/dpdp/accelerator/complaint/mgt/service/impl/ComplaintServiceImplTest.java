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
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationServiceImpl;
import org.wso2.dpdp.accelerator.common.persistence.JDBCPersistenceManager;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintEventDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.exception.DuplicateReferenceIdException;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintQueueStats;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintCreateResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintQueueStatsResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintException;
import org.wso2.dpdp.accelerator.complaint.mgt.service.internal.ComplaintServiceDataHolder;
import org.wso2.dpdp.accelerator.complaint.mgt.service.notification.NotificationClient;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;
import static org.testng.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ComplaintServiceImplTest {

    @Mock
    private ComplaintDAO complaintDAO;
    @Mock
    private ComplaintEventDAO complaintEventDAO;
    @Mock
    private NotificationClient notificationClient;

    private ComplaintServiceImpl complaintService;

    // Counts every real JDBC connection handed out - see #145: a convenience overload that opened
    // its own connection silently doubled the connections a single service call acquired. Tests
    // below assert against this to pin that property, not just count DAO calls.
    private static final AtomicInteger CONNECTION_COUNT = new AtomicInteger();

    /**
     * Delegates to a real H2 {@link JdbcDataSource}, incrementing {@link #CONNECTION_COUNT} on
     * every borrow. Composition, not inheritance - {@code JdbcDataSource} is {@code final}.
     */
    private static final class CountingDataSource implements javax.sql.DataSource {

        private final JdbcDataSource delegate = new JdbcDataSource();

        void setURL(String url) {
            delegate.setURL(url);
        }

        void setUser(String user) {
            delegate.setUser(user);
        }

        void setPassword(String password) {
            delegate.setPassword(password);
        }

        @Override
        public Connection getConnection() throws SQLException {
            CONNECTION_COUNT.incrementAndGet();
            return delegate.getConnection();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            CONNECTION_COUNT.incrementAndGet();
            return delegate.getConnection(username, password);
        }

        @Override
        public java.io.PrintWriter getLogWriter() throws SQLException {
            return delegate.getLogWriter();
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) throws SQLException {
            delegate.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            delegate.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return delegate.getLoginTimeout();
        }

        @Override
        public java.util.logging.Logger getParentLogger() throws java.sql.SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return delegate.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return delegate.isWrapperFor(iface);
        }
    }

    @BeforeClass
    static void pointPersistenceManagerAtAnInMemoryDatabase() throws Exception {
        CountingDataSource dataSource = new CountingDataSource();
        dataSource.setURL("jdbc:h2:mem:complaint_service_test;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        setManagerDataSource(dataSource);

        // Normally bound by ComplaintServiceComponent's OSGi @Reference; StatutoryDuePeriodPolicy
        // reads it via ComplaintServiceDataHolder, so tests running outside a live Carbon
        // environment must seed it themselves.
        ComplaintServiceDataHolder.getInstance().setConfigurationService(new DPDPConfigurationServiceImpl());
    }

    @AfterClass
    static void clearPersistenceManagerDataSource() throws Exception {
        setManagerDataSource(null);
        ComplaintServiceDataHolder.getInstance().setConfigurationService(null);
    }

    private static void setManagerDataSource(Object dataSource) throws Exception {
        Field field = JDBCPersistenceManager.class.getDeclaredField("dataSource");
        field.setAccessible(true);
        field.set(null, dataSource);
    }

    @BeforeMethod
    void setUp() {
        MockitoAnnotations.openMocks(this);
        complaintService = new ComplaintServiceImpl(complaintDAO, complaintEventDAO, notificationClient);
        CONNECTION_COUNT.set(0);
    }


    @Test
    void createComplaintThrowsWhenOrgIdIsMissing() {
        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> complaintService.createComplaint(" ", "user1", "User One", "DATA_BREACH", "desc"));

        assertEquals("CO-4001", ex.getCode());
        assertEquals(400, ex.getStatusCode());
    }

    @Test
    void createComplaintThrowsWhenUserIdIsMissing() {
        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> complaintService.createComplaint("org1", " ", "User One", "DATA_BREACH", "desc"));

        assertEquals("CO-4002", ex.getCode());
        assertEquals(422, ex.getStatusCode());
    }

    @Test
    void createComplaintThrowsWhenSubjectCategoryIsMissing() {
        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> complaintService.createComplaint("org1", "user1", "User One", null, "desc"));

        assertEquals("CO-4002", ex.getCode());
    }

    @Test
    void createComplaintThrowsWhenSubjectCategoryIsUnknown() {
        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> complaintService.createComplaint("org1", "user1", "User One", "NOT_A_REAL_CATEGORY", "desc"));

        assertEquals("CO-4002", ex.getCode());
        assertTrue(ex.getDescription().contains("NOT_A_REAL_CATEGORY"));
    }

    @Test
    void createComplaintThrowsWhenDescriptionIsMissing() {
        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> complaintService.createComplaint("org1", "user1", "User One", "DATA_BREACH", " "));

        assertEquals("CO-4002", ex.getCode());
    }

    @Test
    void createComplaintThrowsWhenDescriptionExceedsMaxLength() {
        String tooLong = "a".repeat(5001);

        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> complaintService.createComplaint("org1", "user1", "User One", "DATA_BREACH", tooLong));

        assertEquals("CO-4002", ex.getCode());
    }

    @Test
    void createComplaintPersistsWithDerivedPriorityAndOpenStatus() throws Exception {
        when(complaintDAO.countByReferenceIdPrefix(any(Connection.class), eq("org1"), anyString())).thenReturn(0);
        when(complaintDAO.addComplaint(any(Connection.class), any(Complaint.class))).thenReturn(true);

        ComplaintCreateResponseDTO complaint =
                complaintService.createComplaint("org1", "user1", "User One", "DATA_BREACH", "desc  ");

        assertEquals("CRITICAL", complaint.getPriority());
        assertEquals("OPEN", complaint.getStatus());
        assertEquals("desc", complaint.getDescription());
        assertEquals("user1", complaint.getUserId());

        ArgumentCaptor<Complaint> captor = ArgumentCaptor.forClass(Complaint.class);
        verify(complaintDAO).addComplaint(any(Connection.class), captor.capture());
        assertEquals("org1", captor.getValue().getOrgId());
        assertEquals("OPEN", captor.getValue().getStatus());
        assertEquals("User One", captor.getValue().getUserName());
        assertTrue(captor.getValue().getStatutoryDueTime() > captor.getValue().getCreatedTime());
        verify(notificationClient).notifyComplaintCreated(captor.getValue());
    }

    @Test
    void createComplaintThrowsInternalErrorWhenPersistFails() throws Exception {
        when(complaintDAO.countByReferenceIdPrefix(any(Connection.class), anyString(), anyString())).thenReturn(0);
        when(complaintDAO.addComplaint(any(Connection.class), any(Complaint.class))).thenReturn(false);

        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> complaintService.createComplaint("org1", "user1", "User One", "DATA_BREACH", "desc"));

        assertEquals("CO-5000", ex.getCode());
        assertEquals(500, ex.getStatusCode());
        verify(notificationClient, never()).notifyComplaintCreated(any());
    }

    @Test
    void createComplaintRetriesWithAFreshReferenceIdOnCollisionAndSucceeds() throws Exception {
        when(complaintDAO.countByReferenceIdPrefix(any(Connection.class), eq("org1"), anyString())).thenReturn(0);
        when(complaintDAO.addComplaint(any(Connection.class), any(Complaint.class)))
                .thenThrow(new DuplicateReferenceIdException(new SQLIntegrityConstraintViolationException("dup")))
                .thenReturn(true);

        ComplaintCreateResponseDTO complaint =
                complaintService.createComplaint("org1", "user1", "User One", "DATA_BREACH", "desc");

        assertEquals("OPEN", complaint.getStatus());
        verify(complaintDAO, times(2)).addComplaint(any(Connection.class), any(Complaint.class));
    }

    @Test
    void createComplaintGivesUpAfterExhaustingReferenceIdRetries() throws Exception {
        when(complaintDAO.countByReferenceIdPrefix(any(Connection.class), eq("org1"), anyString())).thenReturn(0);
        when(complaintDAO.addComplaint(any(Connection.class), any(Complaint.class)))
                .thenThrow(new DuplicateReferenceIdException(new SQLIntegrityConstraintViolationException("dup")));

        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> complaintService.createComplaint("org1", "user1", "User One", "DATA_BREACH", "desc"));

        assertEquals("CO-5000", ex.getCode());
        assertTrue(ex.getCause() instanceof DuplicateReferenceIdException);
        verify(complaintDAO, times(3)).addComplaint(any(Connection.class), any(Complaint.class));
    }

    @Test
    void createComplaintForOfficerIntakeRecordsAuditEventAtomically() throws Exception {
        when(complaintDAO.countByReferenceIdPrefix(any(Connection.class), eq("org1"), anyString())).thenReturn(0);
        when(complaintDAO.addComplaint(any(Connection.class), any(Complaint.class))).thenReturn(true);
        when(complaintEventDAO.addEvent(any(Connection.class), any(ComplaintEvent.class))).thenReturn(true);

        ComplaintCreateResponseDTO complaint = complaintService.createComplaint("org1", "user1", null,
                "DATA_BREACH", "desc", "officer1", "COMPLAINT_OFFICER");

        assertEquals("OPEN", complaint.getStatus());
        ArgumentCaptor<ComplaintEvent> captor = ArgumentCaptor.forClass(ComplaintEvent.class);
        verify(complaintEventDAO).addEvent(any(Connection.class), captor.capture());
        assertEquals("officer1", captor.getValue().getActorUserId());
        assertEquals("COMPLAINT_OFFICER", captor.getValue().getActorRole());
        assertEquals("OPEN", captor.getValue().getToStatus());
        assertEquals(complaint.getId(), captor.getValue().getComplaintId());
        assertEquals(complaint.getId(), captor.getValue().getComplaintId());
        ArgumentCaptor<Complaint> notifiedComplaintCaptor = ArgumentCaptor.forClass(Complaint.class);
        verify(notificationClient).notifyComplaintCreated(notifiedComplaintCaptor.capture());
        assertEquals(complaint.getId(), notifiedComplaintCaptor.getValue().getComplaintId());
    }

    @Test
    void createComplaintThrowsWhenIntakeActorRoleIsInvalid() {
        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> complaintService.createComplaint("org1", "user1", null, "DATA_BREACH", "desc", "officer1",
                        "USER"));

        assertEquals("CO-4002", ex.getCode());
    }

    @Test
    void createComplaintWithoutActorNeverTouchesComplaintEventDao() throws Exception {
        when(complaintDAO.countByReferenceIdPrefix(any(Connection.class), eq("org1"), anyString())).thenReturn(0);
        when(complaintDAO.addComplaint(any(Connection.class), any(Complaint.class))).thenReturn(true);

        complaintService.createComplaint("org1", "user1", null, "DATA_BREACH", "desc");

        verify(complaintEventDAO, never()).addEvent(any(Connection.class), any());
    }

    @Test
    void requireComplaintThrows404WhenIdOrOrgIsBlank() {
        ComplaintException ex1 = expectThrows(ComplaintException.class,
                () -> complaintService.requireComplaint("org1", " "));
        ComplaintException ex2 = expectThrows(ComplaintException.class,
                () -> complaintService.requireComplaint(" ", "c1"));

        assertEquals("CO-4040", ex1.getCode());
        assertEquals(404, ex1.getStatusCode());
        assertEquals("CO-4040", ex2.getCode());
        verify(complaintDAO, never()).getComplaintById(any(Connection.class), anyString(), anyString());
    }

    @Test
    void requireComplaintThrows404WhenDaoReturnsEmpty() {
        when(complaintDAO.getComplaintById(any(Connection.class), eq("c1"), eq("org1"))).thenReturn(Optional.empty());

        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> complaintService.requireComplaint("org1", "c1"));

        assertEquals("CO-4040", ex.getCode());
    }

    @Test
    void requireComplaintReturnsDtoWhenFound() {
        Complaint complaint = new Complaint("c1", "org1", "user1", "User One", "CMP-2026-00001", "DATA_BREACH",
                "CRITICAL", "OPEN", "desc", 1L, 2L, 3L);
        when(complaintDAO.getComplaintById(any(Connection.class), eq("c1"), eq("org1")))
                .thenReturn(Optional.of(complaint));

        Complaint result = complaintService.requireComplaint("org1", "c1");

        assertEquals("c1", result.getComplaintId());
        assertEquals("CMP-2026-00001", result.getReferenceId());
    }

    @Test
    void getComplaintDelegatesToRequireComplaint() {
        Complaint complaint = new Complaint("c1", "org1", "user1", "User One", "CMP-2026-00001", "DATA_BREACH",
                "CRITICAL", "OPEN", "desc", 1L, 2L, 3L);
        when(complaintDAO.getComplaintById(any(Connection.class), eq("c1"), eq("org1")))
                .thenReturn(Optional.of(complaint));

        Complaint result = complaintService.getComplaint("org1", "c1");

        assertEquals("c1", result.getComplaintId());
    }

    @Test
    void listComplaintsMapsDaoResultsToDtosAndPassesThroughParams() {
        Complaint c1 = new Complaint("c1", "org1", "user1", "User One", "CMP-2026-00001", "DATA_BREACH", "CRITICAL",
                "OPEN", "desc1", 1L, 2L, 3L);
        Complaint c2 = new Complaint("c2", "org1", "user1", "User One", "CMP-2026-00002", "OTHER", "LOW", "OPEN",
                "desc2", 4L, 5L, 6L);
        int[] totalOut = new int[1];
        when(complaintDAO.listComplaints(any(Connection.class), eq("org1"), eq("OPEN"), isNull(), eq("user1"), eq(10),
                        eq(0), eq("-updatedTime"), eq(totalOut)))
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
        when(complaintDAO.listComplaints(any(Connection.class), anyString(), any(), any(), any(), anyInt(), anyInt(),
                any(), eq(totalOut)))
                .thenReturn(List.of());

        List<Complaint> results =
                complaintService.listComplaints("org1", null, null, null, 10, 0, null, totalOut);

        assertTrue(results.isEmpty());
        verify(complaintDAO, times(1)).listComplaints(any(Connection.class), anyString(), any(), any(), any(),
                anyInt(), anyInt(), any(), eq(totalOut));
    }

    @Test
    void listComplaintsThrowsWhenStatusFilterIsNotARecognizedEnumValue() {
        int[] totalOut = new int[1];

        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> complaintService.listComplaints("org1", "OPEN_TYPO", null, null, 10, 0, null, totalOut));

        assertEquals("CO-4002", ex.getCode());
        verifyNoInteractions(complaintDAO);
    }

    @Test
    void listComplaintsThrowsWhenPriorityFilterIsNotARecognizedEnumValue() {
        int[] totalOut = new int[1];

        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> complaintService.listComplaints("org1", null, "URGENT", null, 10, 0, null, totalOut));

        assertEquals("CO-4002", ex.getCode());
        verifyNoInteractions(complaintDAO);
    }

    @Test
    void getQueueStatsDelegatesToDao() {
        ComplaintQueueStats stats = new ComplaintQueueStats(3, 1, 2, 1);
        when(complaintDAO.getQueueStats(any(Connection.class), eq("org1"), anyLong())).thenReturn(stats);

        ComplaintQueueStatsResponseDTO result = complaintService.getQueueStats("org1");

        assertEquals(stats.getOpenCount(), result.getOpenCount());
        assertEquals(stats.getAwaitingInternalReviewCount(), result.getAwaitingInternalReviewCount());
        assertEquals(stats.getResolvedCount(), result.getResolvedCount());
        assertEquals(stats.getSlaBreachedCount(), result.getSlaBreachedCount());
        verify(complaintDAO).getQueueStats(any(Connection.class), eq("org1"), anyLong());
    }

    // ---- one connection per call - the actual property #145 was about ----

    @Test
    void createComplaintAcquiresExactlyOneConnection() {
        when(complaintDAO.countByReferenceIdPrefix(any(Connection.class), eq("org1"), anyString())).thenReturn(0);
        when(complaintDAO.addComplaint(any(Connection.class), any(Complaint.class))).thenReturn(true);

        complaintService.createComplaint("org1", "user1", "User One", "DATA_BREACH", "desc");

        // Before the fix, ReferenceIdGenerator opened its own connection separately from the
        // complaint insert - two connections for one logical create. Now the count and the insert
        // share the single connection this transaction acquires.
        assertEquals(1, CONNECTION_COUNT.get());
    }

    @Test
    void requireComplaintAcquiresExactlyOneConnection() {
        Complaint complaint = new Complaint("c1", "org1", "user1", "User One", "CMP-2026-00001", "DATA_BREACH",
                "CRITICAL", "OPEN", "desc", 1L, 2L, 3L);
        when(complaintDAO.getComplaintById(any(Connection.class), eq("c1"), eq("org1")))
                .thenReturn(Optional.of(complaint));

        complaintService.requireComplaint("org1", "c1");

        assertEquals(1, CONNECTION_COUNT.get());
    }

    @Test
    void listComplaintsAcquiresExactlyOneConnection() {
        int[] totalOut = new int[1];
        when(complaintDAO.listComplaints(any(Connection.class), anyString(), any(), any(), any(), anyInt(), anyInt(),
                any(), eq(totalOut))).thenReturn(List.of());

        complaintService.listComplaints("org1", null, null, null, 10, 0, null, totalOut);

        assertEquals(1, CONNECTION_COUNT.get());
    }
}
