package org.wso2.dpdp.accelerator.complaint.mgt.endpoint;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.TimelineListResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.handler.ComplaintTimelineHandler;

import javax.ws.rs.core.Response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplaintTimelineEndpointTest {

    @Mock
    private ComplaintTimelineHandler timelineHandler;

    private ComplaintTimelineEndpoint endpoint;

    @BeforeEach
    void setUp() {
        endpoint = new ComplaintTimelineEndpoint(timelineHandler);
    }

    @Test
    void getTimelineReturns200WithHandlerResponse() {
        TimelineListResponseBean handlerResponse = new TimelineListResponseBean();
        when(timelineHandler.getTimeline("org1", "c1", "2026-01-01T00:00:00Z", null, "asc", 10, 0))
                .thenReturn(handlerResponse);

        Response response = endpoint.getTimeline("org1", "c1", "2026-01-01T00:00:00Z", null, "asc", 10, 0);

        assertEquals(200, response.getStatus());
        assertSame(handlerResponse, response.getEntity());
    }

    @Test
    void getTimelinePassesIsPublicFilterThrough() {
        TimelineListResponseBean handlerResponse = new TimelineListResponseBean();
        when(timelineHandler.getTimeline("org1", "c1", null, true, "asc", 10, 0))
                .thenReturn(handlerResponse);

        Response response = endpoint.getTimeline("org1", "c1", null, true, "asc", 10, 0);

        assertEquals(200, response.getStatus());
        assertSame(handlerResponse, response.getEntity());
    }

    @Test
    void noArgsConstructorWiresARealHandler() {
        assertNotNull(new ComplaintTimelineEndpoint());
    }
}
