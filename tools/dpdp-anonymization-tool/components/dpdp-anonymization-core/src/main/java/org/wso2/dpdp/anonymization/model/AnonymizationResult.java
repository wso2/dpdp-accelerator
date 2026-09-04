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

package org.wso2.dpdp.anonymization.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class AnonymizationResult {

    private AnonymizationStatus status;
    private final Map<String, Long> counts = new LinkedHashMap<>();
    private final Set<String> trustedUsernames = new LinkedHashSet<>();
    private int discoveredAliasCount;

    public AnonymizationStatus getStatus() {
        return status;
    }

    public void setStatus(AnonymizationStatus status) {
        this.status = status;
    }

    public void increment(String key) {
        counts.put(key, getCount(key) + 1L);
    }

    public long getCount(String key) {
        Long value = counts.get(key);
        return value == null ? 0L : value;
    }

    public Map<String, Long> getCounts() {
        return Collections.unmodifiableMap(counts);
    }

    public int getDiscoveredAliasCount() {
        return discoveredAliasCount;
    }

    public void setDiscoveredAliasCount(int discoveredAliasCount) {
        this.discoveredAliasCount = discoveredAliasCount;
    }

    public Set<String> getTrustedUsernames() {
        return Collections.unmodifiableSet(trustedUsernames);
    }

    public void setTrustedUsernames(Set<String> usernames) {
        trustedUsernames.clear();
        if (usernames != null) {
            trustedUsernames.addAll(usernames);
        }
    }
}
