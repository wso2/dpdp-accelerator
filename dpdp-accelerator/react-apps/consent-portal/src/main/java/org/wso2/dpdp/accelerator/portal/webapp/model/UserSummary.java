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

package org.wso2.dpdp.accelerator.portal.webapp.model;

/**
 * A directory entry resolved from Identity Server's SCIM2 API, in the shape
 * the SPA expects for nominee/owner lookups (frontend/src/features/nominee).
 */
public class UserSummary {

    private final String id;
    private final String name;
    private final String email;

    public UserSummary(String id, String name, String email) {

        this.id = id;
        this.name = name;
        this.email = email;
    }

    public String getId() {

        return id;
    }

    public String getName() {

        return name;
    }

    public String getEmail() {

        return email;
    }

    /**
     * "Given Family", falling back to a bare name attribute, then username.
     * Mirrors the Go BFF's identityserver display-name resolution so the two
     * backends present a person the same way.
     */
    public static String displayName(String givenName, String familyName, String username) {

        String given = givenName == null ? "" : givenName.trim();
        String family = familyName == null ? "" : familyName.trim();
        if (!given.isEmpty() || !family.isEmpty()) {
            return (given + " " + family).trim();
        }
        return username == null ? "" : username.trim();
    }
}
