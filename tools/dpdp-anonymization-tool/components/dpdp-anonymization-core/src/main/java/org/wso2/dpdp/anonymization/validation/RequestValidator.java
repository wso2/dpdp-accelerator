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

package org.wso2.dpdp.anonymization.validation;

import org.wso2.dpdp.anonymization.model.AnonymizationRequest;

import java.util.UUID;

public final class RequestValidator {

    private RequestValidator() {
    }

    public static void validate(AnonymizationRequest request) throws AnonymizationException {
        if (request == null) {
            throw new AnonymizationException("Anonymization request is required.");
        }
        if (isBlank(request.getTenantDomain())) {
            throw new AnonymizationException("Tenant domain is required.");
        }
        String source = canonicalUuid(request.getSourceUserId(), "Source user ID");
        String target = canonicalUuid(request.getPseudonym(), "Pseudonym");
        if (source.equals(target)) {
            throw new AnonymizationException("Source user ID and pseudonym must be different.");
        }
        if (request.getExecutionMode() == null) {
            throw new AnonymizationException("Execution mode is required.");
        }
    }

    public static String canonicalUuid(String value, String label) throws AnonymizationException {
        if (isBlank(value)) {
            throw new AnonymizationException(label + " is required.");
        }
        try {
            String canonical = UUID.fromString(value.trim()).toString();
            if (!canonical.equalsIgnoreCase(value.trim())) {
                throw new IllegalArgumentException("non-canonical UUID");
            }
            return canonical;
        } catch (IllegalArgumentException e) {
            throw new AnonymizationException(label + " must be a canonical UUID.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
