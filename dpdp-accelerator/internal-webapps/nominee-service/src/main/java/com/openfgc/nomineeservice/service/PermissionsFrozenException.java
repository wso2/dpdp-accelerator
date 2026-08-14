package com.openfgc.nomineeservice.service;

/**
 * Raised when an owner tries to widen a grant an administrator has already
 * activated.
 *
 * <p>Activation records that a specific grant was verified, against a specific
 * ticket. Adding a permission afterwards would put the nominee beyond what was
 * reviewed while the record still claims otherwise, so the wider grant has to go
 * back for review.
 *
 * <p>Narrowing is always allowed: it can only take away access the administrator
 * already approved, and an owner should never have to wait to reduce someone's
 * reach. Removing the nomination outright is always allowed too.
 */
public class PermissionsFrozenException extends RuntimeException {

    public PermissionsFrozenException(String nominationId) {
        super("Permissions cannot be widened while a nomination is active: " + nominationId
                + ". Remove the nomination, or ask an administrator to deactivate it first.");
    }
}
