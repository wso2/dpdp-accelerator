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

import org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintPriority;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintPriority.CRITICAL;
import static org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintPriority.HIGH;
import static org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintPriority.LOW;
import static org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintPriority.MEDIUM;

/**
 * ComplaintPriority is "server-derived, never client-supplied" per the API spec. This maps each
 * ComplaintCategory to a default priority, and the set of its keys is also the set of valid
 * ComplaintCategory values (see ComplaintServiceImpl#isValidCategory). The mapping below is the
 * built-in default; it is replaced wholesale by the [categoryPriority] table in deployment.toml
 * when present, via configure() - see AppBootstrap#loadDeploymentConfig in the endpoint webapp
 * module, which is invoked once at servlet context startup by AppContextListener.
 */
public class PriorityMapper {

    private static volatile Map<String, String> categoryToPriority = buildDefaultMapping();

    private PriorityMapper() {
    }

    private static Map<String, String> buildDefaultMapping() {
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

    public static String derivePriority(String category) {
        return categoryToPriority.getOrDefault(category, LOW.name());
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
