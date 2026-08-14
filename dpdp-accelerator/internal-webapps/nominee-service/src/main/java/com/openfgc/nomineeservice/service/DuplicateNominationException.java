package com.openfgc.nomineeservice.service;

/**
 * Raised when an owner nominates the same person twice. An owner may nominate
 * any number of people, but each of them only once - a second nomination for the
 * same pairing would create two competing permission sets for one relationship.
 */
public class DuplicateNominationException extends RuntimeException {

    public DuplicateNominationException(String ownerId, String nomineeId) {
        super("A nomination already exists for owner " + ownerId + " and nominee " + nomineeId);
    }
}
