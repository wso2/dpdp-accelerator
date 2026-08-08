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
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintAttachmentDownloadResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintAttachmentResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.handler.ComplaintAttachmentHandler;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

@Path("/complaints/{complaintId}/attachments")
@Produces(MediaType.APPLICATION_JSON)
public class ComplaintAttachmentEndpoint {

    private final ComplaintAttachmentHandler attachmentHandler;

    public ComplaintAttachmentEndpoint() {
        this.attachmentHandler = new ComplaintAttachmentHandler();
    }

    public ComplaintAttachmentEndpoint(ComplaintAttachmentHandler attachmentHandler) {
        this.attachmentHandler = attachmentHandler;
    }

    /** POST /complaints/{complaintId}/attachments - bind one or more files directly to the complaint. */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadComplaintAttachment(
            @HeaderParam("org-id") String orgId,
            @PathParam("complaintId") String complaintId,
            @FormDataParam("file") List<FormDataBodyPart> fileParts) {
        List<ComplaintAttachmentResponseBean> response =
                attachmentHandler.uploadComplaintAttachments(orgId, complaintId, fileParts);
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    /** GET /complaints/{complaintId}/attachments/{attachmentId} - metadata + base64 content in one JSON body. */
    @GET
    @Path("/{attachmentId}")
    public Response downloadComplaintAttachment(
            @HeaderParam("org-id") String orgId,
            @HeaderParam("actor-role") String actorRole,
            @PathParam("complaintId") String complaintId,
            @PathParam("attachmentId") String attachmentId) {
        ComplaintAttachmentDownloadResponseBean response =
                attachmentHandler.downloadAttachment(orgId, complaintId, attachmentId, actorRole);
        return Response.ok(response).build();
    }
}
