package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.filter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorsFilterTest {

    @Mock
    private ContainerRequestContext requestContext;
    @Mock
    private ContainerResponseContext responseContext;

    private final CorsFilter filter = new CorsFilter();

    @Test
    void requestFilterAbortsPreflightOptionsRequestsWith200() throws IOException {
        when(requestContext.getMethod()).thenReturn(HttpMethod.OPTIONS);

        filter.filter(requestContext);

        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(requestContext).abortWith(captor.capture());
        assertEquals(200, captor.getValue().getStatus());
    }

    @Test
    void requestFilterDoesNotAbortNonOptionsRequests() throws IOException {
        lenient().when(requestContext.getMethod()).thenReturn(HttpMethod.GET);

        filter.filter(requestContext);

        verify(requestContext, never()).abortWith(any());
    }

    @Test
    void responseFilterAddsCorsHeaders() throws IOException {
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        when(responseContext.getHeaders()).thenReturn(headers);

        filter.filter(requestContext, responseContext);

        assertEquals("*", headers.getFirst("Access-Control-Allow-Origin"));
        assertEquals("Content-Type, org-id", headers.getFirst("Access-Control-Allow-Headers"));
        assertEquals("GET, POST, PUT, DELETE, OPTIONS", headers.getFirst("Access-Control-Allow-Methods"));
    }
}
