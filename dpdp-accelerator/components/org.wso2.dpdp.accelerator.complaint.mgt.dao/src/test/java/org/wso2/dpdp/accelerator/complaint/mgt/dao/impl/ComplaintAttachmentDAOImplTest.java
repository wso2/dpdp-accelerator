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
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintAttachment;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.util.H2TestDbSupport;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.expectThrows;
import static org.testng.Assert.assertTrue;

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
            "IS_PUBLIC BOOLEAN NOT NULL DEFAULT TRUE, " +
            "CREATED_TIME BIGINT)";

    private final ComplaintAttachmentDAOImpl dao = new ComplaintAttachmentDAOImpl();

    @BeforeClass
    static void setUpDatabase() throws SQLException {
        H2TestDbSupport.setUpDatabase("complaint_attachment_dao_test", CREATE_TABLE);
    }

    @AfterClass
    static void tearDownDatabase() {
        H2TestDbSupport.tearDownDatabase();
    }

    @BeforeMethod
    void clearTable() throws SQLException {
        Connection conn = DatabaseUtils.getDBConnection();
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM COMPLAINT_ATTACHMENT");
            DatabaseUtils.commitTransaction(conn);
        } finally {
            DatabaseUtils.closeConnection(conn);
        }
    }

    private ComplaintAttachment sampleAttachment(String id, String orgId, String complaintId, byte[] data,
            boolean isPublic, long createdTime) {
        return new ComplaintAttachment(id, orgId, complaintId, "file-" + id + ".pdf", "application/pdf",
                data, isPublic, createdTime);
    }

    @Test
    void addAttachmentPersistsRowWithNullEventIdAndIsPublicFlag() {
        boolean added = addAttachment(sampleAttachment("a1", "org1", "c1", new byte[]{1, 2, 3}, false, 100L));

        assertTrue(added);
        Optional<ComplaintAttachment> fetched = getAttachmentWithDataById("a1", "org1", "c1");
        assertTrue(fetched.isPresent());
        assertNull(fetched.get().getComplaintEventId());
        assertFalse(fetched.get().isPublic());
        assertEquals(new byte[]{1, 2, 3}, fetched.get().getFileData());
    }

    @Test
    void addAttachmentPersistsGivenComplaintEventId() {
        ComplaintAttachment attachment = sampleAttachment("a1", "org1", "c1", new byte[]{1}, true, 100L);
        attachment.setComplaintEventId("e1");

        addAttachment(attachment);

        Optional<ComplaintAttachment> fetched = getAttachmentWithDataById("a1", "org1", "c1");
        assertTrue(fetched.isPresent());
        assertEquals("e1", fetched.get().getComplaintEventId());
    }

    @Test
    void addAttachmentDefaultsIsPublicTrue() {
        addAttachment(sampleAttachment("a1", "org1", "c1", new byte[]{1, 2}, true, 100L));

        Optional<ComplaintAttachment> fetched = getAttachmentWithDataById("a1", "org1", "c1");

        assertTrue(fetched.isPresent());
        assertTrue(fetched.get().isPublic());
    }

    @Test
    void addAttachmentThrowsOnDuplicateAttachmentIdInsteadOfReturningFalse() {
        addAttachment(sampleAttachment("a1", "org1", "c1", new byte[]{1}, true, 100L));

        expectThrows(ComplaintDAOException.class,
                () -> addAttachment(sampleAttachment("a1", "org1", "c1", new byte[]{2}, true, 200L)));
    }

    @Test
    void getAttachmentMetadataByIdDoesNotLoadFileDataButReportsSize() {
        addAttachment(sampleAttachment("a1", "org1", "c1", new byte[]{1, 2, 3, 4}, true, 100L));

        Optional<ComplaintAttachment> fetched = getAttachmentMetadataById("a1", "org1", "c1");

        assertTrue(fetched.isPresent());
        assertNull(fetched.get().getFileData());
        assertEquals(4L, fetched.get().getSizeBytes());
    }

    @Test
    void getAttachmentWithDataByIdReturnsEmptyWhenNotFound() {
        Optional<ComplaintAttachment> fetched = getAttachmentWithDataById("missing", "org1", "c1");

        assertFalse(fetched.isPresent());
    }

    @Test
    void getAttachmentMetadataByIdIsScopedByComplaintAndOrg() {
        addAttachment(sampleAttachment("a1", "org1", "c1", new byte[]{1}, true, 100L));

        assertFalse(getAttachmentMetadataById("a1", "org1", "c-other").isPresent());
        assertFalse(getAttachmentMetadataById("a1", "org-other", "c1").isPresent());
    }

    @Test
    void listAttachmentsForComplaintReturnsAllAttachmentsOrderedByCreatedTime() {
        addAttachment(sampleAttachment("a1", "org1", "c1", new byte[]{1}, true, 200L));
        addAttachment(sampleAttachment("a2", "org1", "c1", new byte[]{1}, false, 100L));

        List<ComplaintAttachment> results = listAttachmentsForComplaint("org1", "c1");

        assertEquals(2, results.size());
        assertEquals("a2", results.get(0).getAttachmentId());
        assertEquals("a1", results.get(1).getAttachmentId());
    }

    // The DAO takes a Connection and never opens one itself, so these stand in for the service
    // layer that owns the transaction in production - see ComplaintAttachmentDAO.

    private boolean addAttachment(ComplaintAttachment attachment) {
        return DatabaseUtils.executeInTransaction(conn -> dao.addAttachment(conn, attachment));
    }

    private Optional<ComplaintAttachment> getAttachmentMetadataById(String attachmentId, String orgId,
            String complaintId) {
        return DatabaseUtils.executeInTransaction(conn -> dao.getAttachmentMetadataById(conn, attachmentId, orgId, complaintId));
    }

    private Optional<ComplaintAttachment> getAttachmentWithDataById(String attachmentId, String orgId,
            String complaintId) {
        return DatabaseUtils.executeInTransaction(conn -> dao.getAttachmentWithDataById(conn, attachmentId, orgId, complaintId));
    }

    private List<ComplaintAttachment> listAttachmentsForComplaint(String orgId, String complaintId) {
        return DatabaseUtils.executeInTransaction(conn -> dao.listAttachmentsForComplaint(conn, orgId, complaintId));
    }
}
