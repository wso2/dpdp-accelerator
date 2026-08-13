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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriorityMapperTest {

    private static final Map<String, String> DEFAULT_MAPPING = buildDefaultMapping();

    private static Map<String, String> buildDefaultMapping() {
        Map<String, String> defaults = new HashMap<>();
        defaults.put("DATA_BREACH", "CRITICAL");
        defaults.put("UNAUTHORIZED_DATA_SHARING", "HIGH");
        defaults.put("CONSENT_WITHDRAWN_DATA_STILL_USED", "HIGH");
        defaults.put("PURPOSE_VIOLATION", "HIGH");
        defaults.put("DATA_ACCESS_DENIED", "HIGH");
        defaults.put("DATA_ERASURE_NOT_COMPLETED", "MEDIUM");
        defaults.put("DATA_CORRECTION_NOT_COMPLETED", "MEDIUM");
        defaults.put("CONSENT_LIFECYCLE_ISSUE", "MEDIUM");
        defaults.put("EXCESSIVE_DATA_COLLECTION", "MEDIUM");
        defaults.put("OTHER", "LOW");
        return defaults;
    }

    @AfterEach
    void restoreDefaultMapping() {
        // PriorityMapper.configure() replaces its static mapping wholesale with no reset hook,
        // so tests that reconfigure it must restore the defaults to avoid leaking state into
        // other test classes that share this JVM.
        PriorityMapper.configure(DEFAULT_MAPPING);
    }

    @Test
    void derivePriorityReturnsMappedPriorityForKnownCategory() {
        assertEquals("CRITICAL", PriorityMapper.derivePriority("DATA_BREACH"));
        assertEquals("LOW", PriorityMapper.derivePriority("OTHER"));
    }

    @Test
    void derivePriorityReturnsLowForUnknownCategory() {
        assertEquals("LOW", PriorityMapper.derivePriority("SOME_UNKNOWN_CATEGORY"));
    }

    @Test
    void isKnownCategoryReturnsTrueForDefaultCategoriesAndFalseOtherwise() {
        assertTrue(PriorityMapper.isKnownCategory("DATA_BREACH"));
        assertFalse(PriorityMapper.isKnownCategory("NOT_A_CATEGORY"));
        assertFalse(PriorityMapper.isKnownCategory(null));
    }

    @Test
    void getCategoryPrioritiesReflectsTheBuiltInDefaultsByDefault() {
        assertEquals(DEFAULT_MAPPING, PriorityMapper.getCategoryPriorities());
    }

    @Test
    void getCategoryPrioritiesReflectsAConfiguredOverride() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("CUSTOM_CATEGORY", "high");

        PriorityMapper.configure(overrides);

        assertEquals(Map.of("CUSTOM_CATEGORY", "HIGH"), PriorityMapper.getCategoryPriorities());
    }

    @Test
    void getCategoryPrioritiesReturnsAnUnmodifiableView() {
        assertThrows(UnsupportedOperationException.class,
                () -> PriorityMapper.getCategoryPriorities().put("X", "LOW"));
    }

    @Test
    void configureReplacesMappingWholesaleWithValidatedEntries() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("CUSTOM_CATEGORY", "high"); // lower-case should be normalized to upper-case

        PriorityMapper.configure(overrides);

        assertEquals("HIGH", PriorityMapper.derivePriority("CUSTOM_CATEGORY"));
        // wholesale replacement means the previously-known default category is no longer known
        assertFalse(PriorityMapper.isKnownCategory("DATA_BREACH"));
    }

    @Test
    void configureDropsEntriesWithInvalidPriorityValues() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("VALID_ONE", "HIGH");
        overrides.put("INVALID_ONE", "NOT_A_PRIORITY");

        PriorityMapper.configure(overrides);

        assertTrue(PriorityMapper.isKnownCategory("VALID_ONE"));
        assertFalse(PriorityMapper.isKnownCategory("INVALID_ONE"));
    }

    @Test
    void configureKeepsExistingMappingWhenOverridesAreNullOrEmpty() {
        PriorityMapper.configure(null);
        assertTrue(PriorityMapper.isKnownCategory("DATA_BREACH"));

        PriorityMapper.configure(new HashMap<>());
        assertTrue(PriorityMapper.isKnownCategory("DATA_BREACH"));
    }

    @Test
    void configureKeepsExistingMappingWhenAllOverridesAreInvalid() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("SOME_CATEGORY", "NOT_A_PRIORITY");

        PriorityMapper.configure(overrides);

        assertTrue(PriorityMapper.isKnownCategory("DATA_BREACH"));
        assertEquals("CRITICAL", PriorityMapper.derivePriority("DATA_BREACH"));
    }
}
