package org.wso2.dpdp.accelerator.complaint.mgt.service.exception;

public class ComplaintException extends RuntimeException {

    private final String code;
    private final String description;
    private final int statusCode;

    public ComplaintException(String code, String message, String description, int statusCode) {
        super(message);
        this.code = code;
        this.description = description;
        this.statusCode = statusCode;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
