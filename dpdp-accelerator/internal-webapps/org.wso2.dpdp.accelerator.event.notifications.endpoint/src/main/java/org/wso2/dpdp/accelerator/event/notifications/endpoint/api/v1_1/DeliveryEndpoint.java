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

package org.wso2.dpdp.accelerator.event.notifications.endpoint.api.v1_1;

import org.wso2.dpdp.accelerator.common.util.DPDPTenantContext;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.constants.EventNotificationEndpointConstants;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.handler.EventHandler;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.handler.SubscriptionHandler;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.util.v1_1.EventNotificationDtoMapper;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionEventHistoryDTO;

import java.util.function.Supplier;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * JAX-RS endpoint for V1.1 delivery operations: details, attempt audit history,
 * manual retry, and externally submitted delivery completion evidence.
 */
@Path("/deliveries")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class DeliveryEndpoint {

    private final EventHandler eventHandler;
    private final SubscriptionHandler subscriptionHandler;
    private final Supplier<String> organizationIdResolver;

    public DeliveryEndpoint() {
        this.eventHandler = new EventHandler();
        this.subscriptionHandler = new SubscriptionHandler();
        this.organizationIdResolver = DPDPTenantContext::getOrganizationId;
    }

    public DeliveryEndpoint(EventHandler eventHandler, SubscriptionHandler subscriptionHandler) {
        this(eventHandler, subscriptionHandler, DPDPTenantContext::getOrganizationId);
    }

    public DeliveryEndpoint(EventHandler eventHandler, SubscriptionHandler subscriptionHandler,
            Supplier<String> organizationIdResolver) {
        this.eventHandler = eventHandler;
        this.subscriptionHandler = subscriptionHandler;
        this.organizationIdResolver = organizationIdResolver;
    }

    @GET
    @Path("/{deliveryId}")
    public Response getDelivery(@PathParam("deliveryId") String deliveryId) {
        SubscriptionEventHistoryDTO dto = eventHandler.getDeliveryHistory(organizationIdResolver.get(), deliveryId);
        return Response.ok(EventNotificationDtoMapper.toDelivery(dto)).build();
    }

    @GET
    @Path("/{deliveryId}/history")
    public Response getDeliveryHistory(@PathParam("deliveryId") String deliveryId) {
        SubscriptionEventHistoryDTO dto = eventHandler.getDeliveryHistory(organizationIdResolver.get(), deliveryId);
        return Response.ok(EventNotificationDtoMapper.toApi(dto)).build();
    }

    @POST
    @Path("/{deliveryId}/retry")
    public Response retryDelivery(@PathParam("deliveryId") String deliveryId) {
        SubscriptionEventHistoryDTO dto = subscriptionHandler.retryDelivery(
                organizationIdResolver.get(), deliveryId);
        return Response.status(Response.Status.ACCEPTED)
                .entity(EventNotificationDtoMapper.toRetryResponse(dto))
                .build();
    }

    @POST
    @Path("/{deliveryId}/completion")
    public Response completeDelivery(
            @PathParam("deliveryId") String deliveryId,
            @HeaderParam(EventNotificationEndpointConstants.EVENT_SIGNATURE_HEADER) String signature,
            String requestBody) {
        String orgId = organizationIdResolver.get();
        eventHandler.completeDelivery(orgId, orgId, deliveryId, requestBody, signature);
        return Response.noContent().build();
    }
}
