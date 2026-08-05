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

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Issues, reads and clears the split-token cookies shared with the SPA.
 *
 * Tokens are split in two: part 1 is readable by the SPA (sent back as the
 * bearer header), part 2 is HttpOnly so scripts can never reconstruct the
 * whole token. The servlet API has no SameSite support, so Set-Cookie headers
 * are written manually.
 */
public final class CookieUtil {

    private CookieUtil() {
    }

    public static String[] split(String token) {

        int middle = token.length() / 2;
        return new String[]{token.substring(0, middle), token.substring(middle)};
    }

    private static void assertHeaderSafe(String value) {

        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '\r' || character == '\n' || character == ';') {
                throw new IllegalArgumentException("Illegal character in cookie attribute.");
            }
        }
    }

    public static void addCookie(HttpServletResponse response, String name, String value, String path,
                                 int maxAgeSeconds, boolean httpOnly, boolean secure, String sameSite) {

        assertHeaderSafe(name);
        assertHeaderSafe(value);
        assertHeaderSafe(path);
        StringBuilder cookie = new StringBuilder();
        cookie.append(name).append('=').append(value)
                .append("; Path=").append(path)
                .append("; Max-Age=").append(maxAgeSeconds)
                .append("; SameSite=").append(sameSite);
        if (secure) {
            cookie.append("; Secure");
        }
        if (httpOnly) {
            cookie.append("; HttpOnly");
        }
        response.addHeader("Set-Cookie", cookie.toString());
    }

    public static void addSplitTokenCookies(HttpServletResponse response, String part1Name, String part2Name,
                                            String token, String path, int maxAgeSeconds, boolean part2HttpOnly,
                                            boolean secure) {

        String[] parts = split(token);
        addCookie(response, part1Name, parts[0], path, maxAgeSeconds, false, secure, "Strict");
        addCookie(response, part2Name, parts[1], path, maxAgeSeconds, part2HttpOnly, secure, "Strict");
    }

    public static String getCookieValue(HttpServletRequest request, String name) {

        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public static void clearCookie(HttpServletResponse response, String name, String path, boolean secure) {

        addCookie(response, name, "", path, 0, false, secure, "Strict");
    }

    public static void clearAllAuthCookies(HttpServletResponse response, String path, boolean secure) {

        clearCookie(response, PortalConstants.ACCESS_TOKEN_PART1_COOKIE, path, secure);
        clearCookie(response, PortalConstants.ACCESS_TOKEN_PART2_COOKIE, path, secure);
        clearCookie(response, PortalConstants.REFRESH_TOKEN_PART1_COOKIE, path, secure);
        clearCookie(response, PortalConstants.REFRESH_TOKEN_PART2_COOKIE, path, secure);
        clearCookie(response, PortalConstants.ID_TOKEN_PART1_COOKIE, path, secure);
        clearCookie(response, PortalConstants.ID_TOKEN_PART2_COOKIE, path, secure);
    }
}
