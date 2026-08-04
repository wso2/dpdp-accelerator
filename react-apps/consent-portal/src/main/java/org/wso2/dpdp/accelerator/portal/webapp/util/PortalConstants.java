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

    public static final int AUTH_TRANSACTION_MAX_AGE_SECONDS = 300;
    public static final int REFRESH_COOKIE_MAX_AGE_SECONDS = 86400;

    public static final String ERROR_UNAUTHORIZED = "UNAUTHORIZED";
    public static final String ERROR_FORBIDDEN = "FORBIDDEN";
    public static final String ERROR_BAD_REQUEST = "BAD_REQUEST";
    public static final String ERROR_UPSTREAM = "UPSTREAM_ERROR";

    public static final String CONTENT_TYPE_JSON = "application/json";
    public static final String CONTENT_TYPE_FORM = "application/x-www-form-urlencoded";
}
