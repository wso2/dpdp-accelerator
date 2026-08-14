package com.openfgc.nomineeservice.service;

/** The gate or a fine-grained permission check refused the request. */
public class NotAuthorizedException extends RuntimeException {
    public NotAuthorizedException(String message) {
        super(message);
    }
}
