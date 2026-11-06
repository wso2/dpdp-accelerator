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

import org.wso2.dpdp.accelerator.event.notifications.common.enums.PurposeFilterMode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Utility class for purpose set canonicalization, SHA-256 hashing, and conflict/overlap detection.
 */
public class PurposeOverlapUtils {

    private PurposeOverlapUtils() {
    }

    /**
     * Converts a list of raw purpose strings into a sorted, lowercased, deduplicated set.
     */
    public static Set<String> canonicalize(List<String> purposes) {
        if (purposes == null || purposes.isEmpty()) {
            return Collections.emptySet();
        }
        return purposes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * Computes a canonical SHA-256 hash representation of a purpose list.
     * Returns an empty string for ALL filter mode.
     */
    public static String computePurposeSetHash(PurposeFilterMode mode, List<String> purposes) {
        if (mode == PurposeFilterMode.ALL) {
            return "";
        }
        Set<String> canonicalSet = canonicalize(purposes);
        if (canonicalSet.isEmpty()) {
            return "";
        }
        String joined = String.join(",", canonicalSet);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(joined.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Determines whether two subscription purpose filter modes and purpose sets overlap.
     */
    public static boolean overlaps(PurposeFilterMode mode1, Set<String> set1,
                                   PurposeFilterMode mode2, Set<String> set2) {
        if (mode1 == PurposeFilterMode.ALL || mode2 == PurposeFilterMode.ALL) {
            return true;
        }
        if (mode1 == PurposeFilterMode.EXCEPT && mode2 == PurposeFilterMode.EXCEPT) {
            return true;
        }
        if (mode1 == PurposeFilterMode.EXCEPT || mode2 == PurposeFilterMode.EXCEPT) {
            Set<String> exceptSet = (mode1 == PurposeFilterMode.EXCEPT) ? set1 : set2;
            Set<String> specificSet = (mode1 == PurposeFilterMode.EXCEPT) ? set2 : set1;
            return !exceptSet.containsAll(specificSet);
        }
        return !Collections.disjoint(set1, set2);
    }
}
