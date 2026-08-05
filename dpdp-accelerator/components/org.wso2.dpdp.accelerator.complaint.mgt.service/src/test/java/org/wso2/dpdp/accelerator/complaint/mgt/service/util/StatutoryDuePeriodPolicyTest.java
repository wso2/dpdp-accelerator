package org.wso2.dpdp.accelerator.complaint.mgt.service.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatutoryDuePeriodPolicyTest {

    private static final String PROP = "CO_STATUTORY_DUE_PERIOD_DAYS";

    @AfterEach
    void clearSystemProperty() {
        System.clearProperty(PROP);
    }

    @Test
    void defaultsToNinetyDaysWhenPropertyNotSet() {
        System.clearProperty(PROP);

        assertEquals(90L * 24 * 60 * 60 * 1000, StatutoryDuePeriodPolicy.getDuePeriodMillis());
    }

    @Test
    void usesConfiguredDaysWhenPropertyIsSet() {
        System.setProperty(PROP, "30");

        assertEquals(30L * 24 * 60 * 60 * 1000, StatutoryDuePeriodPolicy.getDuePeriodMillis());
    }

    @Test
    void fallsBackToDefaultWhenPropertyIsNotAValidNumber() {
        System.setProperty(PROP, "not-a-number");

        assertEquals(90L * 24 * 60 * 60 * 1000, StatutoryDuePeriodPolicy.getDuePeriodMillis());
    }
}
