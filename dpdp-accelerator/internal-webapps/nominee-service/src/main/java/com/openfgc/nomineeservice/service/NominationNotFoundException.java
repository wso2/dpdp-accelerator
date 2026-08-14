package com.openfgc.nomineeservice.service;

public class NominationNotFoundException extends RuntimeException {
    public NominationNotFoundException(String id) {
        super("No nomination found for: " + id);
    }
}
