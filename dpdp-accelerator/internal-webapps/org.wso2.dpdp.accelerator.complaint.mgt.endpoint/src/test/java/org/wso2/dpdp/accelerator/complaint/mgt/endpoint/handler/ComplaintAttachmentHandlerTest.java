package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.handler;

import org.glassfish.jersey.media.multipart.ContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintAttachmentDownloadResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintAttachmentResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintAttachmentService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintAttachmentService.UploadedFile;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintAttachmentDTO;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplaintAttachmentHandlerTest {

    @Mock
    private ComplaintAttachmentService complaintAttachmentService;
    @Mock
    private FormDataBodyPart filePart;
    @Mock
    private ContentDisposition contentDisposition;

    private ComplaintAttachmentHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ComplaintAttachmentHandler(complaintAttachmentService);
    }

    @Test
    void uploadComplaintAttachmentsReadsFilePartsAndDelegatesToService() {
        byte[] data = "file-content".getBytes();
        when(filePart.getValueAs(java.io.InputStream.class)).thenReturn(new ByteArrayInputStream(data));
        when(filePart.getMediaType()).thenReturn(MediaType.valueOf("application/pdf"));
        when(filePart.getContentDisposition()).thenReturn(contentDisposition);
        when(contentDisposition.getFileName()).thenReturn("a.pdf");
        when(complaintAttachmentService.uploadComplaintAttachments(any(), any(), any()))
                .thenReturn(List.of(new ComplaintAttachmentDTO("att1", "a.pdf", "application/pdf", data.length)));

        List<ComplaintAttachmentResponseBean> result =
                handler.uploadComplaintAttachments("org1", "c1", List.of(filePart));

        assertEquals(1, result.size());
        assertEquals("att1", result.get(0).getAttachmentId());

        ArgumentCaptor<List<UploadedFile>> captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(complaintAttachmentService)
                .uploadComplaintAttachments(org.mockito.ArgumentMatchers.eq("org1"),
                        org.mockito.ArgumentMatchers.eq("c1"), captor.capture());
        UploadedFile uploaded = captor.getValue().get(0);
        assertEquals("a.pdf", uploaded.getFileName());
        assertEquals("application/pdf", uploaded.getContentType());
        assertEquals(data.length, uploaded.getData().length);
    }

    @Test
    void uploadComplaintAttachmentsDefaultsContentTypeToOctetStreamWhenMediaTypeMissing() {
        when(filePart.getValueAs(java.io.InputStream.class))
                .thenReturn(new ByteArrayInputStream("x".getBytes()));
        when(filePart.getMediaType()).thenReturn(null);
        when(filePart.getContentDisposition()).thenReturn(null);
        when(complaintAttachmentService.uploadComplaintAttachments(any(), any(), any())).thenReturn(List.of());

        handler.uploadComplaintAttachments("org1", "c1", List.of(filePart));

        ArgumentCaptor<List<UploadedFile>> captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(complaintAttachmentService)
                .uploadComplaintAttachments(org.mockito.ArgumentMatchers.eq("org1"),
                        org.mockito.ArgumentMatchers.eq("c1"), captor.capture());
        UploadedFile uploaded = captor.getValue().get(0);
        assertEquals(MediaType.APPLICATION_OCTET_STREAM, uploaded.getContentType());
        assertNull(uploaded.getFileName());
    }

    @Test
    void uploadComplaintAttachmentsHandlesNullFilePartList() {
        when(complaintAttachmentService.uploadComplaintAttachments(any(), any(), any())).thenReturn(List.of());

        List<ComplaintAttachmentResponseBean> result =
                handler.uploadComplaintAttachments("org1", "c1", null);

        assertEquals(0, result.size());
        ArgumentCaptor<List<UploadedFile>> captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(complaintAttachmentService)
                .uploadComplaintAttachments(org.mockito.ArgumentMatchers.eq("org1"),
                        org.mockito.ArgumentMatchers.eq("c1"), captor.capture());
        assertEquals(0, captor.getValue().size());
    }

    @Test
    void uploadCommentAttachmentsDelegatesWithCommentIdAndActorUserId() {
        when(complaintAttachmentService.uploadCommentAttachments(any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        handler.uploadCommentAttachments("org1", "c1", "e1", "user1", null);

        org.mockito.Mockito.verify(complaintAttachmentService)
                .uploadCommentAttachments("org1", "c1", "e1", "user1", List.of());
    }

    @Test
    void downloadAttachmentBase64EncodesContentInTheResponseBean() {
        byte[] content = "hello".getBytes();
        ComplaintAttachmentDTO dto = new ComplaintAttachmentDTO("att1", "a.pdf", "application/pdf", content.length);
        dto.setContent(content);
        when(complaintAttachmentService.downloadAttachment("org1", "c1", "att1", "USER")).thenReturn(dto);

        ComplaintAttachmentDownloadResponseBean response =
                handler.downloadAttachment("org1", "c1", "att1", "USER");

        assertEquals("att1", response.getAttachmentId());
        assertEquals(Base64.getEncoder().encodeToString(content), response.getContent());
    }

    @Test
    void noArgsConstructorWiresRealServiceImplementations() {
        assertNotNull(new ComplaintAttachmentHandler());
    }
}
