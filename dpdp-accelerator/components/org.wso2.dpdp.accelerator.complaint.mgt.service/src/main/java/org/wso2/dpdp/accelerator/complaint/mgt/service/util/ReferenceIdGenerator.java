package org.wso2.dpdp.accelerator.complaint.mgt.service.util;

import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintDAO;

import java.time.ZoneOffset;
import java.time.Instant;

/**
 * Generates the human-facing REFERENCE_ID (e.g. "CMP-2026-04821") described in the ER diagram.
 */
public class ReferenceIdGenerator {

    private static final String PREFIX = "CMP";

    private ReferenceIdGenerator() {
    }

    public static String generate(ComplaintDAO complaintDAO, String orgId, long createdTimeMillis) {
        int year = Instant.ofEpochMilli(createdTimeMillis).atZone(ZoneOffset.UTC).get(java.time.temporal.ChronoField.YEAR);
        String likePattern = PREFIX + "-" + year + "-%";
        int existingCount = complaintDAO.countByReferenceIdPrefix(orgId, likePattern);
        int nextSeq = existingCount + 1;
        return String.format("%s-%d-%05d", PREFIX, year, nextSeq);
    }
}
