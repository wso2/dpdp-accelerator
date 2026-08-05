package org.wso2.dpdp.accelerator.complaint.mgt.endpoint;

import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintCommentCreateResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintMessageRequestBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.handler.ComplaintCommentHandler;

import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/complaints/{complaintId}/comments")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ComplaintCommentEndpoint {

    private final ComplaintCommentHandler commentHandler;

    public ComplaintCommentEndpoint() {
        this.commentHandler = new ComplaintCommentHandler();
    }

    public ComplaintCommentEndpoint(ComplaintCommentHandler commentHandler) {
        this.commentHandler = commentHandler;
    }

    @POST
    public Response addComplaintMessage(
            @HeaderParam("org-id") String orgId,
            @PathParam("complaintId") String complaintId,
            ComplaintMessageRequestBean request) {
        ComplaintCommentCreateResponseBean response = commentHandler.addComment(orgId, complaintId, request);
        return Response.ok(response).build();
    }
}
