package com.openfgc.nomineeservice.service;

/**
 * Raised when an owner nominates themselves.
 *
 * <p>A nomination exists so that somebody else may act when the owner cannot.
 * Naming yourself grants nothing you do not already hold, and leaves a record
 * that reads as a delegation when none took place.
 */
public class SelfNominationException extends RuntimeException {

    public SelfNominationException(String ownerId) {
        super("An owner cannot nominate themselves: " + ownerId);
    }
}
