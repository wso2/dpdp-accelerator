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
import org.wso2.dpdp.accelerator.portal.webapp.client.ConsentServerClient;
import org.wso2.dpdp.accelerator.portal.webapp.client.IdentityServerClient;
import org.wso2.dpdp.accelerator.portal.webapp.exception.TokenValidationException;
import org.wso2.dpdp.accelerator.portal.webapp.model.AuthenticatedUser;
import org.wso2.dpdp.accelerator.portal.webapp.service.TokenValidator;
import org.wso2.dpdp.accelerator.portal.webapp.util.AuthUtil;
import org.wso2.dpdp.accelerator.portal.webapp.util.HttpUtil;
import org.wso2.dpdp.accelerator.portal.webapp.util.PortalConfig;
import org.wso2.dpdp.accelerator.portal.webapp.util.PortalConstants;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Administrative and catalog endpoints.
 *
 * Purposes and elements ({@code /api/consent-purposes*}, {@code
 * /api/consent-elements*}) are backed by the standalone OpenFGC Consent
 * Server: its path shape matches the SPA's one to one, so no translation is
 * needed, only the {@code org-id} tenant header this BFF adds from the
 * caller's own validated token, and the {@code group-id} header the SPA
 * itself may send through unchanged on create.
 *
 * Consent listing ({@code /api/consents*}) still goes through Identity
 * Server's consent management v2 API, forwarding the caller's own token so
 * Identity Server enforces their {@code internal_consent_mgt_*} scopes.
 */
@WebServlet(urlPatterns = "/api/*")
public class AdminApiServlet extends AbstractProxyServlet {

    private static final long serialVersionUID = 1L;
    private static final Log LOG = LogFactory.getLog(AdminApiServlet.class);

    /** Path segments the portal accepts; anything else is rejected before reaching the server. */
    private static final Pattern SAFE_PATH = Pattern.compile("[A-Za-z0-9._~/-]*");

    private static final String SCOPE_PURPOSES_READ = "portal:purposes:read";
    private static final String SCOPE_PURPOSES_WRITE = "portal:purposes:write";
    private static final String SCOPE_ELEMENTS_READ = "portal:elements:read";
    private static final String SCOPE_ELEMENTS_WRITE = "portal:elements:write";

    /**
     * All methods are routed here rather than through the {@code doXxx} hooks
     * because {@code HttpServlet} has no PATCH hook, and purpose updates use
     * it; routing PUT the same way, alongside it, keeps the dispatch logic
     * in one place instead of split across {@code service} and {@code doPut}.
     */
    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {

        String method = request.getMethod();
        if (!"GET".equals(method) && !"POST".equals(method) && !"PUT".equals(method)
                && !"PATCH".equals(method) && !"DELETE".equals(method)) {
            HttpUtil.sendError(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                    method + " is not supported by this endpoint.");
            return;
        }

        String path = request.getPathInfo() == null ? "" : request.getPathInfo();
        if (!SAFE_PATH.matcher(path).matches()) {
            sendUnsupported(response, path);
            return;
        }

        if (path.startsWith("/consent-purposes") || path.startsWith("/consent-elements")) {
            dispatchToConsentServer(request, response, path, method);
            return;
        }
        dispatchToIdentityServer(request, response, path, method);
    }

    // ---------------------------------------------------- consent-purposes/elements

    private void dispatchToConsentServer(HttpServletRequest request, HttpServletResponse response, String path,
                                          String method) throws IOException {

        PortalConfig config = PortalConfig.getInstance(getServletContext());
        AuthenticatedUser caller = authenticate(request, response, config);
        if (caller == null) {
            return;
        }

        boolean isPurpose = path.startsWith("/consent-purposes");
        String readScope = isPurpose ? SCOPE_PURPOSES_READ : SCOPE_ELEMENTS_READ;
        String writeScope = isPurpose ? SCOPE_PURPOSES_WRITE : SCOPE_ELEMENTS_WRITE;
        boolean isWrite = !"GET".equals(method);
        String requiredScope = isWrite ? writeScope : readScope;
        if (!caller.getScopes().contains(requiredScope)) {
            HttpUtil.sendError(response, HttpServletResponse.SC_FORBIDDEN, PortalConstants.ERROR_FORBIDDEN,
                    "You are not permitted to perform this operation.");
            return;
        }

        String query = request.getQueryString();
        String target = path + (query == null || query.isEmpty() ? "" : "?" + query);
        String groupId = request.getHeader("group-id");
        String orgId = caller.getRawOrgId();

        try {
            ConsentServerClient client = new ConsentServerClient(config);
            ConsentServerClient.Result result;
            switch (method) {
                case "GET":
                    result = client.get(target, orgId);
                    break;
                case "POST":
                    result = client.post(target, readBody(request), orgId, groupId);
                    break;
                case "DELETE":
                    result = client.delete(target, orgId);
                    break;
                default:
                    HttpUtil.sendError(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                            method + " is not supported for " + path);
                    return;
            }
            relayConsentServerResult(result, response);
        } catch (IOException e) {
            LOG.error("Consent Server request failed.", e);
            sendUpstreamFailure(response);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sendUpstreamFailure(response);
        }
    }

    private void relayConsentServerResult(ConsentServerClient.Result result, HttpServletResponse response)
            throws IOException {

        response.setStatus(result.getStatus());
        if (result.getBody() != null && !result.getBody().isEmpty()) {
            response.setContentType(PortalConstants.CONTENT_TYPE_JSON);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(result.getBody());
        }
    }

    private AuthenticatedUser authenticate(HttpServletRequest request, HttpServletResponse response,
                                            PortalConfig config) throws IOException {

        String accessToken = AuthUtil.resolveAccessToken(request);
        if (accessToken == null) {
            HttpUtil.sendError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    PortalConstants.ERROR_UNAUTHORIZED, "Authentication is required.");
            return null;
        }
        try {
            return TokenValidator.getInstance(config).validate(accessToken);
        } catch (TokenValidationException e) {
            LOG.debug("Access token validation failed.", e);
            HttpUtil.sendError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    PortalConstants.ERROR_UNAUTHORIZED, "Access token is invalid or expired.");
            return null;
        }
    }

    // ---------------------------------------------------------------- consents

    private void dispatchToIdentityServer(HttpServletRequest request, HttpServletResponse response, String path,
                                           String method) throws IOException {

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
                case "PUT":
                    result = client.put(target, readBody(request));
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
     * when the Identity Server has no equivalent. Only {@code /consents*} paths
     * reach this any more -- purposes and elements are handled by {@link
     * #dispatchToConsentServer} above.
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
        return null;
    }

    private void sendUnsupported(HttpServletResponse response, String path) throws IOException {

        HttpUtil.sendError(response, HttpServletResponse.SC_NOT_FOUND, "NOT_FOUND",
                "This portal API route is not supported: " + path);
    }
}
