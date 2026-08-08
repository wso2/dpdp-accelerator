package org.wso2.dpdp.accelerator.complaint.mgt.dao.util;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.impl.ComplaintDAOImpl;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.impl.ComplaintEventDAOImpl;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies DBUtil.executeInTransaction actually gives DAO writes atomic commit/rollback across
 * ComplaintDAO and ComplaintEventDAO - i.e. that a status update and its paired audit event can no
 * longer land independently of each other.
 */
class DBUtilTransactionTest {

    private static final String CREATE_COMPLAINT_TABLE =
            "CREATE TABLE IF NOT EXISTS COMPLAINT (" +
            "COMPLAINT_ID VARCHAR(64) PRIMARY KEY, " +
            "ORG_ID VARCHAR(64) NOT NULL, " +
            "USER_ID VARCHAR(64) NOT NULL, " +
            "REFERENCE_ID VARCHAR(64), " +
            "CATEGORY VARCHAR(64), " +
            "PRIORITY VARCHAR(32), " +
            "STATUS VARCHAR(32), " +
            "DESCRIPTION VARCHAR(4000), " +
            "CREATED_TIME BIGINT, " +
            "UPDATED_TIME BIGINT, " +
            "STATUTORY_DUE_TIME BIGINT)";

    private static final String CREATE_EVENT_TABLE =
            "CREATE TABLE IF NOT EXISTS COMPLAINT_EVENT (" +
            "EVENT_ID VARCHAR(64) PRIMARY KEY, " +
            "ORG_ID VARCHAR(64) NOT NULL, " +
            "COMPLAINT_ID VARCHAR(64) NOT NULL, " +
            "ACTOR_USER_ID VARCHAR(64), " +
            "ACTOR_ROLE VARCHAR(32), " +
            "IS_PUBLIC BOOLEAN, " +
            "\"COMMENT\" VARCHAR(4000), " +
            "FROM_STATUS VARCHAR(32), " +
            "TO_STATUS VARCHAR(32), " +
            "ACTION_TIME BIGINT)";

    private final ComplaintDAOImpl complaintDAO = new ComplaintDAOImpl();
    private final ComplaintEventDAOImpl complaintEventDAO = new ComplaintEventDAOImpl();

    @BeforeAll
    static void setUpDatabase() throws SQLException {
        H2TestDbSupport.setUpDatabase("dbutil_transaction_test", CREATE_COMPLAINT_TABLE, CREATE_EVENT_TABLE);
    }

    @BeforeEach
    void clearTables() throws SQLException {
        try (Connection conn = DBUtil.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM COMPLAINT_EVENT");
            stmt.execute("DELETE FROM COMPLAINT");
        }
    }

    private Complaint sampleComplaint(String id, String status) {
        return new Complaint(id, "org1", "user1", "CMP-2026-" + id, "DATA_BREACH", "HIGH", status, "desc", 100L,
                100L, 1100L);
    }

    @Test
    void statusUpdateAndAuditEventCommitTogetherOnSuccess() throws SQLException {
        complaintDAO.addComplaint(sampleComplaint("c1", "OPEN"));

        DBUtil.executeInTransaction(conn -> {
            complaintDAO.updateStatus(conn, "c1", "org1", "IN_PROGRESS", 500L);
            ComplaintEvent event = new ComplaintEvent("e1", "org1", "c1", "user1", "COMPLAINT_OFFICER", true, "note",
                    "OPEN", "IN_PROGRESS", 500L);
            complaintEventDAO.addEvent(conn, event);
        });

        Optional<Complaint> complaint = complaintDAO.getComplaintById("c1", "org1");
        assertTrue(complaint.isPresent());
        assertEquals("IN_PROGRESS", complaint.get().getStatus());
        assertTrue(complaintEventDAO.getEventById("e1", "org1", "c1").isPresent());
    }

    @Test
    void statusUpdateRollsBackWhenTheAuditEventWriteFailsInTheSameTransaction() {
        complaintDAO.addComplaint(sampleComplaint("c1", "OPEN"));

        assertThrows(SQLException.class, () -> DBUtil.executeInTransaction(conn -> {
            complaintDAO.updateStatus(conn, "c1", "org1", "IN_PROGRESS", 500L);
            throw new SQLException("simulated failure recording the audit event");
        }));

        Optional<Complaint> complaint = complaintDAO.getComplaintById("c1", "org1");
        assertTrue(complaint.isPresent());
        assertEquals("OPEN", complaint.get().getStatus());
    }
}
