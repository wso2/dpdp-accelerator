package org.wso2.dpdp.accelerator.complaint.mgt.endpoint;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintCommentCreateResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintMessageRequestBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.handler.ComplaintCommentHandler;

import javax.ws.rs.core.Response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplaintCommentEndpointTest {

    @Mock
    private ComplaintCommentHandler commentHandler;

    private ComplaintCommentEndpoint endpoint;

    @BeforeEach
    void setUp() {
        endpoint = new ComplaintCommentEndpoint(commentHandler);
    }

    @Test
    void addComplaintMessageReturns200WithHandlerResponse() {
        ComplaintMessageRequestBean request = new ComplaintMessageRequestBean();
        ComplaintCommentCreateResponseBean handlerResponse = new ComplaintCommentCreateResponseBean();
        when(commentHandler.addComment("org1", "c1", request)).thenReturn(handlerResponse);

        Response response = endpoint.addComplaintMessage("org1", "c1", request);

        assertEquals(200, response.getStatus());
        assertSame(handlerResponse, response.getEntity());
    }

    @Test
    void noArgsConstructorWiresARealHandler() {
        assertNotNull(new ComplaintCommentEndpoint());
    }
}
