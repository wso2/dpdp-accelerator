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

package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.exception;

import org.junit.jupiter.api.Test;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintException;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ComplaintExceptionMapperTest {

    private final ComplaintExceptionMapper mapper = new ComplaintExceptionMapper();

    @Test
    void mapsComplaintExceptionToItsOwnStatusCodeAndErrorBody() {
        ComplaintException exception =
                new ComplaintException("CO-4040", "Complaint not found", "No complaint with that id.", 404);

        Response response = mapper.toResponse(exception);

        assertEquals(404, response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
        ErrorEnvelope envelope = (ErrorEnvelope) response.getEntity();
        assertEquals("CO-4040", envelope.getCode());
        assertEquals("Complaint not found", envelope.getMessage());
        assertEquals("No complaint with that id.", envelope.getDescription());
        assertNotNull(envelope.getTraceId());
    }

    @Test
    void mapsUnexpectedExceptionsToA500WithGenericMessage() {
        Response response = mapper.toResponse(new RuntimeException("boom"));

        assertEquals(500, response.getStatus());
        ErrorEnvelope envelope = (ErrorEnvelope) response.getEntity();
        assertEquals("CO-5000", envelope.getCode());
        assertEquals("Internal error", envelope.getMessage());
        assertNotNull(envelope.getTraceId());
    }

    @Test
    void generatesADifferentTraceIdOnEachInvocation() {
        RuntimeException exception = new RuntimeException("boom");

        Response first = mapper.toResponse(exception);
        Response second = mapper.toResponse(exception);

        ErrorEnvelope firstEnvelope = (ErrorEnvelope) first.getEntity();
        ErrorEnvelope secondEnvelope = (ErrorEnvelope) second.getEntity();
        assertNotNull(firstEnvelope.getTraceId());
        assertNotNull(secondEnvelope.getTraceId());
        org.junit.jupiter.api.Assertions.assertNotEquals(firstEnvelope.getTraceId(), secondEnvelope.getTraceId());
    }
}
