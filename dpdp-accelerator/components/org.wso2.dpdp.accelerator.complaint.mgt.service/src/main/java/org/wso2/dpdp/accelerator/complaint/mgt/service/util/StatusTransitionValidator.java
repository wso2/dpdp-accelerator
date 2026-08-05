package org.wso2.dpdp.accelerator.complaint.mgt.service.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.DAOConstants.STATUS_AWAITING_COMPLAINANT_INFO;
import static org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.DAOConstants.STATUS_AWAITING_INTERNAL_REVIEW;
import static org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.DAOConstants.STATUS_IN_PROGRESS;
import static org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.DAOConstants.STATUS_OPEN;
import static org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.DAOConstants.STATUS_RESOLVED;

/**
 * Valid ComplaintStatus transitions.
 *   OPEN                        -> IN_PROGRESS, AWAITING_COMPLAINANT_INFO
 *   IN_PROGRESS                 -> AWAITING_COMPLAINANT_INFO, RESOLVED
 *   AWAITING_COMPLAINANT_INFO   -> AWAITING_INTERNAL_REVIEW
 *   AWAITING_INTERNAL_REVIEW    -> IN_PROGRESS, RESOLVED
 *   RESOLVED                    -> (terminal - no further transitions)
 *
 * A complaint can only be RESOLVED after having gone through IN_PROGRESS or AWAITING_INTERNAL_REVIEW,
 * so OPEN -> RESOLVED directly is rejected (this matches the 409 example in the API spec).
 *
 * Once a complaint is AWAITING_COMPLAINANT_INFO, the complainant's reply routes it to internal
 * review rather than back into IN_PROGRESS directly - AWAITING_INTERNAL_REVIEW is the only way out.
 */
public class StatusTransitionValidator {

    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = new HashMap<>();

    static {
        ALLOWED_TRANSITIONS.put(STATUS_OPEN, Set.of(STATUS_IN_PROGRESS, STATUS_AWAITING_COMPLAINANT_INFO));
        ALLOWED_TRANSITIONS.put(STATUS_IN_PROGRESS,
                Set.of(STATUS_AWAITING_COMPLAINANT_INFO, STATUS_RESOLVED));
        ALLOWED_TRANSITIONS.put(STATUS_AWAITING_COMPLAINANT_INFO,
                Set.of(STATUS_AWAITING_INTERNAL_REVIEW));
        ALLOWED_TRANSITIONS.put(STATUS_AWAITING_INTERNAL_REVIEW, Set.of(STATUS_IN_PROGRESS, STATUS_RESOLVED));
        ALLOWED_TRANSITIONS.put(STATUS_RESOLVED, new HashSet<>());
    }

    private StatusTransitionValidator() {
    }

    public static boolean isValidTransition(String fromStatus, String toStatus) {
        Set<String> allowedTargets = ALLOWED_TRANSITIONS.get(fromStatus);
        return allowedTargets != null && toStatus != null && allowedTargets.contains(toStatus);
    }
}
