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
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationServiceImpl;
import org.wso2.dpdp.accelerator.common.persistence.JDBCPersistenceManager;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintAttachmentDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintEventDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintAttachment;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintAttachmentService.UploadedFile;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintAttachmentDownloadResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintAttachmentResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintException;
import org.wso2.dpdp.accelerator.complaint.mgt.service.internal.ComplaintServiceDataHolder;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.expectThrows;
import static org.testng.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ComplaintAttachmentServiceImplTest {

    @Mock
    private ComplaintAttachmentDAO attachmentDAO;
    @Mock
    private ComplaintEventDAO complaintEventDAO;
    @Mock
    private ComplaintService complaintService;

    private ComplaintAttachmentServiceImpl attachmentService;

    @BeforeClass
    void seedConfigurationService() {
        // Normally bound by ComplaintServiceComponent's OSGi @Reference; AttachmentPolicy reads
        // it via ComplaintServiceDataHolder, so a test running outside a live Carbon environment
        // must seed it itself.
        ComplaintServiceDataHolder.getInstance().setConfigurationService(new DPDPConfigurationServiceImpl());
    }

    @BeforeClass
    static void pointPersistenceManagerAtAnInMemoryDatabase() throws Exception {
        // uploadComplaintAttachments now opens its own Connection via DatabaseUtils to make the
        // event/attachment writes atomic (see ComplaintAttachmentServiceImpl) - the DAOs
        // themselves are mocked here, so no schema is needed, but JDBCPersistenceManager still
        // needs a real DataSource to hand out a Connection at all.
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:complaint_attachment_service_test;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        setManagerDataSource(dataSource);
    }

    @AfterClass
    void clearConfigurationService() {
        ComplaintServiceDataHolder.getInstance().setConfigurationService(null);
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
        attachmentService = new ComplaintAttachmentServiceImpl(attachmentDAO, complaintEventDAO, complaintService);
    }

    @AfterMethod
    void tearDown() {
        ComplaintServiceDataHolder.getInstance().setConfigurationService(new DPDPConfigurationServiceImpl());
    }

    private UploadedFile pdfFile(String name, int size) {
        return new UploadedFile(name, "application/pdf", new byte[size]);
    }

    // ---- uploadComplaintAttachments ----

    @Test
    void uploadComplaintAttachmentsRequiresComplaintToExist() throws Exception {
        when(complaintService.requireComplaint(any(Connection.class), eq("org1"), eq("c1"))).thenThrow(
                new ComplaintException("CO-4040", "Complaint not found", "desc", 404));

        expectThrows(ComplaintException.class, () -> attachmentService.uploadComplaintAttachments("org1", "c1",
                List.of(pdfFile("a.pdf", 10)), true, "user1", "User One", "USER"));

        verify(attachmentDAO, never()).addAttachment(any(Connection.class), any());
    }

    @Test
    void uploadComplaintAttachmentsThrowsWhenFileListIsEmpty() {
        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> attachmentService.uploadComplaintAttachments("org1", "c1", List.of(), true, "user1",
                        "User One", "USER"));

        assertEquals("CO-4002", ex.getCode());
    }

    @Test
    void uploadComplaintAttachmentsThrowsWhenFileDataIsEmpty() {
        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> attachmentService.uploadComplaintAttachments("org1", "c1",
                        List.of(pdfFile("empty.pdf", 0)), true, "user1", "User One", "USER"));

        assertEquals("CO-4002", ex.getCode());
    }

    @Test
    void uploadComplaintAttachmentsThrowsWhenContentTypeNotAllowed() {
        UploadedFile file = new UploadedFile("a.exe", "application/octet-stream", new byte[]{1});

        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> attachmentService.uploadComplaintAttachments("org1", "c1", List.of(file), true, "user1",
                        "User One", "USER"));

        assertEquals("CO-4002", ex.getCode());
    }

    @Test
    void uploadComplaintAttachmentsThrowsWhenTooManyFilesInOneRequest() {
        DPDPConfigurationService configurationService = mock(DPDPConfigurationService.class);
        when(configurationService.getComplaintsAttachmentMaxFilesPerUpload()).thenReturn(2);
        ComplaintServiceDataHolder.getInstance().setConfigurationService(configurationService);

        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> attachmentService.uploadComplaintAttachments("org1", "c1",
                        List.of(pdfFile("a.pdf", 10), pdfFile("b.pdf", 10), pdfFile("c.pdf", 10)), true, "user1",
                        "User One", "USER"));

        assertEquals("CO-4002", ex.getCode());
    }

    @Test
    void uploadComplaintAttachmentsThrowsWhenFileExceedsMaxSize() {
        // AttachmentPolicy.getMaxSizeBytes() defaults to 10 MB outside a real Carbon environment
        // (no dpdp-accelerator.xml on disk) - see AttachmentPolicyTest for coverage of the
        // configured-value path itself.
        int overTheDefaultLimit = 10 * 1024 * 1024 + 1;

        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> attachmentService.uploadComplaintAttachments("org1", "c1",
                        List.of(pdfFile("big.pdf", overTheDefaultLimit)), true, "user1", "User One", "USER"));

        assertEquals("CO-4002", ex.getCode());
    }

    @Test
    void uploadComplaintAttachmentsThrowsWhenActorUserIdBlank() throws Exception {
        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> attachmentService.uploadComplaintAttachments("org1", "c1", List.of(pdfFile("a.pdf", 10)), true,
                        "  ", "User One", "USER"));

        assertEquals("CO-4002", ex.getCode());
        verify(complaintEventDAO, never()).addEvent(any(Connection.class), any());
    }

    @Test
    void uploadComplaintAttachmentsThrowsWhenActorRoleInvalid() throws Exception {
        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> attachmentService.uploadComplaintAttachments("org1", "c1", List.of(pdfFile("a.pdf", 10)), true,
                        "user1", "User One", "SYSTEM"));

        assertEquals("CO-4002", ex.getCode());
        verify(complaintEventDAO, never()).addEvent(any(Connection.class), any());
    }

    @Test
    void uploadComplaintAttachmentsStoresEachFileWithGivenIsPublic() throws Exception {
        when(complaintEventDAO.addEvent(any(Connection.class), any(ComplaintEvent.class))).thenReturn(true);
        when(attachmentDAO.addAttachment(any(Connection.class), any(ComplaintAttachment.class))).thenReturn(true);

        List<ComplaintAttachmentResponseDTO> result = attachmentService.uploadComplaintAttachments("org1", "c1",
                List.of(pdfFile("a.pdf", 10), pdfFile("b.pdf", 20)), false, "officer1", "Officer One",
                "COMPLAINT_OFFICER");

        assertEquals(2, result.size());
        assertEquals(10, result.get(0).getSizeBytes());
        assertEquals(20, result.get(1).getSizeBytes());
        assertEquals(false, result.get(0).isPublic());
        assertEquals(false, result.get(1).isPublic());
    }

    @Test
    void uploadComplaintAttachmentsRecordsOneUploadEventAndLinksEveryAttachmentToIt() throws Exception {
        when(complaintEventDAO.addEvent(any(Connection.class), any(ComplaintEvent.class))).thenReturn(true);
        when(attachmentDAO.addAttachment(any(Connection.class), any(ComplaintAttachment.class))).thenReturn(true);

        List<ComplaintAttachmentResponseDTO> result = attachmentService.uploadComplaintAttachments("org1", "c1",
                List.of(pdfFile("a.pdf", 10), pdfFile("b.pdf", 20)), true, "user1", "User One", "USER");

        ArgumentCaptor<ComplaintEvent> eventCaptor = ArgumentCaptor.forClass(ComplaintEvent.class);
        verify(complaintEventDAO).addEvent(any(Connection.class), eventCaptor.capture());
        ComplaintEvent event = eventCaptor.getValue();

        assertNotNull(event.getComplaintEventId());
        assertEquals("user1", event.getActorUserId());
        assertEquals("User One", event.getActorUserName());
        assertEquals("USER", event.getActorRole());
        assertTrue(event.isPublic());
        assertNull(event.getComment());

        assertEquals(event.getComplaintEventId(), result.get(0).getComplaintEventId());
        assertEquals(event.getComplaintEventId(), result.get(1).getComplaintEventId());
    }

    @Test
    void uploadComplaintAttachmentsThrowsInternalErrorWhenEventStoreFails() throws Exception {
        when(complaintEventDAO.addEvent(any(Connection.class), any(ComplaintEvent.class))).thenReturn(false);

        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> attachmentService.uploadComplaintAttachments("org1", "c1", List.of(pdfFile("a.pdf", 10)), true,
                        "user1", "User One", "USER"));

        assertEquals("CO-5000", ex.getCode());
        verify(attachmentDAO, never()).addAttachment(any(Connection.class), any());
    }

    @Test
    void uploadComplaintAttachmentsThrowsInternalErrorWhenPersistFails() throws Exception {
        when(complaintEventDAO.addEvent(any(Connection.class), any(ComplaintEvent.class))).thenReturn(true);
        when(attachmentDAO.addAttachment(any(Connection.class), any(ComplaintAttachment.class))).thenReturn(false);

        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> attachmentService.uploadComplaintAttachments("org1", "c1", List.of(pdfFile("a.pdf", 10)),
                        true, "user1", "User One", "USER"));

        assertEquals("CO-5000", ex.getCode());
    }

    // ---- listAttachmentsForComplaint ----

    @Test
    void listAttachmentsForComplaintMapsDaoResultsToMetadataDtos() {
        ComplaintAttachment attachment = new ComplaintAttachment();
        attachment.setAttachmentId("a1");
        attachment.setFileName("a.pdf");
        attachment.setContentType("application/pdf");
        attachment.setSizeBytesOverride(123L);
        when(attachmentDAO.listAttachmentsForComplaint(any(Connection.class), eq("org1"), eq("c1"))).thenReturn(List.of(attachment));

        List<ComplaintAttachmentResponseDTO> result = attachmentService.listAttachmentsForComplaint("org1", "c1");

        assertEquals(1, result.size());
        assertEquals("a1", result.get(0).getAttachmentId());
        assertEquals(123L, result.get(0).getSizeBytes());
    }

    @Test
    void listAttachmentsForComplaintReturnsEmptyWhenNoneExist() {
        when(attachmentDAO.listAttachmentsForComplaint(any(Connection.class), eq("org1"), eq("c1"))).thenReturn(List.of());

        List<ComplaintAttachmentResponseDTO> result = attachmentService.listAttachmentsForComplaint("org1", "c1");

        assertTrue(result.isEmpty());
    }

    // ---- downloadAttachment ----

    @Test
    void downloadAttachmentThrows404WhenNotFound() {
        when(attachmentDAO.getAttachmentWithDataById(any(Connection.class), eq("a1"), eq("org1"), eq("c1"))).thenReturn(Optional.empty());

        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> attachmentService.downloadAttachment("org1", "c1", "a1", true));

        assertEquals("CO-4040", ex.getCode());
    }

    @Test
    void downloadAttachmentAllowsUnrestrictedAccessRegardlessOfIsPublic() {
        ComplaintAttachment attachment = new ComplaintAttachment("a1", "org1", "c1", "a.pdf",
                "application/pdf", new byte[]{1, 2, 3}, false, 100L);
        when(attachmentDAO.getAttachmentWithDataById(any(Connection.class), eq("a1"), eq("org1"), eq("c1"))).thenReturn(Optional.of(attachment));

        ComplaintAttachmentDownloadResponseDTO result = attachmentService.downloadAttachment("org1", "c1", "a1",
                false);

        assertEquals("a1", result.getAttachmentId());
        assertEquals(3, Base64.getDecoder().decode(result.getContent()).length);
    }

    @Test
    void downloadAttachmentDeniesRestrictedAccessToNonPublicAttachment() {
        ComplaintAttachment attachment = new ComplaintAttachment("a1", "org1", "c1", "a.pdf",
                "application/pdf", new byte[]{1}, false, 100L);
        when(attachmentDAO.getAttachmentWithDataById(any(Connection.class), eq("a1"), eq("org1"), eq("c1"))).thenReturn(Optional.of(attachment));

        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> attachmentService.downloadAttachment("org1", "c1", "a1", true));

        assertEquals("CO-4030", ex.getCode());
    }

    @Test
    void downloadAttachmentAllowsRestrictedAccessToPublicAttachment() {
        ComplaintAttachment attachment = new ComplaintAttachment("a1", "org1", "c1", "a.pdf",
                "application/pdf", new byte[]{1}, true, 100L);
        when(attachmentDAO.getAttachmentWithDataById(any(Connection.class), eq("a1"), eq("org1"), eq("c1"))).thenReturn(Optional.of(attachment));

        ComplaintAttachmentDownloadResponseDTO result = attachmentService.downloadAttachment("org1", "c1", "a1",
                true);

        assertEquals("a1", result.getAttachmentId());
    }

    // ---- own* ownership defense-in-depth ----

    @Test
    void uploadOwnComplaintAttachmentsThrowsWhenComplaintIsNotOwnedByCallerAndNeverPersists() throws Exception {
        when(complaintService.requireOwnedComplaint(any(Connection.class), eq("org1"), eq("c1"), eq("user1")))
                .thenThrow(new ComplaintException("CO-4040", "not found", "desc", 404));

        expectThrows(ComplaintException.class, () -> attachmentService.uploadOwnComplaintAttachments("org1", "c1",
                "user1", "User One", List.of(pdfFile("a.pdf", 10))));

        verify(attachmentDAO, never()).addAttachment(any(Connection.class), any());
    }

    @Test
    void uploadOwnComplaintAttachmentsVerifiesOwnershipThenUploadsAsPublicUserRole() throws Exception {
        when(complaintEventDAO.addEvent(any(Connection.class), any(ComplaintEvent.class))).thenReturn(true);
        when(attachmentDAO.addAttachment(any(Connection.class), any())).thenReturn(true);

        List<ComplaintAttachmentResponseDTO> result = attachmentService.uploadOwnComplaintAttachments("org1", "c1",
                "user1", "User One", List.of(pdfFile("a.pdf", 10)));

        assertEquals(1, result.size());
        assertTrue(result.get(0).isPublic());
        verify(complaintService).requireOwnedComplaint(any(Connection.class), eq("org1"), eq("c1"), eq("user1"));
        ArgumentCaptor<ComplaintEvent> captor = ArgumentCaptor.forClass(ComplaintEvent.class);
        verify(complaintEventDAO).addEvent(any(Connection.class), captor.capture());
        assertEquals("USER", captor.getValue().getActorRole());
    }

    @Test
    void downloadOwnAttachmentThrowsWhenComplaintIsNotOwnedByCallerAndNeverFetches() {
        when(complaintService.requireOwnedComplaint(any(Connection.class), eq("org1"), eq("c1"), eq("user1")))
                .thenThrow(new ComplaintException("CO-4040", "not found", "desc", 404));

        expectThrows(ComplaintException.class,
                () -> attachmentService.downloadOwnAttachment("org1", "c1", "user1", "a1"));

        verify(attachmentDAO, never()).getAttachmentWithDataById(any(), any(), any(), any());
    }

    @Test
    void downloadOwnAttachmentVerifiesOwnershipThenRestrictsToPublicAttachments() {
        ComplaintAttachment attachment = new ComplaintAttachment("a1", "org1", "c1", "a.pdf",
                "application/pdf", new byte[]{1}, false, 100L);
        when(attachmentDAO.getAttachmentWithDataById(any(Connection.class), eq("a1"), eq("org1"), eq("c1"))).thenReturn(Optional.of(attachment));

        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> attachmentService.downloadOwnAttachment("org1", "c1", "user1", "a1"));

        assertEquals("CO-4030", ex.getCode());
        verify(complaintService).requireOwnedComplaint(any(Connection.class), eq("org1"), eq("c1"), eq("user1"));
    }
}
