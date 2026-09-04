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
import java.util.LinkedHashSet;
import java.util.Set;

public final class AnonymizationRequest {

    private final String tenantDomain;
    private final String sourceUserId;
    private final String pseudonym;
    private final Set<String> explicitUsernames;
    private final ExecutionMode executionMode;

    public AnonymizationRequest(String tenantDomain, String sourceUserId, String pseudonym,
                                Set<String> explicitUsernames, ExecutionMode executionMode) {
        this.tenantDomain = tenantDomain;
        this.sourceUserId = sourceUserId;
        this.pseudonym = pseudonym;
        this.explicitUsernames = explicitUsernames == null
                ? Collections.<String>emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(explicitUsernames));
        this.executionMode = executionMode;
    }

    public String getTenantDomain() {
        return tenantDomain;
    }

    public String getSourceUserId() {
        return sourceUserId;
    }

    public String getPseudonym() {
        return pseudonym;
    }

    public Set<String> getExplicitUsernames() {
        return explicitUsernames;
    }

    public ExecutionMode getExecutionMode() {
        return executionMode;
    }
}
