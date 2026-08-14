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

package org.wso2.dpdp.accelerator.portal.webapp.util;

/**
 * Shared constants for the consent portal BFF.
 */
public final class PortalConstants {

    private PortalConstants() {
    }

    // Cookie names are part of the frontend contract (see frontend/src/utils/authClient.ts).
    public static final String ACCESS_TOKEN_PART1_COOKIE = "portal-at-p1";
    public static final String ACCESS_TOKEN_PART2_COOKIE = "portal-at-p2";
    public static final String REFRESH_TOKEN_PART1_COOKIE = "portal-rt-p1";
    public static final String REFRESH_TOKEN_PART2_COOKIE = "portal-rt-p2";
    public static final String ID_TOKEN_PART1_COOKIE = "portal-id-p1";
    public static final String ID_TOKEN_PART2_COOKIE = "portal-id-p2";
    public static final String AUTH_TRANSACTION_COOKIE = "portal-auth-txn";

    // Acting-as (nominee delegation) cookies. Separate from the login cookies
    // above so that ending an acting session never disturbs the nominee's own
    // login, and a mask token can never be mistaken for a first-party access
    // token (see frontend/src/features/nominee).
    public static final String ACTING_STATE_COOKIE = "portal-acting-state";
    public static final String ACTING_TOKEN_COOKIE = "portal-acting-token";
    public static final String ACTING_OWNER_HEADER = "X-Acting-Owner";

    public static final int AUTH_TRANSACTION_MAX_AGE_SECONDS = 300;
    public static final int REFRESH_COOKIE_MAX_AGE_SECONDS = 86400;
    public static final int ACTING_STATE_MAX_AGE_SECONDS = 300;

    public static final String ERROR_UNAUTHORIZED = "UNAUTHORIZED";
    public static final String ERROR_FORBIDDEN = "FORBIDDEN";
    public static final String ERROR_BAD_REQUEST = "BAD_REQUEST";
    public static final String ERROR_UPSTREAM = "UPSTREAM_ERROR";
    public static final String ERROR_NOT_FOUND = "NOT_FOUND";
    public static final String ERROR_ADMIN_REQUIRED = "ADMIN_REQUIRED";
    public static final String ERROR_INVALID_TOKEN = "INVALID_TOKEN";
    public static final String ERROR_VERIFIER_UNAVAILABLE = "VERIFIER_UNAVAILABLE";
    public static final String ERROR_ACTING_OWNER_MISMATCH = "ACTING_OWNER_MISMATCH";
    public static final String ERROR_INSUFFICIENT_SCOPE = "INSUFFICIENT_SCOPE";
    public static final String ERROR_NOT_ACTIVE_NOMINEE = "NOT_ACTIVE_NOMINEE";
    public static final String ERROR_PERMISSION_DENIED = "PERMISSION_DENIED";
    public static final String ERROR_CONSENT_NOT_FOUND = "CONSENT_NOT_FOUND";
    public static final String ERROR_INVALID_STATE = "INVALID_STATE";
    public static final String ERROR_NOT_AUTHENTICATED = "NOT_AUTHENTICATED";
    public static final String ERROR_INVALID_PAYLOAD = "INVALID_PAYLOAD";
    public static final String ERROR_INTERNAL = "INTERNAL_ERROR";

    public static final String CONTENT_TYPE_JSON = "application/json";
    public static final String CONTENT_TYPE_FORM = "application/x-www-form-urlencoded";
}
