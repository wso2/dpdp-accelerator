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
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;
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

class ComplaintEventDAOImplTest {

    private static final String CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS COMPLAINT_EVENT (" +
            "COMPLAINT_EVENT_ID VARCHAR(64) PRIMARY KEY, " +
            "ORG_ID VARCHAR(64) NOT NULL, " +
            "COMPLAINT_ID VARCHAR(64) NOT NULL, " +
            "ACTOR_USER_ID VARCHAR(64), " +
            "ACTOR_USER_NAME VARCHAR(64), " +
            "ACTOR_ROLE VARCHAR(32), " +
            "IS_PUBLIC BOOLEAN, " +
            "\"COMMENT\" VARCHAR(4000), " +
            "FROM_STATUS VARCHAR(32), " +
            "TO_STATUS VARCHAR(32), " +
            "ACTION_TIME BIGINT)";

    private final ComplaintEventDAOImpl dao = new ComplaintEventDAOImpl();

    @BeforeClass
    static void setUpDatabase() throws SQLException {
        H2TestDbSupport.setUpDatabase("complaint_event_dao_test", CREATE_TABLE);
    }

    @AfterClass
    static void tearDownDatabase() {
        H2TestDbSupport.tearDownDatabase();
    }

    @BeforeMethod
    void clearTable() throws SQLException {
        Connection conn = DatabaseUtils.getDBConnection();
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM COMPLAINT_EVENT");
            DatabaseUtils.commitTransaction(conn);
        } finally {
            DatabaseUtils.closeConnection(conn);
        }
    }

    private ComplaintEvent sampleEvent(String id, String orgId, String complaintId, boolean isPublic,
            String fromStatus, String toStatus, long actionTime) {
        return new ComplaintEvent(id, orgId, complaintId, "user1", "User One", "USER", isPublic, "comment " + id,
                fromStatus, toStatus, actionTime);
    }

    @Test
    void addEventPersistsRowAndReturnsTrue() {
        boolean added = addEvent(sampleEvent("e1", "org1", "c1", true, null, null, 100L));

        assertTrue(added);
        Optional<ComplaintEvent> fetched = getEventById("e1", "org1", "c1");
        assertTrue(fetched.isPresent());
        assertEquals("comment e1", fetched.get().getComment());
        assertTrue(fetched.get().isPublic());
    }

    @Test
    void addEventThrowsOnDuplicateEventIdInsteadOfReturningFalse() {
        addEvent(sampleEvent("e1", "org1", "c1", true, null, null, 100L));

        expectThrows(ComplaintDAOException.class,
                () -> addEvent(sampleEvent("e1", "org1", "c1", true, null, null, 200L)));
    }

    @Test
    void getEventByIdReturnsEmptyWhenScopedToWrongComplaint() {
        addEvent(sampleEvent("e1", "org1", "c1", true, null, null, 100L));

        Optional<ComplaintEvent> fetched = getEventById("e1", "org1", "c-other");

        assertFalse(fetched.isPresent());
    }

    @Test
    void listEventsFiltersBySinceTimestamp() {
        addEvent(sampleEvent("e1", "org1", "c1", true, null, null, 100L));
        addEvent(sampleEvent("e2", "org1", "c1", true, null, null, 200L));
        addEvent(sampleEvent("e3", "org1", "c1", true, null, null, 300L));

        int[] totalOut = new int[1];
        List<ComplaintEvent> results = listEvents("org1", "c1", 150L, null, null, "asc", 10, 0, totalOut);

        assertEquals(2, totalOut[0]);
        assertEquals("e2", results.get(0).getComplaintEventId());
        assertEquals("e3", results.get(1).getComplaintEventId());
    }

    @Test
    void listEventsFiltersByUntilTimestampInclusive() {
        addEvent(sampleEvent("e1", "org1", "c1", true, null, null, 100L));
        addEvent(sampleEvent("e2", "org1", "c1", true, null, null, 200L));
        addEvent(sampleEvent("e3", "org1", "c1", true, null, null, 300L));

        int[] totalOut = new int[1];
        List<ComplaintEvent> results = listEvents("org1", "c1", null, 200L, null, "asc", 10, 0, totalOut);

        assertEquals(2, totalOut[0]);
        assertEquals("e1", results.get(0).getComplaintEventId());
        assertEquals("e2", results.get(1).getComplaintEventId());
    }

    @Test
    void listEventsFiltersByIsPublic() {
        addEvent(sampleEvent("e1", "org1", "c1", true, null, null, 100L));
        addEvent(sampleEvent("e2", "org1", "c1", false, null, null, 200L));
        addEvent(sampleEvent("e3", "org1", "c1", true, null, null, 300L));

        int[] totalOut = new int[1];
        List<ComplaintEvent> results = listEvents("org1", "c1", null, null, false, "asc", 10, 0, totalOut);

        assertEquals(1, totalOut[0]);
        assertEquals(1, results.size());
        assertEquals("e2", results.get(0).getComplaintEventId());
    }

    @Test
    void listEventsOrdersAscendingByDefault() {
        addEvent(sampleEvent("e1", "org1", "c1", true, null, null, 300L));
        addEvent(sampleEvent("e2", "org1", "c1", true, null, null, 100L));

        int[] totalOut = new int[1];
        List<ComplaintEvent> results = listEvents("org1", "c1", null, null, null, null, 10, 0, totalOut);

        assertEquals("e2", results.get(0).getComplaintEventId());
        assertEquals("e1", results.get(1).getComplaintEventId());
    }

    @Test
    void listEventsOrdersDescendingWhenOrderIsDesc() {
        addEvent(sampleEvent("e1", "org1", "c1", true, null, null, 100L));
        addEvent(sampleEvent("e2", "org1", "c1", true, null, null, 300L));

        int[] totalOut = new int[1];
        List<ComplaintEvent> results = listEvents("org1", "c1", null, null, null, "desc", 10, 0, totalOut);

        assertEquals("e2", results.get(0).getComplaintEventId());
        assertEquals("e1", results.get(1).getComplaintEventId());
    }

    @Test
    void listEventsIsScopedToComplaintAndOrg() {
        addEvent(sampleEvent("e1", "org1", "c1", true, null, null, 100L));
        addEvent(sampleEvent("e2", "org1", "c-other", true, null, null, 200L));
        addEvent(sampleEvent("e3", "org-other", "c1", true, null, null, 300L));

        int[] totalOut = new int[1];
        List<ComplaintEvent> results = listEvents("org1", "c1", null, null, null, "asc", 10, 0, totalOut);

        assertEquals(1, results.size());
        assertEquals("e1", results.get(0).getComplaintEventId());
    }

    @Test
    void statusChangeEventRoundTripsFromAndToStatus() {
        addEvent(sampleEvent("e1", "org1", "c1", true, "OPEN", "IN_PROGRESS", 100L));

        Optional<ComplaintEvent> fetched = getEventById("e1", "org1", "c1");

        assertTrue(fetched.isPresent());
        assertEquals("OPEN", fetched.get().getFromStatus());
        assertEquals("IN_PROGRESS", fetched.get().getToStatus());
        assertEquals("STATUS_CHANGE", fetched.get().deriveEntryType());
    }

    // The DAO takes a Connection and never opens one itself, so these stand in for the service
    // layer that owns the transaction in production - see ComplaintDAO.

    private boolean addEvent(ComplaintEvent event) {
        return DatabaseUtils.executeInTransaction(conn -> dao.addEvent(conn, event));
    }

    private Optional<ComplaintEvent> getEventById(String complaintEventId, String orgId, String complaintId) {
        return DatabaseUtils.executeInTransaction(conn -> dao.getEventById(conn, complaintEventId, orgId, complaintId));
    }

    private List<ComplaintEvent> listEvents(String orgId, String complaintId, Long since, Long until,
            Boolean isPublic, String order, int limit, int offset, int[] totalOut) {
        return DatabaseUtils.executeInTransaction(conn -> dao.listEvents(conn, orgId, complaintId, since, until, isPublic, order,
                limit, offset, totalOut));
    }
}
