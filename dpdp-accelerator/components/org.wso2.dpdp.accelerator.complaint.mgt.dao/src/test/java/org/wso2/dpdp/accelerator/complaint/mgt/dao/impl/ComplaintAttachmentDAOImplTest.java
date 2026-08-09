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
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintAttachment;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.util.DBUtil;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.util.H2TestDbSupport;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComplaintAttachmentDAOImplTest {

    private static final String CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS COMPLAINT_ATTACHMENT (" +
            "ATTACHMENT_ID VARCHAR(64) PRIMARY KEY, " +
            "ORG_ID VARCHAR(64) NOT NULL, " +
            "COMPLAINT_ID VARCHAR(64) NOT NULL, " +
            "COMPLAINT_EVENT_ID VARCHAR(64), " +
            "FILE_NAME VARCHAR(255), " +
            "FILE_CONTENT_TYPE VARCHAR(100), " +
            "FILE_DATA BLOB, " +
            "CREATED_TIME BIGINT)";

    private final ComplaintAttachmentDAOImpl dao = new ComplaintAttachmentDAOImpl();

    @BeforeAll
    static void setUpDatabase() throws SQLException {
        H2TestDbSupport.setUpDatabase("complaint_attachment_dao_test", CREATE_TABLE);
    }

    @BeforeEach
    void clearTable() throws SQLException {
        try (Connection conn = DBUtil.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM COMPLAINT_ATTACHMENT");
        }
    }

    private ComplaintAttachment sampleAttachment(String id, String orgId, String complaintId, String complaintEventId,
            byte[] data, long createdTime) {
        return new ComplaintAttachment(id, orgId, complaintId, complaintEventId, "file-" + id + ".pdf", "application/pdf",
                data, createdTime);
    }

    @Test
    void addAttachmentPersistsRowIncludingNullEventId() {
        boolean added = dao.addAttachment(sampleAttachment("a1", "org1", "c1", null, new byte[]{1, 2, 3}, 100L));

        assertTrue(added);
        Optional<ComplaintAttachment> fetched = dao.getAttachmentWithDataById("a1", "org1", "c1");
        assertTrue(fetched.isPresent());
        assertNull(fetched.get().getComplaintEventId());
        assertArrayEquals(new byte[]{1, 2, 3}, fetched.get().getFileData());
    }

    @Test
    void addAttachmentPersistsRowWithEventId() {
        dao.addAttachment(sampleAttachment("a1", "org1", "c1", "e1", new byte[]{1, 2}, 100L));

        Optional<ComplaintAttachment> fetched = dao.getAttachmentWithDataById("a1", "org1", "c1");

        assertTrue(fetched.isPresent());
        assertEquals("e1", fetched.get().getComplaintEventId());
    }

    @Test
    void addAttachmentThrowsOnDuplicateAttachmentIdInsteadOfReturningFalse() {
        dao.addAttachment(sampleAttachment("a1", "org1", "c1", null, new byte[]{1}, 100L));

        assertThrows(ComplaintDAOException.class,
                () -> dao.addAttachment(sampleAttachment("a1", "org1", "c1", null, new byte[]{2}, 200L)));
    }

    @Test
    void getAttachmentMetadataByIdDoesNotLoadFileDataButReportsSize() {
        dao.addAttachment(sampleAttachment("a1", "org1", "c1", null, new byte[]{1, 2, 3, 4}, 100L));

        Optional<ComplaintAttachment> fetched = dao.getAttachmentMetadataById("a1", "org1", "c1");

        assertTrue(fetched.isPresent());
        assertNull(fetched.get().getFileData());
        assertEquals(4L, fetched.get().getSizeBytes());
    }

    @Test
    void getAttachmentWithDataByIdReturnsEmptyWhenNotFound() {
        Optional<ComplaintAttachment> fetched = dao.getAttachmentWithDataById("missing", "org1", "c1");

        assertFalse(fetched.isPresent());
    }

    @Test
    void getAttachmentMetadataByIdIsScopedByComplaintAndOrg() {
        dao.addAttachment(sampleAttachment("a1", "org1", "c1", null, new byte[]{1}, 100L));

        assertFalse(dao.getAttachmentMetadataById("a1", "org1", "c-other").isPresent());
        assertFalse(dao.getAttachmentMetadataById("a1", "org-other", "c1").isPresent());
    }

    @Test
    void listAttachmentsForComplaintReturnsOnlyAttachmentsWithoutEventIdOrderedByCreatedTime() {
        dao.addAttachment(sampleAttachment("a1", "org1", "c1", null, new byte[]{1}, 200L));
        dao.addAttachment(sampleAttachment("a2", "org1", "c1", null, new byte[]{1}, 100L));
        dao.addAttachment(sampleAttachment("a3", "org1", "c1", "e1", new byte[]{1}, 50L));

        List<ComplaintAttachment> results = dao.listAttachmentsForComplaint("org1", "c1");

        assertEquals(2, results.size());
        assertEquals("a2", results.get(0).getAttachmentId());
        assertEquals("a1", results.get(1).getAttachmentId());
    }

    @Test
    void listAttachmentsForEventReturnsOnlyAttachmentsBoundToThatEvent() {
        dao.addAttachment(sampleAttachment("a1", "org1", "c1", "e1", new byte[]{1}, 100L));
        dao.addAttachment(sampleAttachment("a2", "org1", "c1", "e2", new byte[]{1}, 200L));
        dao.addAttachment(sampleAttachment("a3", "org1", "c1", null, new byte[]{1}, 300L));

        List<ComplaintAttachment> results = dao.listAttachmentsForEvent("org1", "c1", "e1");

        assertEquals(1, results.size());
        assertEquals("a1", results.get(0).getAttachmentId());
    }
}
