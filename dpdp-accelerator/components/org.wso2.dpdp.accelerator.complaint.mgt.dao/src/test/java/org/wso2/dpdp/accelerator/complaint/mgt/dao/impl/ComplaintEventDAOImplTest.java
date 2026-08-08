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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.exception.ComplaintDAOException;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.util.DBUtil;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.util.H2TestDbSupport;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComplaintEventDAOImplTest {

    private static final String CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS COMPLAINT_EVENT (" +
            "COMPLAINT_EVENT_ID VARCHAR(64) PRIMARY KEY, " +
            "ORG_ID VARCHAR(64) NOT NULL, " +
            "COMPLAINT_ID VARCHAR(64) NOT NULL, " +
            "ACTOR_USER_ID VARCHAR(64), " +
            "ACTOR_ROLE VARCHAR(32), " +
            "IS_PUBLIC BOOLEAN, " +
            "\"COMMENT\" VARCHAR(4000), " +
            "FROM_STATUS VARCHAR(32), " +
            "TO_STATUS VARCHAR(32), " +
            "ACTION_TIME BIGINT)";

    private final ComplaintEventDAOImpl dao = new ComplaintEventDAOImpl();

    @BeforeAll
    static void setUpDatabase() throws SQLException {
        H2TestDbSupport.setUpDatabase("complaint_event_dao_test", CREATE_TABLE);
    }

    @BeforeEach
    void clearTable() throws SQLException {
        try (Connection conn = DBUtil.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM COMPLAINT_EVENT");
        }
    }

    private ComplaintEvent sampleEvent(String id, String orgId, String complaintId, boolean isPublic,
            String fromStatus, String toStatus, long actionTime) {
        return new ComplaintEvent(id, orgId, complaintId, "user1", "USER", isPublic, "comment " + id, fromStatus,
                toStatus, actionTime);
    }

    @Test
    void addEventPersistsRowAndReturnsTrue() {
        boolean added = dao.addEvent(sampleEvent("e1", "org1", "c1", true, null, null, 100L));

        assertTrue(added);
        Optional<ComplaintEvent> fetched = dao.getEventById("e1", "org1", "c1");
        assertTrue(fetched.isPresent());
        assertEquals("comment e1", fetched.get().getComment());
        assertTrue(fetched.get().isPublic());
    }

    @Test
    void addEventThrowsOnDuplicateEventIdInsteadOfReturningFalse() {
        dao.addEvent(sampleEvent("e1", "org1", "c1", true, null, null, 100L));

        assertThrows(ComplaintDAOException.class,
                () -> dao.addEvent(sampleEvent("e1", "org1", "c1", true, null, null, 200L)));
    }

    @Test
    void getEventByIdReturnsEmptyWhenScopedToWrongComplaint() {
        dao.addEvent(sampleEvent("e1", "org1", "c1", true, null, null, 100L));

        Optional<ComplaintEvent> fetched = dao.getEventById("e1", "org1", "c-other");

        assertFalse(fetched.isPresent());
    }

    @Test
    void listEventsFiltersBySinceTimestamp() {
        dao.addEvent(sampleEvent("e1", "org1", "c1", true, null, null, 100L));
        dao.addEvent(sampleEvent("e2", "org1", "c1", true, null, null, 200L));
        dao.addEvent(sampleEvent("e3", "org1", "c1", true, null, null, 300L));

        int[] totalOut = new int[1];
        List<ComplaintEvent> results = dao.listEvents("org1", "c1", 150L, null, "asc", 10, 0, totalOut);

        assertEquals(2, totalOut[0]);
        assertEquals("e2", results.get(0).getEventId());
        assertEquals("e3", results.get(1).getEventId());
    }

    @Test
    void listEventsFiltersByIsPublic() {
        dao.addEvent(sampleEvent("e1", "org1", "c1", true, null, null, 100L));
        dao.addEvent(sampleEvent("e2", "org1", "c1", false, null, null, 200L));
        dao.addEvent(sampleEvent("e3", "org1", "c1", true, null, null, 300L));

        int[] totalOut = new int[1];
        List<ComplaintEvent> results = dao.listEvents("org1", "c1", null, false, "asc", 10, 0, totalOut);

        assertEquals(1, totalOut[0]);
        assertEquals(1, results.size());
        assertEquals("e2", results.get(0).getEventId());
    }

    @Test
    void listEventsOrdersAscendingByDefault() {
        dao.addEvent(sampleEvent("e1", "org1", "c1", true, null, null, 300L));
        dao.addEvent(sampleEvent("e2", "org1", "c1", true, null, null, 100L));

        int[] totalOut = new int[1];
        List<ComplaintEvent> results = dao.listEvents("org1", "c1", null, null, null, 10, 0, totalOut);

        assertEquals("e2", results.get(0).getEventId());
        assertEquals("e1", results.get(1).getEventId());
    }

    @Test
    void listEventsOrdersDescendingWhenOrderIsDesc() {
        dao.addEvent(sampleEvent("e1", "org1", "c1", true, null, null, 100L));
        dao.addEvent(sampleEvent("e2", "org1", "c1", true, null, null, 300L));

        int[] totalOut = new int[1];
        List<ComplaintEvent> results = dao.listEvents("org1", "c1", null, null, "desc", 10, 0, totalOut);

        assertEquals("e2", results.get(0).getEventId());
        assertEquals("e1", results.get(1).getEventId());
    }

    @Test
    void listEventsIsScopedToComplaintAndOrg() {
        dao.addEvent(sampleEvent("e1", "org1", "c1", true, null, null, 100L));
        dao.addEvent(sampleEvent("e2", "org1", "c-other", true, null, null, 200L));
        dao.addEvent(sampleEvent("e3", "org-other", "c1", true, null, null, 300L));

        int[] totalOut = new int[1];
        List<ComplaintEvent> results = dao.listEvents("org1", "c1", null, null, "asc", 10, 0, totalOut);

        assertEquals(1, results.size());
        assertEquals("e1", results.get(0).getEventId());
    }

    @Test
    void statusChangeEventRoundTripsFromAndToStatus() {
        dao.addEvent(sampleEvent("e1", "org1", "c1", true, "OPEN", "IN_PROGRESS", 100L));

        Optional<ComplaintEvent> fetched = dao.getEventById("e1", "org1", "c1");

        assertTrue(fetched.isPresent());
        assertEquals("OPEN", fetched.get().getFromStatus());
        assertEquals("IN_PROGRESS", fetched.get().getToStatus());
        assertEquals("STATUS_CHANGE", fetched.get().deriveEntryType());
    }
}
