package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ErrorEnvelopeTest {

    @Test
    void noArgsConstructorGeneratesATraceId() {
        ErrorEnvelope envelope = new ErrorEnvelope();

        assertNotNull(envelope.getTraceId());
    }

    @Test
    void allArgsConstructorUsesGivenTraceIdWhenPresent() {
        ErrorEnvelope envelope = new ErrorEnvelope("CO-4040", "Not found", "desc", "trace-1");

        assertEquals("CO-4040", envelope.getCode());
        assertEquals("Not found", envelope.getMessage());
        assertEquals("desc", envelope.getDescription());
        assertEquals("trace-1", envelope.getTraceId());
    }

    @Test
    void allArgsConstructorGeneratesTraceIdWhenGivenNull() {
        ErrorEnvelope envelope = new ErrorEnvelope("CO-4040", "Not found", "desc", null);

        assertNotNull(envelope.getTraceId());
    }

    @Test
    void settersUpdateAllFields() {
        ErrorEnvelope envelope = new ErrorEnvelope();
        envelope.setCode("CO-5000");
        envelope.setMessage("Internal error");
        envelope.setDescription("desc");
        envelope.setTraceId("trace-2");

        assertEquals("CO-5000", envelope.getCode());
        assertEquals("Internal error", envelope.getMessage());
        assertEquals("desc", envelope.getDescription());
        assertEquals("trace-2", envelope.getTraceId());
    }
}
