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

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.dpdp.accelerator.portal.webapp.client.IdentityServerClient;
import org.wso2.dpdp.accelerator.portal.webapp.util.HttpUtil;

import java.io.IOException;
import java.util.regex.Pattern;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Administrative and catalog endpoints, backed by the Identity Server consent
 * management v2 API. The caller's own token is forwarded, so the Identity
 * Server decides what each user may do based on their
 * {@code internal_consent_mgt_*} scopes.
 *
 * The SPA's resource names are translated to their v2 equivalents:
 * {@code /api/consents -> /consents}, {@code /api/consent-purposes -> /purposes},
 * {@code /api/consent-elements -> /elements}.
 */
@WebServlet(urlPatterns = "/api/*")
public class AdminApiServlet extends AbstractProxyServlet {

    private static final long serialVersionUID = 1L;
    private static final Log LOG = LogFactory.getLog(AdminApiServlet.class);

    /** Path segments the portal accepts; anything else is rejected before reaching the server. */
    private static final Pattern SAFE_PATH = Pattern.compile("[A-Za-z0-9._~/-]*");

    /**
     * All methods are routed here rather than through the {@code doXxx} hooks
     * because {@code HttpServlet} has no PATCH hook and consent updates use it.
     */
    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {

        String method = request.getMethod();
        if (!"GET".equals(method) && !"POST".equals(method)
                && !"PATCH".equals(method) && !"DELETE".equals(method)) {
            HttpUtil.sendError(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                    method + " is not supported by this endpoint.");
            return;
        }
        dispatch(request, response, method);
    }

    private void dispatch(HttpServletRequest request, HttpServletResponse response, String method)
            throws IOException {

        String path = request.getPathInfo() == null ? "" : request.getPathInfo();
        String upstreamPath = translate(path);
        if (upstreamPath == null) {
            sendUnsupported(response, path);
            return;
        }

        IdentityServerClient client = resolveClient(request, response);
        if (client == null) {
            return;
        }

        String query = request.getQueryString();
        String target = IdentityServerClient.CONSENT_MGT_V2_API + upstreamPath
                + (query == null || query.isEmpty() ? "" : "?" + query);

        try {
            IdentityServerClient.Result result;
            switch (method) {
                case "GET":
                    result = client.get(target);
                    break;
                case "POST":
                    result = client.post(target, readBody(request));
                    break;
                case "PATCH":
                    result = client.patch(target, readBody(request));
                    break;
                case "DELETE":
                    result = client.delete(target);
                    break;
                default:
                    sendNotFound(response, path);
                    return;
            }
            // Revoke answers 204 with no body, but the SPA parses JSON for this call.
            if (result.isSuccess() && upstreamPath.endsWith("/revoke")) {
                ObjectNode body = HttpUtil.mapper().createObjectNode();
                body.put("status", "OK");
                HttpUtil.sendJson(response, HttpServletResponse.SC_OK, body);
                return;
            }
            relay(result, response);
        } catch (IOException e) {
            LOG.error("Consent management request failed.", e);
            sendUpstreamFailure(response);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sendUpstreamFailure(response);
        }
    }

    /**
     * Maps a portal API path to its consent management v2 path, or returns null
     * when the Identity Server has no equivalent.
     */
    static String translate(String path) {

        if (!SAFE_PATH.matcher(path).matches()) {
            return null;
        }
        if (path.startsWith("/consents")) {
            // Status history has no v2 equivalent.
            if (path.endsWith("/history")) {
                return null;
            }
            return path;
        }
        if (path.startsWith("/consent-purposes")) {
            return "/purposes" + path.substring("/consent-purposes".length());
        }
        if (path.startsWith("/consent-elements")) {
            // Elements are not versioned in the Identity Server.
            if (path.contains("/versions")) {
                return null;
            }
            return "/elements" + path.substring("/consent-elements".length());
        }
        return null;
    }

    private void sendUnsupported(HttpServletResponse response, String path) throws IOException {

        HttpUtil.sendError(response, HttpServletResponse.SC_NOT_FOUND, "NOT_FOUND",
                "This portal API route is not supported by WSO2 Identity Server consent management: " + path);
    }
}
