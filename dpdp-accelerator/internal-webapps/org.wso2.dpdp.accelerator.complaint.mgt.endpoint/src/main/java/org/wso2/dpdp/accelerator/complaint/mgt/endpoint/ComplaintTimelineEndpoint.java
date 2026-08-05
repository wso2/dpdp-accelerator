package org.wso2.dpdp.accelerator.complaint.mgt.endpoint;

import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.TimelineListResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.handler.ComplaintTimelineHandler;

import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/complaints/{complaintId}/timeline")
@Produces(MediaType.APPLICATION_JSON)
public class ComplaintTimelineEndpoint {

    private final ComplaintTimelineHandler timelineHandler;

    public ComplaintTimelineEndpoint() {
        this.timelineHandler = new ComplaintTimelineHandler();
    }

    public ComplaintTimelineEndpoint(ComplaintTimelineHandler timelineHandler) {
        this.timelineHandler = timelineHandler;
    }

    @GET
    public Response getTimeline(
            @HeaderParam("org-id") String orgId,
            @PathParam("complaintId") String complaintId,
            @QueryParam("since") String since,
            @QueryParam("isPublic") Boolean isPublic,
            @QueryParam("order") String order,
            @QueryParam("limit") Integer limit,
            @QueryParam("offset") Integer offset) {
        TimelineListResponseBean response =
                timelineHandler.getTimeline(orgId, complaintId, since, isPublic, order, limit, offset);
        return Response.ok(response).build();
    }
}
