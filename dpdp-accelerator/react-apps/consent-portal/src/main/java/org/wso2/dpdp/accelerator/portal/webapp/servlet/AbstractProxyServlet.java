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

package org.wso2.dpdp.accelerator.portal.webapp.servlet;

import com.fasterxml.jackson.databind.JsonNode;
import org.wso2.dpdp.accelerator.portal.webapp.client.IdentityServerClient;
import org.wso2.dpdp.accelerator.portal.webapp.util.AuthUtil;
import org.wso2.dpdp.accelerator.portal.webapp.util.HttpUtil;
import org.wso2.dpdp.accelerator.portal.webapp.util.PortalConfig;
import org.wso2.dpdp.accelerator.portal.webapp.util.PortalConstants;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Shared plumbing for the servlets that proxy to the Identity Server consent
 * APIs: token resolution, upstream response relay and the {@code {code, message}}
 * error envelope.
 *
 * These servlets always write their own status and body rather than calling
 * {@code sendError}, so the SPA's {@code 404 -> /index.html} fallback never
 * turns an API miss into an HTML page.
 */
public abstract class AbstractProxyServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    /**
     * Builds a client bound to the caller's access token, or writes a 401 and
     * returns null when the split-token pair is missing.
     */
    protected IdentityServerClient resolveClient(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        String accessToken = AuthUtil.resolveAccessToken(request);
        if (accessToken == null) {
            HttpUtil.sendError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    PortalConstants.ERROR_UNAUTHORIZED, "Authentication is required.");
            return null;
        }
        return new IdentityServerClient(PortalConfig.getInstance(getServletContext()), accessToken);
    }

    protected static String readBody(HttpServletRequest request) throws IOException {

        try (BufferedReader reader = request.getReader()) {
            return reader.lines().collect(Collectors.joining());
        }
    }

    /**
     * Relays an upstream result to the SPA, translating error payloads into the
     * portal's error envelope. A 401 is preserved so the SPA's refresh-then-login
     * recovery still triggers.
     */
    protected void relay(IdentityServerClient.Result result, HttpServletResponse response) throws IOException {

        if (result.isSuccess()) {
            response.setStatus(result.getStatus());
            if (result.getBody() != null && !result.getBody().isEmpty()) {
                response.setContentType(PortalConstants.CONTENT_TYPE_JSON);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.getWriter().write(result.getBody());
            }
            return;
        }
        relayError(result, response);
    }

    protected void relayError(IdentityServerClient.Result result, HttpServletResponse response) throws IOException {

        String code;
        String message;
        switch (result.getStatus()) {
            case HttpServletResponse.SC_UNAUTHORIZED:
                code = PortalConstants.ERROR_UNAUTHORIZED;
                message = "Access token is invalid or expired.";
                break;
            case HttpServletResponse.SC_FORBIDDEN:
                code = PortalConstants.ERROR_FORBIDDEN;
                message = "You are not permitted to perform this operation.";
                break;
            default:
                code = PortalConstants.ERROR_UPSTREAM;
                message = "The consent service returned an error.";
                break;
        }
        // Prefer the Identity Server's own code/message when it sent a JSON error.
        try {
            JsonNode body = HttpUtil.mapper().readTree(result.getBody());
            if (body.hasNonNull("code")) {
                code = body.get("code").asText();
            }
            if (body.hasNonNull("message")) {
                message = body.get("message").asText();
            }
        } catch (IOException ignored) {
            // Non-JSON upstream error; keep the generic envelope above.
        }
        HttpUtil.sendError(response, result.getStatus(), code, message);
    }

    protected void sendNotFound(HttpServletResponse response, String path) throws IOException {

        HttpUtil.sendError(response, HttpServletResponse.SC_NOT_FOUND, "NOT_FOUND",
                "No such portal API route: " + path);
    }

    protected void sendUpstreamFailure(HttpServletResponse response) throws IOException {

        HttpUtil.sendError(response, HttpServletResponse.SC_BAD_GATEWAY, PortalConstants.ERROR_UPSTREAM,
                "The consent service is unavailable.");
    }
}
