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
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException;
import org.wso2.dpdp.accelerator.common.util.LogSanitizer;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.constants.EventNotificationEndpointErrorCodes;

import javax.validation.ConstraintViolationException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.Collections;
import java.util.IdentityHashMap;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.Error;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

@Provider
public class EventNotificationExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Log log = LogFactory.getLog(EventNotificationExceptionMapper.class);

    @Override
    public Response toResponse(Throwable exception) {

        if (exception instanceof EventNotificationException) {
            return handleEventNotificationException((EventNotificationException) exception);
        }

        Throwable rootCause = unwrap(exception);

        if (rootCause instanceof EventNotificationException) {
            return handleEventNotificationException((EventNotificationException) rootCause);
        }

        if (rootCause instanceof WebApplicationException) {
            return handleWebApplicationException((WebApplicationException) rootCause);
        }

        if (rootCause instanceof ConstraintViolationException) {
            return handleConstraintViolation((ConstraintViolationException) rootCause);
        }

        if (rootCause instanceof JsonProcessingException) {
            return handleJsonProcessingException((JsonProcessingException) rootCause);
        }

        if (rootCause instanceof IllegalArgumentException) {
            log.debug("Invalid request argument: " + LogSanitizer.sanitize(rootCause.getMessage()));
            return buildResponse(Response.Status.BAD_REQUEST.getStatusCode(),
                    EventNotificationEndpointErrorCodes.INVALID_REQUEST_PARAMETER,
                    "Invalid request parameter", rootCause.getMessage());
        }

        log.error("Unhandled exception in Event Notification endpoint", exception);
        return buildResponse(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                EventNotificationEndpointErrorCodes.INTERNAL_SERVER_ERROR,
                "Internal server error", "An unexpected error occurred.");
    }

    private Response handleEventNotificationException(EventNotificationException ex) {
        if (ex.getStatusCode() >= 500) {
            log.error("Service error [" + LogSanitizer.sanitize(ex.getCode()) + "]: "
                    + LogSanitizer.sanitize(ex.getMessage()), ex);
        } else {
            log.debug("Service error [" + LogSanitizer.sanitize(ex.getCode()) + "]: "
                    + LogSanitizer.sanitize(ex.getMessage()));
        }
        return buildResponse(ex.getStatusCode(), ex.getCode(), ex.getMessage(), ex.getDescription());
    }

    private Response handleWebApplicationException(WebApplicationException wae) {
        int status = wae.getResponse().getStatus();
        log.debug("JAX-RS exception [" + status + "]: " + LogSanitizer.sanitize(wae.getMessage()));
        return buildResponse(status, EventNotificationEndpointErrorCodes.forHttpStatus(status),
                wae.getMessage() != null ? wae.getMessage() : Response.Status.fromStatusCode(status).getReasonPhrase(), null);
    }

    private Response handleConstraintViolation(ConstraintViolationException cve) {
        String detail = cve.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse(cve.getMessage());

        log.debug("Validation failure: " + LogSanitizer.sanitize(detail));
        return buildResponse(Response.Status.BAD_REQUEST.getStatusCode(),
                EventNotificationEndpointErrorCodes.VALIDATION_FAILURE,
                "Request failed validation", detail);
    }

    private Response handleJsonProcessingException(JsonProcessingException jpe) {
        String detail = jpe.getOriginalMessage();
        if (jpe instanceof UnrecognizedPropertyException) {
            UnrecognizedPropertyException upe = (UnrecognizedPropertyException) jpe;
            detail = "Unrecognized field '" + upe.getPropertyName() + "' in request payload.";
        }

        log.debug("Malformed request payload: " + LogSanitizer.sanitize(detail));
        return buildResponse(Response.Status.BAD_REQUEST.getStatusCode(),
                EventNotificationEndpointErrorCodes.MALFORMED_REQUEST,
                "Malformed request payload", detail);
    }

    private Response buildResponse(int status, String code, String message, String description) {
        Error body = new Error().code(code).message(message).description(description);

        return Response.status(status > 0 ? status : 500)
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
    }

    private Throwable unwrap(Throwable exception) {
        Throwable current = exception;
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());

        while (current != null) {
            if (!visited.add(current)) {
                break; // Cycle detected
            }
            if (current instanceof EventNotificationException) {
                return current;
            }
            if (current.getCause() == null || current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return current != null ? current : exception;
    }
}
