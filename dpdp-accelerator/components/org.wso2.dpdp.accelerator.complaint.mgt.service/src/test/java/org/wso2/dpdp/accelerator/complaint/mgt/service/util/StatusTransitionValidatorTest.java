package org.wso2.dpdp.accelerator.complaint.mgt.service.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusTransitionValidatorTest {

    @ParameterizedTest
    @CsvSource({
            "OPEN, IN_PROGRESS",
            "OPEN, AWAITING_COMPLAINANT_INFO",
            "IN_PROGRESS, AWAITING_COMPLAINANT_INFO",
            "IN_PROGRESS, RESOLVED",
            "AWAITING_COMPLAINANT_INFO, AWAITING_INTERNAL_REVIEW",
            "AWAITING_INTERNAL_REVIEW, IN_PROGRESS",
            "AWAITING_INTERNAL_REVIEW, RESOLVED"
    })
    void allowsDocumentedValidTransitions(String from, String to) {
        assertTrue(StatusTransitionValidator.isValidTransition(from, to));
    }

    @ParameterizedTest
    @CsvSource({
            "OPEN, RESOLVED",
            "OPEN, OPEN",
            "RESOLVED, OPEN",
            "RESOLVED, IN_PROGRESS",
            "IN_PROGRESS, AWAITING_INTERNAL_REVIEW",
            "AWAITING_COMPLAINANT_INFO, IN_PROGRESS",
            "AWAITING_COMPLAINANT_INFO, RESOLVED",
            "AWAITING_INTERNAL_REVIEW, AWAITING_COMPLAINANT_INFO"
    })
    void rejectsInvalidTransitions(String from, String to) {
        assertFalse(StatusTransitionValidator.isValidTransition(from, to));
    }

    @ParameterizedTest
    @CsvSource({
            "GARBAGE, OPEN",
            "OPEN, GARBAGE"
    })
    void rejectsTransitionsInvolvingUnknownStatuses(String from, String to) {
        assertFalse(StatusTransitionValidator.isValidTransition(from, to));
    }

    @org.junit.jupiter.api.Test
    void rejectsNullFromOrToStatus() {
        assertFalse(StatusTransitionValidator.isValidTransition(null, "OPEN"));
        assertFalse(StatusTransitionValidator.isValidTransition("OPEN", null));
    }
}
