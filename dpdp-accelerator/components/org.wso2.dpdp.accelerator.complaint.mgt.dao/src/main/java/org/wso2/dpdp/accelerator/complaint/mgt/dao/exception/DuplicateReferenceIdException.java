package org.wso2.dpdp.accelerator.complaint.mgt.dao.exception;

import java.sql.SQLException;

/** Thrown when {@code addComplaint} hits a concurrent (ORG_ID, REFERENCE_ID) collision; the caller should retry with a fresh reference ID. */
public class DuplicateReferenceIdException extends ComplaintDAOException {

    public DuplicateReferenceIdException(SQLException cause) {
        super("A complaint with this reference ID already exists for this organization.", cause);
    }
}
