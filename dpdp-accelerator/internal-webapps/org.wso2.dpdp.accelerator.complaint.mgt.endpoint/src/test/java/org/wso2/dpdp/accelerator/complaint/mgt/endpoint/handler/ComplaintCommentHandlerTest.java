package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintCommentCreateResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintMessageRequestBean;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintEventService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintCommentDTO;

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
        ComplaintCommentDTO dto = new ComplaintCommentDTO("e1", "user1", "USER", "hello", true, "OPEN",
                "IN_PROGRESS", 100L);
        when(complaintEventService.addComment("org1", "c1", "user1", "USER", "hello", true, "IN_PROGRESS"))
                .thenReturn(dto);

        ComplaintCommentCreateResponseBean response = handler.addComment("org1", "c1", request);

        assertEquals("e1", response.getId());
        assertEquals("IN_PROGRESS", response.getToStatus());
    }

    @Test
    void addCommentDefaultsIsPublicToFalseAndFieldsToNullWhenRequestIsNull() {
        ComplaintCommentDTO dto = new ComplaintCommentDTO("e1", null, null, null, false, null, null, 100L);
        when(complaintEventService.addComment(eq("org1"), eq("c1"), isNull(), isNull(), isNull(), eq(false),
                isNull())).thenReturn(dto);

        ComplaintCommentCreateResponseBean response = handler.addComment("org1", "c1", null);

        assertEquals("e1", response.getId());
        assertNull(response.getActorUserId());
    }

    @Test
    void noArgsConstructorWiresRealEventService() {
        assertNotNull(new ComplaintCommentHandler());
    }
}
