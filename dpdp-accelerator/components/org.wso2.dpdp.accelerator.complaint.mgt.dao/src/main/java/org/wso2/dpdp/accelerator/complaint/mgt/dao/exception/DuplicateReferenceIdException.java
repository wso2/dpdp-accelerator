package org.wso2.dpdp.accelerator.complaint.mgt.dao.exception;

import java.sql.SQLException;

/**
 * Thrown when {@code addComplaint} fails because another concurrent request already took the same
 * (ORG_ID, REFERENCE_ID) - the count-then-format sequence in ReferenceIdGenerator is inherently
 * racy under concurrent submissions, so the caller is expected to catch this, generate a fresh
 * reference ID, and retry rather than surface a generic 500.
 */
public class DuplicateReferenceIdException extends RuntimeException {

    public DuplicateReferenceIdException(SQLException cause) {
        super("A complaint with this reference ID already exists for this organization.", cause);
    }
}
