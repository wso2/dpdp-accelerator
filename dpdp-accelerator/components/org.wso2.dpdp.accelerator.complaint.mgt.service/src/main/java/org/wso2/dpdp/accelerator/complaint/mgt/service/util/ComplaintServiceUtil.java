/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.dpdp.accelerator.complaint.mgt.service.util;

import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintPriority;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintStatus;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintErrorCode;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintException;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintServiceConstants;
import org.wso2.dpdp.accelerator.complaint.mgt.service.internal.ComplaintServiceDataHolder;

import java.sql.Connection;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintPriority.CRITICAL;
import static org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintPriority.HIGH;
import static org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintPriority.LOW;
import static org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintPriority.MEDIUM;
import static org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintStatus.AWAITING_INTERNAL_REVIEW;
import static org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintStatus.IN_PROGRESS;
import static org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintStatus.OPEN;
import static org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintStatus.RESOLVED;
import static org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintStatus.WAITING_ON_CLIENT;

/**
 * Shared, stateless complaint-service helpers - connection-scoped lookups and small policy/mapping
 * decisions used by more than one service (or by the endpoint layer directly), kept out of {@code
 * ComplaintService} itself so its public interface never takes a {@link Connection} parameter -
 * mirrors the Financial Services accelerator's {@code ConsentCoreServiceUtil} convention.
 */
public final class ComplaintServiceUtil {

    // encoding.file.contentType list declared in the OpenAPI spec for both attachment upload
    // endpoints.
    public static final Set<String> ALLOWED_CONTENT_TYPES = Set.of( // default fallback value
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "image/png",
            "image/jpeg");

    private static final String REFERENCE_ID_PREFIX = "CMP";

    private static final Map<ComplaintStatus, Set<ComplaintStatus>> ALLOWED_STATUS_TRANSITIONS =
            new EnumMap<>(ComplaintStatus.class);

    static {
        ALLOWED_STATUS_TRANSITIONS.put(OPEN, EnumSet.of(IN_PROGRESS, WAITING_ON_CLIENT));
        ALLOWED_STATUS_TRANSITIONS.put(IN_PROGRESS, EnumSet.of(WAITING_ON_CLIENT, RESOLVED));
        ALLOWED_STATUS_TRANSITIONS.put(WAITING_ON_CLIENT, EnumSet.of(AWAITING_INTERNAL_REVIEW));
        ALLOWED_STATUS_TRANSITIONS.put(AWAITING_INTERNAL_REVIEW, EnumSet.of(IN_PROGRESS, WAITING_ON_CLIENT, RESOLVED));
        ALLOWED_STATUS_TRANSITIONS.put(RESOLVED, EnumSet.of(AWAITING_INTERNAL_REVIEW));
    }

    // ComplaintPriority is "server-derived, never client-supplied" per the API spec. This maps
    // each ComplaintCategory to a default priority, and the set of its keys is also the set of
    // valid ComplaintCategory values (see #isKnownCategory). The mapping below is the built-in
    // default; configure(Map) exists so it can be overridden wholesale, but nothing currently
    // wires it to deployment.toml - there is no [categoryPriority]-style table support in the
    // dpdp-accelerator.xml config chain yet (unlike the other Complaints.* settings, which are
    // fixed, individually-named elements - see DPDPConfigParser). Wiring an admin-defined,
    // arbitrarily keyed table through that chain is a separate design task, not something to bolt
    // on here.
    //
    // Deliberately a single JVM-wide mapping, not scoped per tenant: like ComplaintCategory
    // itself, "what priority does this category imply" is treated as an accelerator-wide
    // classification decision rather than a per-org policy, so every tenant sharing this
    // deployment would see the same override, if one is ever wired up. Do not add an orgId
    // parameter here without also deciding how deployment.toml should express per-tenant
    // overrides.
    private static volatile Map<String, String> categoryToPriority = buildDefaultCategoryPriorityMapping();

    private ComplaintServiceUtil() {
    }

    private static Map<String, String> buildDefaultCategoryPriorityMapping() {
        Map<String, String> defaults = new HashMap<>();
        defaults.put("DATA_BREACH", CRITICAL.name());
        defaults.put("UNAUTHORIZED_DATA_SHARING", HIGH.name());
        defaults.put("CONSENT_WITHDRAWN_DATA_STILL_USED", HIGH.name());
        defaults.put("PURPOSE_VIOLATION", HIGH.name());
        defaults.put("DATA_ACCESS_DENIED", HIGH.name());
        defaults.put("DATA_ERASURE_NOT_COMPLETED", MEDIUM.name());
        defaults.put("DATA_CORRECTION_NOT_COMPLETED", MEDIUM.name());
        defaults.put("CONSENT_LIFECYCLE_ISSUE", MEDIUM.name());
        defaults.put("EXCESSIVE_DATA_COLLECTION", MEDIUM.name());
        defaults.put("OTHER", LOW.name());
        return defaults;
    }

    /**
     * Fetches core complaint fields, throwing a 404 ComplaintException if it doesn't exist for
     * this org. Used by every service (events, attachments, the complaint service itself) that
     * needs to confirm a complaint exists/belongs to the org before acting on it, without
     * duplicating that existence check in every DAO.
     *
     * @param conn        caller-owned connection this read runs against
     * @param complaintDAO DAO to read through
     * @param orgId       tenant/organization the complaint belongs to
     * @param complaintId complaint to fetch
     * @return the complaint
     * @throws ComplaintException thrown with a 404 status if the complaint doesn't exist for this
     *                            org
     */
    public static Complaint getComplaint(Connection conn, ComplaintDAO complaintDAO, String orgId,
            String complaintId) {
        if (complaintId == null || complaintId.trim().isEmpty() || orgId == null || orgId.trim().isEmpty()) {
            throw new ComplaintException(ComplaintErrorCode.COMPLAINT_NOT_FOUND,
                    ComplaintServiceConstants.COMPLAINT_NOT_FOUND_ERROR);
        }
        Optional<Complaint> complaintOpt = complaintDAO.getComplaintById(conn, complaintId.trim(), orgId.trim());
        if (complaintOpt.isEmpty()) {
            throw new ComplaintException(ComplaintErrorCode.COMPLAINT_NOT_FOUND,
                    String.format(ComplaintServiceConstants.COMPLAINT_NOT_FOUND_BY_ID_ERROR, complaintId));
        }
        return complaintOpt.get();
    }

    /**
     * Same as {@link #getComplaint}, but additionally raises a 404 ComplaintException (not a 403 -
     * see complaint-server-API.yaml, which is explicit that /me/* must not confirm a complaint's
     * existence to a caller who doesn't own it) if the complaint's userId does not match
     * ownerUserId. Used by every /me/* code path that acts on a single complaintId.
     *
     * @param conn        caller-owned connection this read runs against
     * @param complaintDAO DAO to read through
     * @param orgId       tenant/organization the complaint belongs to
     * @param complaintId complaint to fetch
     * @param ownerUserId Data Principal expected to own the complaint
     * @return the complaint
     * @throws ComplaintException thrown with a 404 status if the complaint doesn't exist for this
     *                            org or does not belong to ownerUserId
     */
    public static Complaint getOwnedComplaint(Connection conn, ComplaintDAO complaintDAO, String orgId,
            String complaintId, String ownerUserId) {
        Complaint complaint = getComplaint(conn, complaintDAO, orgId, complaintId);
        if (!complaint.getUserId().equals(ownerUserId)) {
            throw new ComplaintException(ComplaintErrorCode.COMPLAINT_NOT_FOUND,
                    String.format(ComplaintServiceConstants.COMPLAINT_NOT_FOUND_BY_ID_ERROR, complaintId));
        }
        return complaint;
    }

    /**
     * Generates the human-facing REFERENCE_ID (e.g. "CMP-2026-04821") described in the ER diagram.
     * Takes the caller's own {@link Connection} rather than opening one - the count and the
     * complaint insert it feeds into must land in the same transaction, or a concurrent insert
     * between the two could be counted twice (or not at all).
     *
     * @param conn              caller-owned connection this read runs against
     * @param complaintDAO      DAO to read through
     * @param orgId             tenant/organization the new complaint belongs to
     * @param createdTimeMillis the new complaint's CREATED_TIME, whose year becomes part of the
     *                          reference ID
     * @return the newly minted reference ID
     */
    public static String generateReferenceId(Connection conn, ComplaintDAO complaintDAO, String orgId,
            long createdTimeMillis) {
        int year = Instant.ofEpochMilli(createdTimeMillis).atZone(ZoneOffset.UTC).getYear();
        String likePattern = REFERENCE_ID_PREFIX + "-" + year + "-%";
        int existingCount = complaintDAO.countByReferenceIdPrefix(conn, orgId, likePattern);
        int nextSeq = existingCount + 1;
        return String.format("%s-%d-%05d", REFERENCE_ID_PREFIX, year, nextSeq);
    }

    /** fromStatus/toStatus are the raw column/API values; unknown values (not a ComplaintStatus) are rejected. */
    public static boolean isValidTransition(String fromStatus, String toStatus) {
        if (!ComplaintStatus.isValid(fromStatus) || !ComplaintStatus.isValid(toStatus)) {
            return false;
        }
        Set<ComplaintStatus> allowedTargets = ALLOWED_STATUS_TRANSITIONS.get(ComplaintStatus.valueOf(fromStatus));
        return allowedTargets.contains(ComplaintStatus.valueOf(toStatus));
    }

    public static String derivePriority(String category) {
        return categoryToPriority.getOrDefault(category, LOW.name());
    }

    public static boolean isKnownCategory(String category) {
        return category != null && categoryToPriority.containsKey(category.trim());
    }

    /**
     * The current set of valid ComplaintCategory values, each with the priority a complaint in
     * that category is assigned. Reflects the built-in defaults, or whatever {@link #configure}
     * was last called with.
     */
    public static Map<String, String> getCategoryPriorities() {
        return Collections.unmodifiableMap(categoryToPriority);
    }

    /**
     * Replaces the built-in category-to-priority mapping wholesale. Entries whose priority isn't
     * one of the known ComplaintPriority values are dropped; if nothing valid remains, the
     * built-in defaults are kept untouched.
     */
    public static void configure(Map<String, String> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return;
        }
        Map<String, String> validated = new HashMap<>();
        for (Map.Entry<String, String> entry : overrides.entrySet()) {
            String priority = entry.getValue() == null ? null : entry.getValue().trim().toUpperCase();
            if (entry.getKey() != null && ComplaintPriority.isValid(priority)) {
                validated.put(entry.getKey().trim(), priority);
            }
        }
        if (!validated.isEmpty()) {
            categoryToPriority = validated;
        }
    }

    /**
     * Statutory due period for grievance redressal under the DPDP Act. Configurable via
     * deployment.toml's [dpdp_accelerator.complaints] statutory_due_period_days, the same way
     * event notification settings are read - see the DPDPConfigurationService OSGi reference
     * bound into {@link ComplaintServiceDataHolder} by {@code ComplaintServiceComponent},
     * templated into dpdp-accelerator.xml at server startup. Defaults to 90 days if unset.
     */
    public static long getStatutoryDuePeriodMillis() {
        int days = ComplaintServiceDataHolder.getInstance().getConfigurationService()
                .getComplaintsStatutoryDuePeriodDays();
        return days * 24L * 60 * 60 * 1000;
    }

    /**
     * Max attachment size in bytes. Configurable via deployment.toml's [dpdp_accelerator.complaints]
     * attachment_max_size_bytes key, the same way {@link #getStatutoryDuePeriodMillis} reads its
     * own setting. Defaults to 10 MB if unset.
     */
    public static long getAttachmentMaxSizeBytes() {
        return ComplaintServiceDataHolder.getInstance().getConfigurationService()
                .getComplaintsAttachmentMaxSizeBytes();
    }

    /** Max number of files a single upload request may contain. Same configuration source as {@link #getAttachmentMaxSizeBytes}. */
    public static int getAttachmentMaxFilesPerUpload() {
        return ComplaintServiceDataHolder.getInstance().getConfigurationService()
                .getComplaintsAttachmentMaxFilesPerUpload();
    }

    public static boolean isAllowedAttachmentContentType(String contentType) {
        return contentType != null && ALLOWED_CONTENT_TYPES.contains(contentType.trim());
    }
}
