package org.wso2.dpdp.accelerator.complaint.mgt.service.util;

/**
 * Statutory due period for grievance redressal under the DPDP Act. Configurable via the
 * CO_STATUTORY_DUE_PERIOD_DAYS system property (defaults to 90 days).
 */
public class StatutoryDuePeriodPolicy {

    private static final int DEFAULT_DUE_PERIOD_DAYS = 90;

    private StatutoryDuePeriodPolicy() {
    }

    public static long getDuePeriodMillis() {
        String configured = System.getProperty("CO_STATUTORY_DUE_PERIOD_DAYS");
        int days = DEFAULT_DUE_PERIOD_DAYS;
        if (configured != null) {
            try {
                days = Integer.parseInt(configured.trim());
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        return days * 24L * 60 * 60 * 1000;
    }
}
