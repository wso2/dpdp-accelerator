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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintCommentCreateResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintMessageRequestBean;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintEventService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplaintCommentHandlerTest {

    @Mock
    private ComplaintEventService complaintEventService;

    private ComplaintCommentHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ComplaintCommentHandler(complaintEventService);
    }

    @Test
    void addCommentPassesRequestFieldsThroughToEventService() {
        ComplaintMessageRequestBean request = new ComplaintMessageRequestBean();
        request.setActorUserId("user1");
        request.setActorRole("USER");
        request.setMessage("hello");
        request.setPublic(true);
        request.setToStatus("IN_PROGRESS");
        ComplaintEvent event = new ComplaintEvent("e1", "org1", "c1", "user1", "USER", true, "hello", "OPEN",
                "IN_PROGRESS", 100L);
        when(complaintEventService.addComment("org1", "c1", "user1", "USER", "hello", true, "IN_PROGRESS"))
                .thenReturn(event);

        ComplaintCommentCreateResponseBean response = handler.addComment("org1", "c1", request);

        assertEquals("e1", response.getId());
        assertEquals("IN_PROGRESS", response.getToStatus());
    }

    @Test
    void addCommentDefaultsIsPublicToFalseAndFieldsToNullWhenRequestIsNull() {
        ComplaintEvent event = new ComplaintEvent("e1", "org1", "c1", null, null, false, null, null, null, 100L);
        when(complaintEventService.addComment(eq("org1"), eq("c1"), isNull(), isNull(), isNull(), eq(false),
                isNull())).thenReturn(event);

        ComplaintCommentCreateResponseBean response = handler.addComment("org1", "c1", null);

        assertEquals("e1", response.getId());
        assertNull(response.getActorUserId());
    }

    @Test
    void noArgsConstructorWiresRealEventService() {
        assertNotNull(new ComplaintCommentHandler());
    }
}
