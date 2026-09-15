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

package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.api;

import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.CategoryListResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintCreateRequestDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintCreateResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintListResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintQueueStatsResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintRecordDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintStatusUpdateRequestDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintStatusUpdateResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.handler.ComplaintHandler;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Complaint Management (officer/admin) namespace - see complaint-server-API.yaml. Every operation
 * requires complaints:{read,write}:any, enforced per-route by Carbon's own valve pipeline before
 * this webapp ever sees the request; the acting officer/system's identity comes from
 * {@link PrivilegedCarbonContext}, which that same valve pipeline populates for every request.
 */
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

    private static final String ACTOR_ROLE_COMPLAINT_OFFICER = "COMPLAINT_OFFICER";

    private String currentOrgId() {
        return PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain();
    }

    @POST
    public Response createComplaint(ComplaintCreateRequestDTO request) {
        String callerUsername = PrivilegedCarbonContext.getThreadLocalCarbonContext().getUsername();
        ComplaintCreateResponseDTO response = complaintHandler.createComplaint(currentOrgId(),
                callerUsername, ACTOR_ROLE_COMPLAINT_OFFICER, request);
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @GET
    public Response listComplaints(
            @QueryParam("status") String status,
            @QueryParam("priority") String priority,
            @QueryParam("userId") String userId,
            @QueryParam("search") String search,
            @QueryParam("limit") Integer limit,
            @QueryParam("offset") Integer offset,
            @QueryParam("sort") String sort) {
        ComplaintListResponseDTO response = complaintHandler.listComplaints(currentOrgId(), status, priority, userId,
                search, limit, offset, sort);
        return Response.ok(response).build();
    }

    @GET
    @Path("/stats")
    public Response getQueueStats() {
        ComplaintQueueStatsResponseDTO response = complaintHandler.getQueueStats(currentOrgId());
        return Response.ok(response).build();
    }

    @GET
    @Path("/categories")
    public Response getCategories() {
        CategoryListResponseDTO response = complaintHandler.getCategories();
        return Response.ok(response).build();
    }

    @GET
    @Path("/{complaintId}")
    public Response getComplaint(@PathParam("complaintId") String complaintId) {
        ComplaintRecordDTO response = complaintHandler.getComplaint(currentOrgId(), complaintId);
        return Response.ok(response).build();
    }

    @POST
    @Path("/{complaintId}/status")
    public Response updateComplaintStatus(
            @PathParam("complaintId") String complaintId,
            ComplaintStatusUpdateRequestDTO request) {
        String callerUsername = PrivilegedCarbonContext.getThreadLocalCarbonContext().getUsername();
        ComplaintStatusUpdateResponseDTO response = complaintHandler.updateStatus(currentOrgId(), complaintId,
                callerUsername, callerUsername, ACTOR_ROLE_COMPLAINT_OFFICER, request);
        return Response.ok(response).build();
    }
}
