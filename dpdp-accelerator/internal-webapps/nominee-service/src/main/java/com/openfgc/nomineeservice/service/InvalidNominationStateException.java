package com.openfgc.nomineeservice.service;

import com.openfgc.nomineeservice.domain.NominationStatus;

/**
 * Raised when a lifecycle change is asked for from a state that does not permit
 * it.
 *
 * <p>The sequence is deliberate: an owner nominates, the nominee accepts, and
 * only then may an administrator activate. Allowing activation from any state
 * would let an administrator grant access the nominee never agreed to, or
 * reinstate one they explicitly refused - which is the thing the acceptance step
 * exists to prevent.
 */
public class InvalidNominationStateException extends RuntimeException {

    public InvalidNominationStateException(String action, String nominationId,
                                            NominationStatus actual, NominationStatus required) {
        super(String.format(
                "A nomination can only be %s while it is %s; %s is %s.",
                action, required, nominationId, actual));
    }
}
