package org.wso2.dpdp.accelerator.complaint.mgt.service.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.DAOConstants.PRIORITY_CRITICAL;
import static org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.DAOConstants.PRIORITY_HIGH;
import static org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.DAOConstants.PRIORITY_LOW;
import static org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.DAOConstants.PRIORITY_MEDIUM;

/**
 * ComplaintPriority is "server-derived, never client-supplied" per the API spec. This maps each
 * ComplaintCategory to a default priority, and the set of its keys is also the set of valid
 * ComplaintCategory values (see ComplaintServiceImpl#isValidCategory). The mapping below is the
 * built-in default; it is replaced wholesale by the [categoryPriority] table in deployment.toml
 * when present, via configure() - see Main#loadCategoryPriorityMapping.
 */
public class PriorityMapper {

    private static final Set<String> VALID_PRIORITIES =
            Set.of(PRIORITY_CRITICAL, PRIORITY_HIGH, PRIORITY_MEDIUM, PRIORITY_LOW);

    private static volatile Map<String, String> categoryToPriority = buildDefaultMapping();

    private PriorityMapper() {
    }

    private static Map<String, String> buildDefaultMapping() {
        Map<String, String> defaults = new HashMap<>();
        defaults.put("DATA_BREACH", PRIORITY_CRITICAL);
        defaults.put("UNAUTHORIZED_DATA_SHARING", PRIORITY_HIGH);
        defaults.put("CONSENT_WITHDRAWN_DATA_STILL_USED", PRIORITY_HIGH);
        defaults.put("PURPOSE_VIOLATION", PRIORITY_HIGH);
        defaults.put("DATA_ACCESS_DENIED", PRIORITY_HIGH);
        defaults.put("DATA_ERASURE_NOT_COMPLETED", PRIORITY_MEDIUM);
        defaults.put("DATA_CORRECTION_NOT_COMPLETED", PRIORITY_MEDIUM);
        defaults.put("CONSENT_LIFECYCLE_ISSUE", PRIORITY_MEDIUM);
        defaults.put("EXCESSIVE_DATA_COLLECTION", PRIORITY_MEDIUM);
        defaults.put("OTHER", PRIORITY_LOW);
        return defaults;
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
            if (entry.getKey() != null && VALID_PRIORITIES.contains(priority)) {
                validated.put(entry.getKey().trim(), priority);
            }
        }
        if (!validated.isEmpty()) {
            categoryToPriority = validated;
        }
    }

    public static String derivePriority(String category) {
        return categoryToPriority.getOrDefault(category, PRIORITY_LOW);
    }

    public static boolean isKnownCategory(String category) {
        return category != null && categoryToPriority.containsKey(category.trim());
    }

    /**
     * The current set of valid ComplaintCategory values, each with the priority a complaint in
     * that category is assigned. Reflects the built-in defaults, or the [categoryPriority]
     * override from deployment.toml if one is configured.
     */
    public static Map<String, String> getCategoryPriorities() {
        return Collections.unmodifiableMap(categoryToPriority);
    }
}
