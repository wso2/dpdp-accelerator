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

package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.handler;

import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.ContentDisposition;
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
import org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.DAOConstants;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintAttachment;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintAttachmentService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintAttachmentDownloadResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintAttachmentResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintAttachmentService.UploadedFile;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintException;
import org.wso2.dpdp.accelerator.complaint.mgt.service.internal.ComplaintServiceDataHolder;

import javax.activation.DataHandler;
import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.expectThrows;
import static org.testng.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ComplaintAttachmentHandlerTest {

    private static final String ORG_ID = DAOConstants.DEFAULT_ORG_ID;

    @Mock
    private ComplaintService complaintService;
    @Mock
    private ComplaintAttachmentService complaintAttachmentService;
    @Mock
    private Attachment filePart;
    @Mock
    private DataHandler dataHandler;

    private ComplaintAttachmentHandler handler;

    @BeforeClass
    void seedConfigurationService() {
        // Normally bound by ComplaintServiceComponent's OSGi @Reference; ComplaintServiceUtil reads
        // it via ComplaintServiceDataHolder, so a test running outside a live Carbon environment
        // must seed it itself.
        ComplaintServiceDataHolder.getInstance().setConfigurationService(new DPDPConfigurationServiceImpl());
    }

    @AfterClass
    void clearConfigurationService() {
        ComplaintServiceDataHolder.getInstance().setConfigurationService(null);
    }

    @BeforeMethod
    void setUp() {
        MockitoAnnotations.openMocks(this);
        handler = new ComplaintAttachmentHandler(complaintService, complaintAttachmentService);
    }

    @AfterMethod
    void tearDown() {
        ComplaintServiceDataHolder.getInstance().setConfigurationService(new DPDPConfigurationServiceImpl());
    }

    private ComplaintAttachment attachment(String id, boolean isPublic) {
        return new ComplaintAttachment(id, ORG_ID, "c1", "a.pdf", "application/pdf", new byte[]{1}, isPublic, 1L);
    }

    private ComplaintAttachmentResponseDTO attachmentBean(String id, boolean isPublic) {
        return ComplaintAttachmentResponseDTO.from(attachment(id, isPublic));
    }

    private ComplaintAttachmentDownloadResponseDTO downloadBean(ComplaintAttachment attachment) {
        return new ComplaintAttachmentDownloadResponseDTO(attachment.getAttachmentId(), attachment.getFileName(),
                attachment.getContentType(), attachment.getFileData());
    }

    // ---- officer/admin ----

    @Test
    void uploadComplaintAttachmentsReadsFilePartsAndDelegatesToServiceWithGivenIsPublic() throws IOException {
        byte[] data = "file-content".getBytes();
        when(filePart.getDataHandler()).thenReturn(dataHandler);
        when(dataHandler.getInputStream()).thenReturn(new ByteArrayInputStream(data));
        when(filePart.getContentType()).thenReturn(MediaType.valueOf("application/pdf"));
        when(filePart.getContentDisposition()).thenReturn(new ContentDisposition("form-data; filename=\"a.pdf\""));
        when(complaintAttachmentService.uploadComplaintAttachments(eq(ORG_ID), eq("c1"), any(), eq(false),
                eq("officer1"), eq("Officer One"), eq("COMPLAINT_OFFICER")))
                .thenReturn(List.of(attachmentBean("att1", false)));

        List<ComplaintAttachmentResponseDTO> result = handler.uploadComplaintAttachments(ORG_ID, "c1",
                List.of(filePart), false, "officer1", "Officer One");

        assertEquals(1, result.size());
        assertEquals("att1", result.get(0).getAttachmentId());

        ArgumentCaptor<List<UploadedFile>> captor = ArgumentCaptor.forClass(List.class);
        verify(complaintAttachmentService).uploadComplaintAttachments(eq(ORG_ID), eq("c1"), captor.capture(),
                eq(false), eq("officer1"), eq("Officer One"), eq("COMPLAINT_OFFICER"));
        UploadedFile uploaded = captor.getValue().get(0);
        assertEquals("a.pdf", uploaded.getFileName());
        assertEquals("application/pdf", uploaded.getContentType());
        assertEquals(data.length, uploaded.getData().length);
    }

    @Test
    void uploadComplaintAttachmentsDefaultsNullIsPublicToTrue() {
        when(complaintAttachmentService.uploadComplaintAttachments(eq(ORG_ID), eq("c1"), any(), eq(true),
                eq("officer1"), eq("Officer One"), eq("COMPLAINT_OFFICER"))).thenReturn(List.of());

        handler.uploadComplaintAttachments(ORG_ID, "c1", null, null, "officer1", "Officer One");

        verify(complaintAttachmentService).uploadComplaintAttachments(eq(ORG_ID), eq("c1"), any(), eq(true),
                eq("officer1"), eq("Officer One"), eq("COMPLAINT_OFFICER"));
    }

    @Test
    void uploadComplaintAttachmentsDefaultsContentTypeToOctetStreamWhenMediaTypeMissing() throws IOException {
        when(filePart.getDataHandler()).thenReturn(dataHandler);
        when(dataHandler.getInputStream()).thenReturn(new ByteArrayInputStream("x".getBytes()));
        when(filePart.getContentType()).thenReturn(null);
        when(filePart.getContentDisposition()).thenReturn(null);
        when(complaintAttachmentService.uploadComplaintAttachments(eq(ORG_ID), eq("c1"), any(), eq(true),
                eq("officer1"), eq("Officer One"), eq("COMPLAINT_OFFICER"))).thenReturn(List.of());

        handler.uploadComplaintAttachments(ORG_ID, "c1", List.of(filePart), true, "officer1", "Officer One");

        ArgumentCaptor<List<UploadedFile>> captor = ArgumentCaptor.forClass(List.class);
        verify(complaintAttachmentService).uploadComplaintAttachments(eq(ORG_ID), eq("c1"), captor.capture(),
                eq(true), eq("officer1"), eq("Officer One"), eq("COMPLAINT_OFFICER"));
        UploadedFile uploaded = captor.getValue().get(0);
        assertEquals(MediaType.APPLICATION_OCTET_STREAM, uploaded.getContentType());
        assertNull(uploaded.getFileName());
    }

    @Test
    void uploadComplaintAttachmentsThrowsWhenTooManyFilePartsProvidedWithoutReadingAny() {
        DPDPConfigurationService configurationService = mock(DPDPConfigurationService.class);
        when(configurationService.getComplaintsAttachmentMaxFilesPerUpload()).thenReturn(2);
        ComplaintServiceDataHolder.getInstance().setConfigurationService(configurationService);
        List<Attachment> parts = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            parts.add(mock(Attachment.class));
        }

        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> handler.uploadComplaintAttachments(ORG_ID, "c1", parts, true, "officer1", "Officer One"));

        assertEquals("CO-4002", ex.getCode());
        // The count must be checked before any part is read - otherwise the exact memory-exhaustion
        // vector this cap exists to close (many parts, each read into heap before being rejected)
        // would still occur.
        for (Attachment part : parts) {
            verifyNoInteractions(part);
        }
        verifyNoInteractions(complaintAttachmentService);
    }

    @Test
    void uploadComplaintAttachmentsThrowsWhenFileExceedsMaxSizeWithoutBufferingItWhole() throws IOException {
        // An effectively-infinite source: if the handler buffered the whole part before checking
        // its size (the bug this test guards against), reading it to completion would never
        // return, so the test itself would hang rather than fail cleanly.
        CountingInfiniteInputStream infiniteStream = new CountingInfiniteInputStream();
        when(filePart.getDataHandler()).thenReturn(dataHandler);
        when(dataHandler.getInputStream()).thenReturn(infiniteStream);
        when(filePart.getContentType()).thenReturn(MediaType.valueOf("application/pdf"));
        when(filePart.getContentDisposition()).thenReturn(new ContentDisposition("form-data; filename=\"big.pdf\""));

        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> handler.uploadComplaintAttachments(ORG_ID, "c1", List.of(filePart), true, "officer1",
                        "Officer One"));

        assertEquals("CO-4002", ex.getCode());
        assertTrue(ex.getDescription().contains("big.pdf"));
        // ComplaintServiceUtil.getAttachmentMaxSizeBytes() defaults to 10 MB outside a real Carbon environment
        // (no dpdp-accelerator.xml on disk) - confirms the handler stopped reading shortly after
        // that, not somewhere arbitrarily far into the (infinite) stream.
        assertTrue(infiniteStream.getBytesServed() < 20L * 1024 * 1024);
        verifyNoInteractions(complaintAttachmentService);
    }

    /** Reports data forever, so a caller can only pass this test by not trying to drain it. */
    private static final class CountingInfiniteInputStream extends InputStream {

        private long bytesServed;

        @Override
        public int read() {
            bytesServed++;
            return 0;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            bytesServed += len;
            return len;
        }

        long getBytesServed() {
            return bytesServed;
        }
    }

    @Test
    void downloadAttachmentIsUnrestricted() {
        byte[] content = "hello".getBytes();
        ComplaintAttachment attachment = new ComplaintAttachment("att1", ORG_ID, "c1", "a.pdf", "application/pdf",
                content, false, 100L);
        when(complaintAttachmentService.downloadAttachment(ORG_ID, "c1", "att1", false))
                .thenReturn(downloadBean(attachment));

        ComplaintAttachmentDownloadResponseDTO response = handler.downloadAttachment(ORG_ID, "c1", "att1");

        assertEquals("att1", response.getAttachmentId());
        assertEquals(Base64.getEncoder().encodeToString(content), response.getContent());
    }

    // ---- Data Principal ----

    @Test
    void uploadOwnComplaintAttachmentsDelegatesOwnershipEnforcementToTheService() {
        // Ownership is enforced by the service (uploadOwnComplaintAttachments), not the handler -
        // see ComplaintAttachmentServiceImpl for the defense-in-depth check.
        when(complaintAttachmentService.uploadOwnComplaintAttachments(eq(ORG_ID), eq("c1"), eq("user1"),
                eq("User One"), any())).thenReturn(List.of(attachmentBean("att1", true)));

        List<ComplaintAttachmentResponseDTO> result =
                handler.uploadOwnComplaintAttachments(ORG_ID, "c1", "user1", "User One", List.of());

        assertEquals(1, result.size());
        verify(complaintAttachmentService).uploadOwnComplaintAttachments(eq(ORG_ID), eq("c1"), eq("user1"),
                eq("User One"), any());
    }

    @Test
    void downloadOwnAttachmentDelegatesOwnershipEnforcementToTheService() {
        // Ownership is enforced by the service (downloadOwnAttachment), not the handler - see
        // ComplaintAttachmentServiceImpl for the defense-in-depth check.
        ComplaintAttachment attachment = attachment("att1", true);
        when(complaintAttachmentService.downloadOwnAttachment(ORG_ID, "c1", "user1", "att1"))
                .thenReturn(downloadBean(attachment));

        ComplaintAttachmentDownloadResponseDTO response =
                handler.downloadOwnAttachment(ORG_ID, "c1", "user1", "att1");

        assertEquals("att1", response.getAttachmentId());
        verify(complaintAttachmentService).downloadOwnAttachment(ORG_ID, "c1", "user1", "att1");
    }

}
