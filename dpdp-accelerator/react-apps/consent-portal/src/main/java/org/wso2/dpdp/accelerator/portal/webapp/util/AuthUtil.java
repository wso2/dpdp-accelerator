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

import javax.servlet.http.HttpServletRequest;

/**
 * Reconstructs the user's access token from the split-token contract: the SPA
 * sends part 1 as the bearer header while the browser attaches the HttpOnly
 * part 2 cookie. Scripts can never read part 2, and cross-site requests can't
 * forge the header, so the combination doubles as CSRF protection.
 */
public final class AuthUtil {

    private static final String BEARER_PREFIX = "Bearer ";

    private AuthUtil() {
    }

    public static String resolveAccessToken(HttpServletRequest request) {

        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String part1 = authorization.substring(BEARER_PREFIX.length()).trim();
        if (part1.isEmpty()) {
            return null;
        }
        String part2 = CookieUtil.getCookieValue(request, PortalConstants.ACCESS_TOKEN_PART2_COOKIE);
        if (part2 == null || part2.isEmpty()) {
            return null;
        }
        return part1 + part2;
    }

    public static String resolveIdToken(HttpServletRequest request) {

        String part1 = CookieUtil.getCookieValue(request, PortalConstants.ID_TOKEN_PART1_COOKIE);
        String part2 = CookieUtil.getCookieValue(request, PortalConstants.ID_TOKEN_PART2_COOKIE);
        if (part1 == null || part2 == null) {
            return null;
        }
        return part1 + part2;
    }
}
