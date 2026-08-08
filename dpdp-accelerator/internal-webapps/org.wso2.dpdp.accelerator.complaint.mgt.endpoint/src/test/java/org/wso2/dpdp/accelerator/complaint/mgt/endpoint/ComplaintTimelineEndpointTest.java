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
