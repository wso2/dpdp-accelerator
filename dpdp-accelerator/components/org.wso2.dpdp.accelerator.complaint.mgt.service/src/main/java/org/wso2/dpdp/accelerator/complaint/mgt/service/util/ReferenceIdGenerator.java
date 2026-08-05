package org.wso2.dpdp.accelerator.complaint.mgt.service.util;

import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintDAO;

import java.time.ZoneOffset;
import java.time.Instant;

/**
 * Generates the human-facing REFERENCE_ID (e.g. "CMP-2026-04821") described in the ER diagram.
 * Note: the OpenAPI spec's response schemas do not currently expose a referenceId field - this
 * generator/DTO field is included on the assumption that the human-readable ticket number is meant
 * to be surfaced to callers (otherwise there would be no reason to store it). Flag this to the
 * front-end/BFF team so the field can be added to the published spec if it is in fact wanted.
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
