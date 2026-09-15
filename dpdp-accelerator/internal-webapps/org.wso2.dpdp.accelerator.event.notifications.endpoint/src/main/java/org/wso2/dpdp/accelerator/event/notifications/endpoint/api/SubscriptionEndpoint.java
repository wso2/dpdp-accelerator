/**
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 * <p>
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 *     http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.dpdp.accelerator.event.notifications.endpoint.api;

import org.wso2.dpdp.accelerator.event.notifications.endpoint.handler.SubscriptionHandler;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.constants.EventNotificationEndpointConstants;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDeliveryDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionEventHistoryDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.model.PaginatedResult;
import org.wso2.dpdp.accelerator.common.util.DPDPTenantContext;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.function.Supplier;

@Path("/subscriptions")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class SubscriptionEndpoint {

    private final SubscriptionHandler subscriptionHandler;
    private final Supplier<String> organizationIdSupplier;

    public SubscriptionEndpoint() {
        this.subscriptionHandler = new SubscriptionHandler();
        this.organizationIdSupplier = DPDPTenantContext::getOrganizationId;
    }

    public SubscriptionEndpoint(SubscriptionHandler subscriptionHandler) {
        this.subscriptionHandler = subscriptionHandler;
        this.organizationIdSupplier = DPDPTenantContext::getOrganizationId;
    }

    public SubscriptionEndpoint(SubscriptionHandler subscriptionHandler, Supplier<String> organizationIdSupplier) {
        this.subscriptionHandler = subscriptionHandler;
        this.organizationIdSupplier = organizationIdSupplier;
    }

    @POST
    public Response createSubscription(SubscriptionDTO request) {
        String orgId = organizationIdSupplier.get();
        SubscriptionDTO dto = subscriptionHandler.createSubscription(orgId, request);
        return Response.status(Response.Status.CREATED).entity(dto).build();
    }

    @GET
    public Response listSubscriptions(
            @QueryParam("status") String status,
            @QueryParam("purposes") String purposes,
            @QueryParam("search") String search,
            @QueryParam("limit") @DefaultValue(EventNotificationEndpointConstants.DEFAULT_LIMIT_STR) int limit,
            @QueryParam("offset") @DefaultValue(EventNotificationEndpointConstants.DEFAULT_OFFSET_STR) int offset,
            @QueryParam("sort") String sort) {
        PaginatedResult<SubscriptionDTO> result = subscriptionHandler.listSubscriptions(
                organizationIdSupplier.get(), status, purposes, search, limit, offset, sort);
        return Response.ok(result).build();
    }

    @GET
    @Path("/{subscriptionId}")
    public Response getSubscription(
            @PathParam("subscriptionId") String subscriptionId) {
        SubscriptionDTO dto = subscriptionHandler.getSubscription(organizationIdSupplier.get(), subscriptionId);
        return Response.ok(dto).build();
    }

    @DELETE
    @Path("/{subscriptionId}")
    public Response deleteSubscription(
            @PathParam("subscriptionId") String subscriptionId) {
        SubscriptionDTO dto = subscriptionHandler.deleteSubscription(organizationIdSupplier.get(), subscriptionId);
        return Response.ok(dto).build();
    }

    @POST
    @Path("/{subscriptionId}/verify")
    public Response retryVerification(
            @PathParam("subscriptionId") String subscriptionId) {
        SubscriptionDTO dto = subscriptionHandler.retryVerification(organizationIdSupplier.get(), subscriptionId);
        return Response.ok(dto).build();
    }

    @GET
    @Path("/{subscriptionId}/events")
    public Response listSubscriptionEvents(
            @PathParam("subscriptionId") String subscriptionId,
            @QueryParam("limit") @DefaultValue(EventNotificationEndpointConstants.DEFAULT_LIMIT_STR) int limit,
            @QueryParam("offset") @DefaultValue(EventNotificationEndpointConstants.DEFAULT_OFFSET_STR) int offset) {
        PaginatedResult<SubscriptionDeliveryDTO> result = subscriptionHandler.listSubscriptionEvents(
                organizationIdSupplier.get(), subscriptionId, limit, offset);
        return Response.ok(result).build();
    }

    @GET
    @Path("/{subscriptionId}/events/{deliveryId}")
    public Response getSubscriptionEventHistory(
            @PathParam("subscriptionId") String subscriptionId,
            @PathParam("deliveryId") String deliveryId) {
        SubscriptionEventHistoryDTO dto = subscriptionHandler.getSubscriptionEventHistory(
                organizationIdSupplier.get(), subscriptionId, deliveryId);
        return Response.ok(dto).build();
    }

    @POST
    @Path("/{subscriptionId}/events/{deliveryId}/retry")
    public Response retryDelivery(
            @PathParam("subscriptionId") String subscriptionId,
            @PathParam("deliveryId") String deliveryId) {
        SubscriptionEventHistoryDTO dto = subscriptionHandler.retryDelivery(
                organizationIdSupplier.get(), subscriptionId, deliveryId);
        return Response.status(Response.Status.ACCEPTED).entity(dto).build();
    }
}
