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

package org.wso2.dpdp.accelerator.complaint.mgt.dao.impl;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.common.util.DatabaseUtils;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.exception.ComplaintDAOException;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.exception.DuplicateReferenceIdException;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintQueueStats;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.util.H2TestDbSupport;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.expectThrows;
import static org.testng.Assert.assertTrue;

class ComplaintDAOImplTest {

    private static final String CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS COMPLAINT (" +
            "COMPLAINT_ID VARCHAR(64) PRIMARY KEY, " +
            "ORG_ID VARCHAR(64) NOT NULL, " +
            "USER_ID VARCHAR(64) NOT NULL, " +
            "USER_NAME VARCHAR(64), " +
            "REFERENCE_ID VARCHAR(64), " +
            "CATEGORY VARCHAR(64), " +
            "PRIORITY VARCHAR(32), " +
            "STATUS VARCHAR(32), " +
            "DESCRIPTION VARCHAR(4000), " +
            "CREATED_TIME BIGINT, " +
            "UPDATED_TIME BIGINT, " +
            "STATUTORY_DUE_TIME BIGINT, " +
            // Mirrors UQ_COMPLAINT_REFERENCE in mysql.sql - without it here, addComplaintThrows
            // DuplicateReferenceIdExceptionOnReferenceIdCollision below would pass for the wrong
            // reason (or not at all), since H2 would happily accept the duplicate row.
            "CONSTRAINT UQ_COMPLAINT_REFERENCE UNIQUE (ORG_ID, REFERENCE_ID))";

    private final ComplaintDAOImpl dao = new ComplaintDAOImpl();

    @BeforeClass
    static void setUpDatabase() throws SQLException {
        H2TestDbSupport.setUpDatabase("complaint_dao_test", CREATE_TABLE);
    }

    @AfterClass
    static void tearDownDatabase() {
        H2TestDbSupport.tearDownDatabase();
    }

    @BeforeMethod
    void clearTable() throws SQLException {
        Connection conn = DatabaseUtils.getDBConnection();
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM COMPLAINT");
            DatabaseUtils.commitTransaction(conn);
        } finally {
            DatabaseUtils.closeConnection(conn);
        }
    }

    private Complaint sampleComplaint(String id, String orgId, String status, String priority, String userId,
            long createdTime, long updatedTime) {
        return new Complaint(id, orgId, userId, userId + "-name", "CMP-2026-" + id, "DATA_BREACH", priority, status,
                "desc " + id, createdTime, updatedTime, createdTime + 1000);
    }

    @Test
    void addComplaintPersistsRowAndReturnsTrue() {
        Complaint complaint = sampleComplaint("c1", "org1", "OPEN", "HIGH", "user1", 100L, 100L);

        boolean added = dao.addComplaint(complaint);

        assertTrue(added);
        Optional<Complaint> fetched = dao.getComplaintById("c1", "org1");
        assertTrue(fetched.isPresent());
        assertEquals("org1", fetched.get().getOrgId());
        assertEquals("OPEN", fetched.get().getStatus());
    }

    @Test
    void addComplaintThrowsOnDuplicateComplaintIdInsteadOfReturningFalse() {
        dao.addComplaint(sampleComplaint("c1", "org1", "OPEN", "HIGH", "user1", 100L, 100L));

        expectThrows(ComplaintDAOException.class,
                () -> dao.addComplaint(sampleComplaint("c1", "org1", "OPEN", "HIGH", "user1", 200L, 200L)));
    }

    @Test
    void addComplaintThrowsDuplicateReferenceIdExceptionOnReferenceIdCollision() {
        dao.addComplaint(sampleComplaint("c1", "org1", "OPEN", "HIGH", "user1", 100L, 100L));

        Complaint collidingReferenceId = new Complaint("c2", "org1", "user2", "user2-name", "CMP-2026-c1",
                "DATA_BREACH", "HIGH", "OPEN", "desc c2", 200L, 200L, 1200L);

        expectThrows(DuplicateReferenceIdException.class, () -> dao.addComplaint(collidingReferenceId));
    }

    @Test
    void getComplaintByIdReturnsEmptyWhenNotFound() {
        Optional<Complaint> fetched = dao.getComplaintById("does-not-exist", "org1");

        assertFalse(fetched.isPresent());
    }

    @Test
    void getComplaintByIdIsScopedByOrgId() {
        dao.addComplaint(sampleComplaint("c1", "org1", "OPEN", "HIGH", "user1", 100L, 100L));

        Optional<Complaint> fetched = dao.getComplaintById("c1", "org-does-not-own-this");

        assertFalse(fetched.isPresent());
    }

    @Test
    void countByReferenceIdPrefixCountsOnlyMatchingOrgAndPrefix() {
        dao.addComplaint(sampleComplaint("c1", "org1", "OPEN", "HIGH", "user1", 100L, 100L));
        dao.addComplaint(sampleComplaint("c2", "org1", "OPEN", "HIGH", "user1", 100L, 100L));
        dao.addComplaint(sampleComplaint("c3", "org2", "OPEN", "HIGH", "user1", 100L, 100L));

        int count = dao.countByReferenceIdPrefix("org1", "CMP-2026-%");

        assertEquals(2, count);
    }

    @Test
    void updateStatusModifiesStatusAndUpdatedTime() {
        dao.addComplaint(sampleComplaint("c1", "org1", "OPEN", "HIGH", "user1", 100L, 100L));

        boolean updated = dao.updateStatus("c1", "org1", "IN_PROGRESS", 500L);

        assertTrue(updated);
        Optional<Complaint> fetched = dao.getComplaintById("c1", "org1");
        assertTrue(fetched.isPresent());
        assertEquals("IN_PROGRESS", fetched.get().getStatus());
        assertEquals(500L, fetched.get().getUpdatedTime());
    }

    @Test
    void updateStatusReturnsFalseWhenComplaintDoesNotExist() {
        boolean updated = dao.updateStatus("does-not-exist", "org1", "IN_PROGRESS", 500L);

        assertFalse(updated);
    }

    @Test
    void listComplaintsFiltersByStatusPriorityAndUserAndReportsTotal() {
        dao.addComplaint(sampleComplaint("c1", "org1", "OPEN", "HIGH", "user1", 100L, 100L));
        dao.addComplaint(sampleComplaint("c2", "org1", "IN_PROGRESS", "HIGH", "user1", 200L, 200L));
        dao.addComplaint(sampleComplaint("c3", "org1", "OPEN", "LOW", "user2", 300L, 300L));
        dao.addComplaint(sampleComplaint("c4", "org2", "OPEN", "HIGH", "user1", 400L, 400L));

        int[] totalOut = new int[1];
        List<Complaint> results =
                dao.listComplaints("org1", "OPEN", null, null, null, 10, 0, null, totalOut);

        assertEquals(2, totalOut[0]);
        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(c -> "org1".equals(c.getOrgId()) && "OPEN".equals(c.getStatus())));
    }

    @Test
    void listComplaintsAppliesLimitAndOffsetForPagination() {
        for (int i = 1; i <= 5; i++) {
            dao.addComplaint(sampleComplaint("c" + i, "org1", "OPEN", "HIGH", "user1", i * 100L, i * 100L));
        }

        int[] totalOut = new int[1];
        List<Complaint> page1 = dao.listComplaints("org1", null, null, null, null, 2, 0, "updatedTime", totalOut);
        List<Complaint> page2 = dao.listComplaints("org1", null, null, null, null, 2, 2, "updatedTime", totalOut);

        assertEquals(5, totalOut[0]);
        assertEquals(2, page1.size());
        assertEquals(2, page2.size());
        assertEquals("c1", page1.get(0).getComplaintId());
        assertEquals("c2", page1.get(1).getComplaintId());
        assertEquals("c3", page2.get(0).getComplaintId());
    }

    @Test
    void listComplaintsFiltersBySearchAgainstReferenceIdOrUserName() {
        dao.addComplaint(sampleComplaint("c1", "org1", "OPEN", "HIGH", "user1", 100L, 100L));
        dao.addComplaint(sampleComplaint("c2", "org1", "OPEN", "HIGH", "user2", 200L, 200L));

        int[] totalOut = new int[1];
        List<Complaint> byReferenceId =
                dao.listComplaints("org1", null, null, null, "cmp-2026-c1", 10, 0, null, totalOut);
        List<Complaint> byUserName =
                dao.listComplaints("org1", null, null, null, "user2-name", 10, 0, null, totalOut);

        assertEquals(1, byReferenceId.size());
        assertEquals("c1", byReferenceId.get(0).getComplaintId());
        assertEquals(1, byUserName.size());
        assertEquals("c2", byUserName.get(0).getComplaintId());
    }

    @Test
    void listComplaintsSortsDescendingWhenSortHasMinusPrefix() {
        dao.addComplaint(sampleComplaint("c1", "org1", "OPEN", "HIGH", "user1", 100L, 100L));
        dao.addComplaint(sampleComplaint("c2", "org1", "OPEN", "HIGH", "user1", 300L, 300L));
        dao.addComplaint(sampleComplaint("c3", "org1", "OPEN", "HIGH", "user1", 200L, 200L));

        int[] totalOut = new int[1];
        List<Complaint> results = dao.listComplaints("org1", null, null, null, null, 10, 0, "-updatedTime", totalOut);

        assertEquals("c2", results.get(0).getComplaintId());
        assertEquals("c3", results.get(1).getComplaintId());
        assertEquals("c1", results.get(2).getComplaintId());
    }

    @Test
    void listComplaintsDefaultsToUpdatedTimeDescendingWhenSortIsBlank() {
        dao.addComplaint(sampleComplaint("c1", "org1", "OPEN", "HIGH", "user1", 100L, 100L));
        dao.addComplaint(sampleComplaint("c2", "org1", "OPEN", "HIGH", "user1", 300L, 300L));

        int[] totalOut = new int[1];
        List<Complaint> results = dao.listComplaints("org1", null, null, null, null, 10, 0, null, totalOut);

        assertEquals("c2", results.get(0).getComplaintId());
        assertEquals("c1", results.get(1).getComplaintId());
    }

    @Test
    void getQueueStatsGroupsByStatusBucketAndCountsSlaBreaches() {
        // statutoryDueTime = createdTime + 1000 for every row - see sampleComplaint.
        dao.addComplaint(sampleComplaint("c1", "org1", "OPEN", "HIGH", "user1", 100L, 100L));
        dao.addComplaint(sampleComplaint("c2", "org1", "IN_PROGRESS", "HIGH", "user1", 100L, 100L));
        dao.addComplaint(sampleComplaint("c3", "org1", "AWAITING_INTERNAL_REVIEW", "HIGH", "user1", 100L, 100L));
        dao.addComplaint(sampleComplaint("c4", "org1", "WAITING_ON_CLIENT", "HIGH", "user1", 100L, 100L));
        dao.addComplaint(sampleComplaint("c5", "org1", "RESOLVED", "HIGH", "user1", 100L, 100L));
        dao.addComplaint(sampleComplaint("c6", "org2", "OPEN", "HIGH", "user1", 100L, 100L));

        ComplaintQueueStats stats = dao.getQueueStats("org1", 2000L);

        // openCount is OPEN+IN_PROGRESS only (c1, c2) - AWAITING_INTERNAL_REVIEW gets its own
        // dedicated bucket instead, and WAITING_ON_CLIENT has no tile at all (c4 counted nowhere).
        assertEquals(2, stats.getOpenCount());
        assertEquals(1, stats.getAwaitingInternalReviewCount());
        assertEquals(1, stats.getResolvedCount());
        // c1-c4 are all overdue (due 1100 < now 2000); c5 is excluded despite being overdue too,
        // since RESOLVED never counts as breached; c6 is excluded entirely as a different org.
        assertEquals(4, stats.getSlaBreachedCount());
    }

    @Test
    void getQueueStatsExcludesComplaintsNotYetPastTheirDueTime() {
        dao.addComplaint(sampleComplaint("c1", "org1", "OPEN", "HIGH", "user1", 100L, 100L));

        ComplaintQueueStats stats = dao.getQueueStats("org1", 500L);

        assertEquals(1, stats.getOpenCount());
        assertEquals(0, stats.getSlaBreachedCount());
    }
}
