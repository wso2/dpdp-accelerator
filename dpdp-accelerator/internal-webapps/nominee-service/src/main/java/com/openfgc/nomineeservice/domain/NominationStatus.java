package com.openfgc.nomineeservice.domain;

/**
 * The lifecycle of a nomination. Only {@link #ACTIVE} permits a nominee to act,
 * so a nomination that is merely accepted grants nothing until an administrator
 * has reviewed it.
 */
public enum NominationStatus {

    /** Created by the owner, awaiting the nominee's acceptance. */
    PENDING,

    /** Accepted by the nominee, awaiting administrative activation. */
    ACCEPTED,

    /**
     * Declined by the nominee. Terminal: the owner must create a new nomination
     * rather than revive this one, so a refusal is never silently reversed.
     */
    REJECTED,

    /** In force. The nominee may act within the granted permissions. */
    ACTIVE,

    /** Withdrawn by an administrator. Access ends on the nominee's next request. */
    DEACTIVATED
}
