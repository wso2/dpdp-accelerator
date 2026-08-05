package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.util;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * The DAO/service layers store timestamps as epoch millis (bigint, per the ER diagram), while the
 * OpenAPI spec exposes them as "type: string, format: date-time" (ISO-8601). This is the single
 * conversion point between the two.
 */
public class DateTimeUtil {

    private DateTimeUtil() {
    }

    public static String toIso(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).toString();
    }

    public static long fromIso(String isoDateTime) {
        try {
            return Instant.parse(isoDateTime).toEpochMilli();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date-time value: " + isoDateTime, e);
        }
    }
}
