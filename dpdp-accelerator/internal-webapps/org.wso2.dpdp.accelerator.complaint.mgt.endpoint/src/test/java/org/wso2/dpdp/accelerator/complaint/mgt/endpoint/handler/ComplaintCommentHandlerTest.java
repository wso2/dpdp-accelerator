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

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.DAOConstants;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintEventService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintCommentCreateResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintMessageRequestDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.MeComplaintMessageRequestDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ComplaintCommentHandlerTest {

    private static final String ORG_ID = DAOConstants.DEFAULT_ORG_ID;

    @Mock
    private ComplaintService complaintService;
    @Mock
    private ComplaintEventService complaintEventService;

    private ComplaintCommentHandler handler;

    @BeforeMethod
    void setUp() {
        MockitoAnnotations.openMocks(this);
        handler = new ComplaintCommentHandler(complaintService, complaintEventService);
    }

    @Test
    void addCommentPassesResolvedIdentityAndRequestFieldsThroughToEventService() {
        ComplaintMessageRequestDTO request = new ComplaintMessageRequestDTO();
        request.setMessage("hello");
        request.setPublic(true);
        request.setToStatus("IN_PROGRESS");
        ComplaintEvent event = new ComplaintEvent("e1", ORG_ID, "c1", "officer1", "Officer One", "COMPLAINT_OFFICER",
                true, "hello", "OPEN", "IN_PROGRESS", 100L);
        when(complaintEventService.addComment(ORG_ID, "c1", "officer1", "Officer One", "COMPLAINT_OFFICER", "hello",
                true, "IN_PROGRESS")).thenReturn(ComplaintCommentCreateResponseDTO.from(event));

        ComplaintCommentCreateResponseDTO response =
                handler.addComment(ORG_ID, "c1", "officer1", "Officer One", "COMPLAINT_OFFICER", request);

        assertEquals("e1", response.getId());
        assertEquals("IN_PROGRESS", response.getToStatus());
    }

    @Test
    void addCommentThrowsWhenRequestIsNull() {
        // isPublic is a required field per the spec - a null request (or a request missing
        // isPublic) must be rejected, not silently treated as isPublic=false (an internal note).
        expectThrows(ComplaintException.class,
                () -> handler.addComment(ORG_ID, "c1", "officer1", "Officer One", "COMPLAINT_OFFICER", null));

        verifyNoInteractions(complaintEventService);
    }

    @Test
    void addCommentThrowsWhenIsPublicIsMissingFromRequest() {
        ComplaintMessageRequestDTO request = new ComplaintMessageRequestDTO();
        request.setMessage("hello");

        expectThrows(ComplaintException.class,
                () -> handler.addComment(ORG_ID, "c1", "officer1", "Officer One", "COMPLAINT_OFFICER", request));

        verifyNoInteractions(complaintEventService);
    }

    @Test
    void addCommentHonorsExplicitIsPublicFalse() {
        ComplaintMessageRequestDTO request = new ComplaintMessageRequestDTO();
        request.setMessage("internal note");
        request.setPublic(false);
        ComplaintEvent event = new ComplaintEvent("e1", ORG_ID, "c1", "officer1", "Officer One", "COMPLAINT_OFFICER",
                false, "internal note", null, null, 100L);
        when(complaintEventService.addComment(eq(ORG_ID), eq("c1"), eq("officer1"), eq("Officer One"),
                eq("COMPLAINT_OFFICER"), eq("internal note"), eq(false), isNull()))
                .thenReturn(ComplaintCommentCreateResponseDTO.from(event));

        ComplaintCommentCreateResponseDTO response =
                handler.addComment(ORG_ID, "c1", "officer1", "Officer One", "COMPLAINT_OFFICER", request);

        assertEquals("e1", response.getId());
    }

    @Test
    void addOwnCommentVerifiesOwnershipAndForcesUserRoleAndPublic() {
        MeComplaintMessageRequestDTO request = new MeComplaintMessageRequestDTO();
        request.setMessage("hello");
        request.setToStatus("RESOLVED");
        ComplaintEvent event = new ComplaintEvent("e1", ORG_ID, "c1", "user1", "User One", "USER", true, "hello",
                "OPEN", "RESOLVED", 100L);
        when(complaintEventService.addComment(ORG_ID, "c1", "user1", "User One", "USER", "hello", true, "RESOLVED"))
                .thenReturn(ComplaintCommentCreateResponseDTO.from(event));

        ComplaintCommentCreateResponseDTO response =
                handler.addOwnComment(ORG_ID, "c1", "user1", "User One", request);

        assertEquals("e1", response.getId());
        assertEquals("USER", response.getActorRole());
        verify(complaintService).getOwnedComplaint(ORG_ID, "c1", "user1");
    }

}
