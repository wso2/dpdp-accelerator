/**
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 * <p>
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 *     http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.dpdp.accelerator.event.notifications.common.util;

import org.testng.Assert;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.PurposeFilterMode;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

public class PurposeOverlapUtilsTest {

    @Test
    public void testCanonicalize() {
        Set<String> canonical = PurposeOverlapUtils.canonicalize(Arrays.asList(" Marketing ", "BILLING ", "marketing"));
        Assert.assertEquals(canonical.size(), 2);
        Assert.assertTrue(canonical.contains("marketing"));
        Assert.assertTrue(canonical.contains("billing"));
    }

    @Test
    public void testCanonicalizeLocaleIndependence() {
        java.util.Locale originalDefault = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
            Set<String> canonical = PurposeOverlapUtils.canonicalize(Collections.singletonList("PROFILE"));
            Assert.assertEquals(canonical.size(), 1);
            Assert.assertTrue(canonical.contains("profile"),
                    "PROFILE should canonicalize to standard ASCII 'profile' regardless of default locale");
        } finally {
            java.util.Locale.setDefault(originalDefault);
        }
    }

    @Test
    public void testComputePurposeSetHash() {
        String hash1 = PurposeOverlapUtils.computePurposeSetHash(PurposeFilterMode.ALL, Arrays.asList("marketing"));
        Assert.assertEquals(hash1, "");

        String hash2 = PurposeOverlapUtils.computePurposeSetHash(PurposeFilterMode.SPECIFIC, Arrays.asList("marketing", "billing"));
        String hash3 = PurposeOverlapUtils.computePurposeSetHash(PurposeFilterMode.SPECIFIC, Arrays.asList("billing", "marketing"));
        Assert.assertFalse(hash2.isEmpty());
        Assert.assertEquals(hash2, hash3);
    }

    @Test
    public void testOverlapsAllMode() {
        Set<String> setA = PurposeOverlapUtils.canonicalize(Arrays.asList("marketing"));
        Set<String> setB = PurposeOverlapUtils.canonicalize(Arrays.asList("billing"));

        Assert.assertTrue(PurposeOverlapUtils.overlaps(PurposeFilterMode.ALL, setA, PurposeFilterMode.SPECIFIC, setB));
        Assert.assertTrue(PurposeOverlapUtils.overlaps(PurposeFilterMode.SPECIFIC, setA, PurposeFilterMode.ALL, setB));
        Assert.assertTrue(PurposeOverlapUtils.overlaps(PurposeFilterMode.ALL, Collections.emptySet(), PurposeFilterMode.ALL, Collections.emptySet()));
    }

    @Test
    public void testOverlapsExceptAndExcept() {
        Set<String> setA = PurposeOverlapUtils.canonicalize(Arrays.asList("marketing"));
        Set<String> setB = PurposeOverlapUtils.canonicalize(Arrays.asList("billing"));
        Assert.assertTrue(PurposeOverlapUtils.overlaps(PurposeFilterMode.EXCEPT, setA, PurposeFilterMode.EXCEPT, setB));
    }

    @Test
    public void testOverlapsExceptAndSpecific() {
        Set<String> exceptSet = PurposeOverlapUtils.canonicalize(Arrays.asList("marketing", "billing"));
        Set<String> specificSubset = PurposeOverlapUtils.canonicalize(Arrays.asList("marketing"));
        Set<String> specificOverlapping = PurposeOverlapUtils.canonicalize(Arrays.asList("marketing", "analytics"));

        // specificSubset is fully inside exceptSet -> no overlap
        Assert.assertFalse(PurposeOverlapUtils.overlaps(PurposeFilterMode.EXCEPT, exceptSet, PurposeFilterMode.SPECIFIC, specificSubset));

        // specificOverlapping contains 'analytics' which is not in exceptSet -> overlaps!
        Assert.assertTrue(PurposeOverlapUtils.overlaps(PurposeFilterMode.EXCEPT, exceptSet, PurposeFilterMode.SPECIFIC, specificOverlapping));
    }

    @Test
    public void testOverlapsSpecificAndSpecific() {
        Set<String> set1 = PurposeOverlapUtils.canonicalize(Arrays.asList("marketing", "billing"));
        Set<String> set2 = PurposeOverlapUtils.canonicalize(Arrays.asList("analytics", "billing"));
        Set<String> set3 = PurposeOverlapUtils.canonicalize(Arrays.asList("support"));

        Assert.assertTrue(PurposeOverlapUtils.overlaps(PurposeFilterMode.SPECIFIC, set1, PurposeFilterMode.SPECIFIC, set2));
        Assert.assertFalse(PurposeOverlapUtils.overlaps(PurposeFilterMode.SPECIFIC, set1, PurposeFilterMode.SPECIFIC, set3));
    }
}
