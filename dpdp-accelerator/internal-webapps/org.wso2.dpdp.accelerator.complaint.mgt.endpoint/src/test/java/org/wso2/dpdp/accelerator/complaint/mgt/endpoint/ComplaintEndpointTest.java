package org.wso2.dpdp.accelerator.complaint.mgt.endpoint;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintCreateRequestBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintCreateResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintListResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintRecordBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintStatusUpdateRequestBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintStatusUpdateResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.handler.ComplaintHandler;

import javax.ws.rs.core.Response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplaintEndpointTest {

    @Mock
    private ComplaintHandler complaintHandler;

    private ComplaintEndpoint endpoint;

    @BeforeEach
    void setUp() {
        endpoint = new ComplaintEndpoint(complaintHandler);
    }

    @Test
    void createComplaintReturns201WithHandlerResponse() {
        ComplaintCreateRequestBean request = new ComplaintCreateRequestBean();
        ComplaintCreateResponseBean handlerResponse = new ComplaintCreateResponseBean();
        when(complaintHandler.createComplaint("org1", request)).thenReturn(handlerResponse);

        Response response = endpoint.createComplaint("org1", request);

        assertEquals(201, response.getStatus());
        assertSame(handlerResponse, response.getEntity());
    }

    @Test
    void listComplaintsReturns200WithHandlerResponse() {
        ComplaintListResponseBean handlerResponse = new ComplaintListResponseBean();
        when(complaintHandler.listComplaints("org1", "OPEN", "HIGH", "user1", 10, 0, "updatedTime"))
                .thenReturn(handlerResponse);

        Response response = endpoint.listComplaints("org1", "OPEN", "HIGH", "user1", 10, 0, "updatedTime");

        assertEquals(200, response.getStatus());
        assertSame(handlerResponse, response.getEntity());
    }

    @Test
    void getComplaintReturns200WithHandlerResponse() {
        ComplaintRecordBean handlerResponse = new ComplaintRecordBean();
        when(complaintHandler.getComplaint("org1", "c1")).thenReturn(handlerResponse);

        Response response = endpoint.getComplaint("org1", "c1");

        assertEquals(200, response.getStatus());
        assertSame(handlerResponse, response.getEntity());
    }

    @Test
    void updateComplaintStatusReturns200WithHandlerResponse() {
        ComplaintStatusUpdateRequestBean request = new ComplaintStatusUpdateRequestBean();
        ComplaintStatusUpdateResponseBean handlerResponse = new ComplaintStatusUpdateResponseBean();
        when(complaintHandler.updateStatus("org1", "c1", request)).thenReturn(handlerResponse);

        Response response = endpoint.updateComplaintStatus("org1", "c1", request);

        assertEquals(200, response.getStatus());
        assertSame(handlerResponse, response.getEntity());
    }

    @Test
    void noArgsConstructorWiresARealHandler() {
        assertNotNull(new ComplaintEndpoint());
    }
}
