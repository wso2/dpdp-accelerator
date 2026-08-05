package org.wso2.dpdp.accelerator.complaint.mgt.endpoint;

import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintAttachmentDownloadResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintAttachmentResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.handler.ComplaintAttachmentHandler;

import javax.ws.rs.core.Response;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplaintAttachmentEndpointTest {

    @Mock
    private ComplaintAttachmentHandler attachmentHandler;
    @Mock
    private FormDataBodyPart filePart;

    private ComplaintAttachmentEndpoint endpoint;

    @BeforeEach
    void setUp() {
        endpoint = new ComplaintAttachmentEndpoint(attachmentHandler);
    }

    @Test
    void uploadComplaintAttachmentReturns201WithHandlerResponse() {
        List<ComplaintAttachmentResponseBean> handlerResponse = List.of();
        when(attachmentHandler.uploadComplaintAttachments("org1", "c1", List.of(filePart)))
                .thenReturn(handlerResponse);

        Response response = endpoint.uploadComplaintAttachment("org1", "c1", List.of(filePart));

        assertEquals(201, response.getStatus());
        assertSame(handlerResponse, response.getEntity());
    }

    @Test
    void downloadComplaintAttachmentReturns200WithHandlerResponse() {
        ComplaintAttachmentDownloadResponseBean handlerResponse =
                new ComplaintAttachmentDownloadResponseBean("att1", "a.pdf", "application/pdf", new byte[]{1});
        when(attachmentHandler.downloadAttachment("org1", "c1", "att1", "USER")).thenReturn(handlerResponse);

        Response response = endpoint.downloadComplaintAttachment("org1", "USER", "c1", "att1");

        assertEquals(200, response.getStatus());
        assertSame(handlerResponse, response.getEntity());
    }

    @Test
    void noArgsConstructorWiresARealHandler() {
        assertNotNull(new ComplaintAttachmentEndpoint());
    }
}
