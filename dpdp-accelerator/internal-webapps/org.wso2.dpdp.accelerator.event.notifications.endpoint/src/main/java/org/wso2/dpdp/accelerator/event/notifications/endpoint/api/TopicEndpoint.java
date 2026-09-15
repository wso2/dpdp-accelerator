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

import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.TopicCreateRequest;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.util.EventNotificationDtoMapper;

import org.wso2.dpdp.accelerator.event.notifications.endpoint.handler.TopicHandler;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.constants.EventNotificationEndpointConstants;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.TopicDTO;
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

@Path("/topics")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class TopicEndpoint {

    private final TopicHandler topicHandler;
    private final Supplier<String> organizationIdSupplier;

    public TopicEndpoint() {
        this.topicHandler = new TopicHandler();
        this.organizationIdSupplier = DPDPTenantContext::getOrganizationId;
    }

    public TopicEndpoint(TopicHandler topicHandler) {
        this.topicHandler = topicHandler;
        this.organizationIdSupplier = DPDPTenantContext::getOrganizationId;
    }

    public TopicEndpoint(TopicHandler topicHandler, Supplier<String> organizationIdSupplier) {
        this.topicHandler = topicHandler;
        this.organizationIdSupplier = organizationIdSupplier;
    }

    @POST
    public Response createTopic(TopicCreateRequest request) {
        TopicDTO dto = topicHandler.createTopic(organizationIdSupplier.get(), EventNotificationDtoMapper.toService(request));
        return Response.status(Response.Status.CREATED).entity(EventNotificationDtoMapper.toApi(dto)).build();
    }

    @GET
    public Response listTopics(
            @QueryParam("status") String status,
            @QueryParam("search") String search,
            @QueryParam("limit") @DefaultValue(EventNotificationEndpointConstants.DEFAULT_LIMIT_STR) int limit,
            @QueryParam("offset") @DefaultValue(EventNotificationEndpointConstants.DEFAULT_OFFSET_STR) int offset,
            @QueryParam("sort") String sort) {
        PaginatedResult<TopicDTO> result = topicHandler.listTopics(organizationIdSupplier.get(), status,
                search, limit, offset, sort);
        return Response.ok(EventNotificationDtoMapper.topics(result)).build();
    }

    @DELETE
    @Path("/{topicId}")
    public Response deleteTopic(
            @PathParam("topicId") String topicId) {
        TopicDTO dto = topicHandler.deleteTopic(organizationIdSupplier.get(), topicId);
        return Response.ok(EventNotificationDtoMapper.toApi(dto)).build();
    }
}
