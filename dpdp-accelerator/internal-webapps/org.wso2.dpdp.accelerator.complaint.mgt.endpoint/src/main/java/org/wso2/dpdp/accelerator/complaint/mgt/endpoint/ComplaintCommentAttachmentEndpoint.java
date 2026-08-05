package org.wso2.dpdp.accelerator.complaint.mgt.endpoint;

import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintAttachmentResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.handler.ComplaintAttachmentHandler;

import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

@Path("/complaints/{complaintId}/comments/{commentId}/attachments")
@Produces(MediaType.APPLICATION_JSON)
public class ComplaintCommentAttachmentEndpoint {

    private final ComplaintAttachmentHandler attachmentHandler;

    public ComplaintCommentAttachmentEndpoint() {
        this.attachmentHandler = new ComplaintAttachmentHandler();
    }

    public ComplaintCommentAttachmentEndpoint(ComplaintAttachmentHandler attachmentHandler) {
        this.attachmentHandler = attachmentHandler;
    }

    /** POST /complaints/{complaintId}/comments/{commentId}/attachments - bind files to a timeline entry. */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadCommentAttachment(
            @HeaderParam("org-id") String orgId,
            @PathParam("complaintId") String complaintId,
            @PathParam("commentId") String commentId,
            @FormDataParam("actorUserId") String actorUserId,
            @FormDataParam("file") List<FormDataBodyPart> fileParts) {
        List<ComplaintAttachmentResponseBean> response =
                attachmentHandler.uploadCommentAttachments(orgId, complaintId, commentId, actorUserId, fileParts);
        return Response.status(Response.Status.CREATED).entity(response).build();
    }
}
