package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.exception;

import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintException;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@Provider
public class ComplaintExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOGGER = Logger.getLogger(ComplaintExceptionMapper.class.getName());

    @Override
    public Response toResponse(Throwable exception) {
        if (exception instanceof ComplaintException) {
            ComplaintException coEx = (ComplaintException) exception;
            ErrorEnvelope envelope = new ErrorEnvelope(
                    coEx.getCode(),
                    coEx.getMessage(),
                    coEx.getDescription(),
                    UUID.randomUUID().toString()
            );
            return Response.status(coEx.getStatusCode())
                    .type(MediaType.APPLICATION_JSON)
                    .entity(envelope)
                    .build();
        }

        LOGGER.log(Level.SEVERE, "Unhandled exception in Complaint API: " + exception.getMessage(), exception);

        ErrorEnvelope envelope = new ErrorEnvelope(
                "CO-5000",
                "Internal error",
                "An unexpected error occurred while processing the request.",
                UUID.randomUUID().toString()
        );

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(envelope)
                .build();
    }
}
