package org.wso2.dpdp.accelerator.complaint.mgt.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintAttachmentDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintAttachment;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintAttachmentService.UploadedFile;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintEventService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintAttachmentDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintTimelineEntryDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplaintAttachmentServiceImplTest {

    @Mock
    private ComplaintAttachmentDAO attachmentDAO;
    @Mock
    private ComplaintService complaintService;
    @Mock
    private ComplaintEventService complaintEventService;

    private ComplaintAttachmentServiceImpl attachmentService;

    @BeforeEach
    void setUp() {
        attachmentService = new ComplaintAttachmentServiceImpl(attachmentDAO, complaintService,
                complaintEventService);
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("CO_MAX_ATTACHMENT_SIZE_BYTES");
    }

    private UploadedFile pdfFile(String name, int size) {
        return new UploadedFile(name, "application/pdf", new byte[size]);
    }

    // ---- uploadComplaintAttachments ----

    @Test
    void uploadComplaintAttachmentsRequiresComplaintToExist() {
        when(complaintService.requireComplaint("org1", "c1")).thenThrow(
                new ComplaintException("CO-4040", "Complaint not found", "desc", 404));

        assertThrows(ComplaintException.class,
                () -> attachmentService.uploadComplaintAttachments("org1", "c1", List.of(pdfFile("a.pdf", 10))));

        verify(attachmentDAO, never()).addAttachment(any());
    }

    @Test
    void uploadComplaintAttachmentsThrowsWhenFileListIsEmpty() {
        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> attachmentService.uploadComplaintAttachments("org1", "c1", List.of()));

        assertEquals("CO-4002", ex.getCode());
    }

    @Test
    void uploadComplaintAttachmentsThrowsWhenFileDataIsEmpty() {
        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> attachmentService.uploadComplaintAttachments("org1", "c1",
                        List.of(pdfFile("empty.pdf", 0))));

        assertEquals("CO-4002", ex.getCode());
    }

    @Test
    void uploadComplaintAttachmentsThrowsWhenContentTypeNotAllowed() {
        UploadedFile file = new UploadedFile("a.exe", "application/octet-stream", new byte[]{1});

        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> attachmentService.uploadComplaintAttachments("org1", "c1", List.of(file)));

        assertEquals("CO-4002", ex.getCode());
    }

    @Test
    void uploadComplaintAttachmentsThrowsWhenFileExceedsMaxSize() {
        System.setProperty("CO_MAX_ATTACHMENT_SIZE_BYTES", "5");

        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> attachmentService.uploadComplaintAttachments("org1", "c1",
                        List.of(pdfFile("big.pdf", 10))));

        assertEquals("CO-4002", ex.getCode());
    }

    @Test
    void uploadComplaintAttachmentsStoresEachFileWithNullEventId() {
        when(attachmentDAO.addAttachment(any(ComplaintAttachment.class))).thenReturn(true);

        List<ComplaintAttachmentDTO> result = attachmentService.uploadComplaintAttachments("org1", "c1",
                List.of(pdfFile("a.pdf", 10), pdfFile("b.pdf", 20)));

        assertEquals(2, result.size());
        assertEquals(10, result.get(0).getSizeBytes());
        assertEquals(20, result.get(1).getSizeBytes());
    }

    @Test
    void uploadComplaintAttachmentsThrowsInternalErrorWhenPersistFails() {
        when(attachmentDAO.addAttachment(any(ComplaintAttachment.class))).thenReturn(false);

        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> attachmentService.uploadComplaintAttachments("org1", "c1", List.of(pdfFile("a.pdf", 10))));

        assertEquals("CO-5000", ex.getCode());
    }

    // ---- uploadCommentAttachments ----

    @Test
    void uploadCommentAttachmentsThrowsWhenActorUserIdIsBlank() {
        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> attachmentService.uploadCommentAttachments("org1", "c1", "e1", " ",
                        List.of(pdfFile("a.pdf", 10))));

        assertEquals("CO-4002", ex.getCode());
    }

    @Test
    void uploadCommentAttachmentsThrowsForbiddenWhenActorDoesNotMatchCommentAuthor() {
        ComplaintTimelineEntryDTO entry = new ComplaintTimelineEntryDTO();
        entry.setActorUserId("original-author");
        when(complaintEventService.getTimelineEntry("org1", "c1", "e1")).thenReturn(entry);

        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> attachmentService.uploadCommentAttachments("org1", "c1", "e1", "someone-else",
                        List.of(pdfFile("a.pdf", 10))));

        assertEquals("CO-4030", ex.getCode());
        assertEquals(403, ex.getStatusCode());
    }

    @Test
    void uploadCommentAttachmentsStoresFilesBoundToTheEventWhenActorMatches() {
        ComplaintTimelineEntryDTO entry = new ComplaintTimelineEntryDTO();
        entry.setActorUserId("author1");
        when(complaintEventService.getTimelineEntry("org1", "c1", "e1")).thenReturn(entry);
        when(attachmentDAO.addAttachment(any(ComplaintAttachment.class))).thenReturn(true);

        List<ComplaintAttachmentDTO> result = attachmentService.uploadCommentAttachments("org1", "c1", "e1",
                "author1", List.of(pdfFile("a.pdf", 10)));

        assertEquals(1, result.size());
    }

    // ---- listAttachmentsForComplaint / listAttachmentsForEvent ----

    @Test
    void listAttachmentsForComplaintMapsDaoResultsToMetadataDtos() {
        ComplaintAttachment attachment = new ComplaintAttachment();
        attachment.setAttachmentId("a1");
        attachment.setFileName("a.pdf");
        attachment.setContentType("application/pdf");
        attachment.setSizeBytesOverride(123L);
        when(attachmentDAO.listAttachmentsForComplaint("org1", "c1")).thenReturn(List.of(attachment));

        List<ComplaintAttachmentDTO> result = attachmentService.listAttachmentsForComplaint("org1", "c1");

        assertEquals(1, result.size());
        assertEquals("a1", result.get(0).getAttachmentId());
        assertEquals(123L, result.get(0).getSizeBytes());
    }

    @Test
    void listAttachmentsForEventMapsDaoResultsToMetadataDtos() {
        when(attachmentDAO.listAttachmentsForEvent("org1", "c1", "e1")).thenReturn(List.of());

        List<ComplaintAttachmentDTO> result = attachmentService.listAttachmentsForEvent("org1", "c1", "e1");

        assertTrue(result.isEmpty());
    }

    // ---- downloadAttachment ----

    @Test
    void downloadAttachmentThrows404WhenNotFound() {
        when(attachmentDAO.getAttachmentWithDataById("a1", "org1", "c1")).thenReturn(Optional.empty());

        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> attachmentService.downloadAttachment("org1", "c1", "a1", "USER"));

        assertEquals("CO-4040", ex.getCode());
    }

    @Test
    void downloadAttachmentAllowsAccessWhenNotBoundToAnEvent() {
        ComplaintAttachment attachment = new ComplaintAttachment("a1", "org1", "c1", null, "a.pdf",
                "application/pdf", new byte[]{1, 2, 3}, 100L);
        when(attachmentDAO.getAttachmentWithDataById("a1", "org1", "c1")).thenReturn(Optional.of(attachment));

        ComplaintAttachmentDTO dto = attachmentService.downloadAttachment("org1", "c1", "a1", "USER");

        assertEquals("a1", dto.getAttachmentId());
        assertEquals(3, dto.getContent().length);
    }

    @Test
    void downloadAttachmentDeniesUserAccessToInternalTimelineEntryAttachment() {
        ComplaintAttachment attachment = new ComplaintAttachment("a1", "org1", "c1", "e1", "a.pdf",
                "application/pdf", new byte[]{1}, 100L);
        when(attachmentDAO.getAttachmentWithDataById("a1", "org1", "c1")).thenReturn(Optional.of(attachment));
        ComplaintTimelineEntryDTO entry = new ComplaintTimelineEntryDTO();
        entry.setPublic(false);
        when(complaintEventService.getTimelineEntry("org1", "c1", "e1")).thenReturn(entry);

        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> attachmentService.downloadAttachment("org1", "c1", "a1", "USER"));

        assertEquals("CO-4030", ex.getCode());
    }

    @Test
    void downloadAttachmentAllowsUserAccessToPublicTimelineEntryAttachment() {
        ComplaintAttachment attachment = new ComplaintAttachment("a1", "org1", "c1", "e1", "a.pdf",
                "application/pdf", new byte[]{1}, 100L);
        when(attachmentDAO.getAttachmentWithDataById("a1", "org1", "c1")).thenReturn(Optional.of(attachment));
        ComplaintTimelineEntryDTO entry = new ComplaintTimelineEntryDTO();
        entry.setPublic(true);
        when(complaintEventService.getTimelineEntry("org1", "c1", "e1")).thenReturn(entry);

        ComplaintAttachmentDTO dto = attachmentService.downloadAttachment("org1", "c1", "a1", "USER");

        assertEquals("a1", dto.getAttachmentId());
    }

    @Test
    void downloadAttachmentSkipsRoleCheckWhenRequesterRoleIsAbsent() {
        ComplaintAttachment attachment = new ComplaintAttachment("a1", "org1", "c1", "e1", "a.pdf",
                "application/pdf", new byte[]{1}, 100L);
        when(attachmentDAO.getAttachmentWithDataById("a1", "org1", "c1")).thenReturn(Optional.of(attachment));

        ComplaintAttachmentDTO dto = attachmentService.downloadAttachment("org1", "c1", "a1", null);

        assertEquals("a1", dto.getAttachmentId());
        verify(complaintEventService, never()).getTimelineEntry(any(), any(), any());
    }

    @Test
    void downloadAttachmentSkipsRoleCheckForNonUserRole() {
        ComplaintAttachment attachment = new ComplaintAttachment("a1", "org1", "c1", "e1", "a.pdf",
                "application/pdf", new byte[]{1}, 100L);
        when(attachmentDAO.getAttachmentWithDataById("a1", "org1", "c1")).thenReturn(Optional.of(attachment));

        ComplaintAttachmentDTO dto = attachmentService.downloadAttachment("org1", "c1", "a1", "COMPLAINT_OFFICER");

        assertEquals("a1", dto.getAttachmentId());
        verify(complaintEventService, never()).getTimelineEntry(any(), any(), any());
    }
}
