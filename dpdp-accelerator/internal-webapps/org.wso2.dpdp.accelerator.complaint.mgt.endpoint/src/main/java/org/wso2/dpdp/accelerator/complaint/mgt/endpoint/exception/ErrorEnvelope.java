package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.exception;

import java.util.UUID;

public class ErrorEnvelope {

    private String code;
    private String message;
    private String description;
    private String traceId;

    public ErrorEnvelope() {
        this.traceId = UUID.randomUUID().toString();
    }

    public ErrorEnvelope(String code, String message, String description, String traceId) {
        this.code = code;
        this.message = message;
        this.description = description;
        this.traceId = traceId != null ? traceId : UUID.randomUUID().toString();
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
