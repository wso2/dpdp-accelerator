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

package org.wso2.dpdp.accelerator.event.notifications.endpoint.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.WebApplicationException;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.Collections;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class EventNotificationExceptionMapperTest {

    private EventNotificationExceptionMapper mapper;

    @BeforeMethod
    public void setUp() {
        mapper = new EventNotificationExceptionMapper();
    }

    @Test
    public void testToResponseNotFoundException() {
        EventNotificationException ex = new EventNotificationException("EN-4040", "Resource not found", "Topic ID not found.", 404);
        Response response = mapper.toResponse(ex);

        assertNotNull(response);
        assertEquals(response.getStatus(), 404);
        assertEquals(response.getMediaType(), MediaType.APPLICATION_JSON_TYPE);

        @SuppressWarnings("unchecked")
        Map<String, Object> entity = new com.fasterxml.jackson.databind.ObjectMapper().convertValue(response.getEntity(), Map.class);
        assertNotNull(entity);
        assertEquals(entity.get("code"), "EN-4040");
        assertEquals(entity.get("message"), "Resource not found");
        assertEquals(entity.get("description"), "Topic ID not found.");
    }

    @Test
    public void testToResponseConflictException() {
        EventNotificationException ex = new EventNotificationException("EN-4090", "Topic already exists", "Topic name conflict.", 409);
        Response response = mapper.toResponse(ex);

        assertNotNull(response);
        assertEquals(response.getStatus(), 409);
        assertTrue(response.getEntity() instanceof org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.Error);
    }

    @Test
    public void testToResponseValidationException() {
        EventNotificationException ex = new EventNotificationException("EN-4001", "Malformed request", "Org ID required.", 400);
        Response response = mapper.toResponse(ex);

        assertNotNull(response);
        assertEquals(response.getStatus(), 400);
    }

    @Test
    public void mapsWrappedServiceAndWebApplicationExceptions() {
        EventNotificationException service = new EventNotificationException("EN-5000", "failure", "details", 500);
        assertEquals(mapper.toResponse(new RuntimeException(new RuntimeException(service))).getStatus(), 500);
        assertEquals(mapper.toResponse(new WebApplicationException(Response.status(418).build())).getStatus(), 418);
    }

    @Test
    public void mapsArgumentJsonConstraintAndUnknownExceptions() {
        assertEquals(mapper.toResponse(new IllegalArgumentException("bad")).getStatus(), 400);
        assertEquals(mapper.toResponse(new JsonProcessingException("bad") { }).getStatus(), 400);
        ConstraintViolation<?> violation = Mockito.mock(ConstraintViolation.class);
        Mockito.when(violation.getPropertyPath()).thenReturn(null);
        Mockito.when(violation.getMessage()).thenReturn("required");
        ConstraintViolationException validation = new ConstraintViolationException(
                "invalid", Collections.singleton(violation));
        assertEquals(mapper.toResponse(validation).getStatus(), 400);
        assertEquals(mapper.toResponse(new RuntimeException("unexpected")).getStatus(), 500);
    }
}
