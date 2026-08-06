package org.wso2.dpdp.accelerator.complaint.mgt.endpoint;

import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.CategoryListResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintCreateRequestBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintCreateResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintListResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintRecordBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintStatusUpdateRequestBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintStatusUpdateResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.handler.ComplaintHandler;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/complaints")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ComplaintEndpoint {

    private final ComplaintHandler complaintHandler;

    public ComplaintEndpoint() {
        this.complaintHandler = new ComplaintHandler();
    }

    public ComplaintEndpoint(ComplaintHandler complaintHandler) {
        this.complaintHandler = complaintHandler;
    }

    @POST
    public Response createComplaint(@HeaderParam("org-id") String orgId, ComplaintCreateRequestBean request) {
        ComplaintCreateResponseBean response = complaintHandler.createComplaint(orgId, request);
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @GET
    public Response listComplaints(
            @HeaderParam("org-id") String orgId,
            @QueryParam("status") String status,
            @QueryParam("priority") String priority,
            @QueryParam("userId") String userId,
            @QueryParam("limit") Integer limit,
            @QueryParam("offset") Integer offset,
            @QueryParam("sort") String sort) {
        ComplaintListResponseBean response =
                complaintHandler.listComplaints(orgId, status, priority, userId, limit, offset, sort);
        return Response.ok(response).build();
    }

    @GET
    @Path("/categories")
    public Response getCategories() {
        CategoryListResponseBean response = complaintHandler.getCategories();
        return Response.ok(response).build();
    }

    @GET
    @Path("/{complaintId}")
    public Response getComplaint(
            @HeaderParam("org-id") String orgId,
            @PathParam("complaintId") String complaintId) {
        ComplaintRecordBean response = complaintHandler.getComplaint(orgId, complaintId);
        return Response.ok(response).build();
    }

    @POST
    @Path("/{complaintId}/status")
    public Response updateComplaintStatus(
            @HeaderParam("org-id") String orgId,
            @PathParam("complaintId") String complaintId,
            ComplaintStatusUpdateRequestBean request) {
        ComplaintStatusUpdateResponseBean response = complaintHandler.updateStatus(orgId, complaintId, request);
        return Response.ok(response).build();
    }
}
