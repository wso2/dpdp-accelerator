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
