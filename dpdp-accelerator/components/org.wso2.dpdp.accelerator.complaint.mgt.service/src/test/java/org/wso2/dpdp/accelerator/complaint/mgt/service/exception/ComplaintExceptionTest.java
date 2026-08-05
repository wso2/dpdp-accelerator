package org.wso2.dpdp.accelerator.complaint.mgt.service.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ComplaintExceptionTest {

    @Test
    void exposesCodeMessageDescriptionAndStatusCode() {
        ComplaintException exception = new ComplaintException("CO-4040", "Complaint not found",
                "No complaint exists with the given ID for this organization.", 404);

        assertEquals("CO-4040", exception.getCode());
        assertEquals("Complaint not found", exception.getMessage());
        assertEquals("No complaint exists with the given ID for this organization.", exception.getDescription());
        assertEquals(404, exception.getStatusCode());
    }

    @Test
    void isARuntimeException() {
        ComplaintException exception = new ComplaintException("CO-5000", "Internal error", "desc", 500);

        assertEquals(RuntimeException.class, exception.getClass().getSuperclass());
    }
}
