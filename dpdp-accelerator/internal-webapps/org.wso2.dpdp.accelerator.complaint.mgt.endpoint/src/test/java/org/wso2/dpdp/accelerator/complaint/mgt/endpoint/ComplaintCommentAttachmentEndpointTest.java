package org.wso2.dpdp.accelerator.complaint.mgt.endpoint;

import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintAttachmentResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.handler.ComplaintAttachmentHandler;

import javax.ws.rs.core.Response;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplaintCommentAttachmentEndpointTest {

    @Mock
    private ComplaintAttachmentHandler attachmentHandler;
    @Mock
    private FormDataBodyPart filePart;

    private ComplaintCommentAttachmentEndpoint endpoint;

    @BeforeEach
    void setUp() {
        endpoint = new ComplaintCommentAttachmentEndpoint(attachmentHandler);
    }

    @Test
    void uploadCommentAttachmentReturns201WithHandlerResponse() {
        List<ComplaintAttachmentResponseBean> handlerResponse = List.of();
        when(attachmentHandler.uploadCommentAttachments("org1", "c1", "e1", "user1", List.of(filePart)))
                .thenReturn(handlerResponse);

        Response response = endpoint.uploadCommentAttachment("org1", "c1", "e1", "user1", List.of(filePart));

        assertEquals(201, response.getStatus());
        assertSame(handlerResponse, response.getEntity());
    }

    @Test
    void noArgsConstructorWiresARealHandler() {
        assertNotNull(new ComplaintCommentAttachmentEndpoint());
    }
}
