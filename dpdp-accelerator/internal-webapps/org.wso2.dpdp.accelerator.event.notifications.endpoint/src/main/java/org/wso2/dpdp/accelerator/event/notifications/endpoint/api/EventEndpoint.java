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

import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.EventCreateRequest;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.util.EventNotificationDtoMapper;

import org.wso2.dpdp.accelerator.event.notifications.endpoint.handler.EventHandler;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.constants.EventNotificationEndpointConstants;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.EventDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.EventPollingResponseDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDeliveryDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionEventHistoryDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.model.PaginatedResult;
import org.wso2.dpdp.accelerator.common.util.DPDPTenantContext;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.function.Supplier;

/**
 * JAX-RS endpoint for event publication, delivery listing, and delivery audit history.
 */
@Path("/events")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class EventEndpoint {

    private final EventHandler eventHandler;
    private final Supplier<String> organizationIdResolver;

    public EventEndpoint() {
        this.eventHandler = new EventHandler();
        this.organizationIdResolver = DPDPTenantContext::getOrganizationId;
    }

    public EventEndpoint(EventHandler eventHandler) {
        this(eventHandler, DPDPTenantContext::getOrganizationId);
    }

    EventEndpoint(EventHandler eventHandler, Supplier<String> organizationIdResolver) {
        this.eventHandler = eventHandler;
        this.organizationIdResolver = organizationIdResolver;
    }

    @POST
    public Response publishEvent(
            @HeaderParam(EventNotificationEndpointConstants.GROUP_ID_HEADER) String groupId,
            EventCreateRequest request) {
        EventDTO dto = eventHandler.publishEvent(organizationIdResolver.get(), groupId, EventNotificationDtoMapper.toService(request));
        return Response.status(Response.Status.CREATED).entity(EventNotificationDtoMapper.toApi(dto)).build();
    }

    @POST
    @Path("/poll")
    public Response pollEvents(
            @HeaderParam(EventNotificationEndpointConstants.SUBSCRIPTION_ID_HEADER) String subscriptionId,
            @HeaderParam(EventNotificationEndpointConstants.GROUP_ID_HEADER) String groupId,
            @HeaderParam(EventNotificationEndpointConstants.EVENT_SIGNATURE_HEADER) String signature,
            String requestBody) {
        EventPollingResponseDTO response = eventHandler.pollEvents(organizationIdResolver.get(), groupId,
                subscriptionId, requestBody, signature);
        return Response.ok(EventNotificationDtoMapper.toApi(response)).build();
    }

    @GET
    public Response listEvents(
            @QueryParam("topic") String topic,
            @QueryParam("status") String status,
            @QueryParam("subscriptionId") String subscriptionId,
            @QueryParam("purposes") String purposes,
            @QueryParam("search") String search,
            @QueryParam("limit") @DefaultValue(EventNotificationEndpointConstants.DEFAULT_LIMIT_STR) int limit,
            @QueryParam("offset") @DefaultValue(EventNotificationEndpointConstants.DEFAULT_OFFSET_STR) int offset) {
        String orgId = organizationIdResolver.get();
        PaginatedResult<EventDTO> result = subscriptionId == null || subscriptionId.trim().isEmpty()
                ? eventHandler.searchEvents(orgId, topic, status, orgId, purposes, search, limit, offset)
                : eventHandler.searchEvents(orgId, topic, status, orgId, subscriptionId,
                        purposes, search, limit, offset);
        return Response.ok(EventNotificationDtoMapper.events(result)).build();
    }

    @GET
    @Path("/{deliveryId}/history")
    public Response getDeliveryHistory(
            @PathParam("deliveryId") String deliveryId) {
        SubscriptionEventHistoryDTO dto = eventHandler.getDeliveryHistory(organizationIdResolver.get(), deliveryId);
        return Response.ok(EventNotificationDtoMapper.toApi(dto)).build();
    }

    @GET
    @Path("/{eventId}")
    public Response getEvent(
            @PathParam("eventId") String eventId) {
        EventDTO dto = eventHandler.getEventById(organizationIdResolver.get(), eventId);
        return Response.ok(EventNotificationDtoMapper.toApi(dto)).build();
    }

    @GET
    @Path("/{eventId}/deliveries")
    public Response getEventDeliveries(
            @PathParam("eventId") String eventId,
            @QueryParam("limit") @DefaultValue(EventNotificationEndpointConstants.DEFAULT_LIMIT_STR) int limit,
            @QueryParam("offset") @DefaultValue(EventNotificationEndpointConstants.DEFAULT_OFFSET_STR) int offset) {
        PaginatedResult<SubscriptionDeliveryDTO> result = eventHandler.getEventDeliveries(
                organizationIdResolver.get(), eventId, limit, offset);
        return Response.ok(EventNotificationDtoMapper.deliveries(result)).build();
    }
}
