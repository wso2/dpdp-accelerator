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
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.dpdp.accelerator.portal.webapp.client.ConsentServerClient;
import org.wso2.dpdp.accelerator.portal.webapp.exception.TokenValidationException;
import org.wso2.dpdp.accelerator.portal.webapp.model.AuthenticatedUser;
import org.wso2.dpdp.accelerator.portal.webapp.service.ConsentPayloadUtil;
import org.wso2.dpdp.accelerator.portal.webapp.service.TokenValidator;
import org.wso2.dpdp.accelerator.portal.webapp.util.AuthUtil;
import org.wso2.dpdp.accelerator.portal.webapp.util.HttpUtil;
import org.wso2.dpdp.accelerator.portal.webapp.util.LogUtil;
import org.wso2.dpdp.accelerator.portal.webapp.util.PortalConfig;
import org.wso2.dpdp.accelerator.portal.webapp.util.PortalConstants;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Self-service consent endpoints, backed by the standalone OpenFGC Consent
 * Server (not Identity Server's native consent-mgt API, which does not expose
 * a working self-service route on this deployment). The caller's own access
 * token is validated here -- the Consent Server itself does not check bearer
 * tokens -- and its subject becomes the {@code userIds} filter, so a caller
 * can only ever see or act on their own consents.
 */
@WebServlet(urlPatterns = "/me/*")
public class MyConsentsServlet extends AbstractProxyServlet {

    private static final long serialVersionUID = 1L;
    private static final Log LOG = LogFactory.getLog(MyConsentsServlet.class);

    private static final Pattern ITEM_PATH = Pattern.compile("^/consents/([^/]+)$");
    private static final Pattern APPROVE_PATH = Pattern.compile("^/consents/([^/]+)/approve$");
    private static final Pattern REJECT_PATH = Pattern.compile("^/consents/([^/]+)/reject$");
    private static final Pattern REVOKE_PATH = Pattern.compile("^/consents/([^/]+)/revoke$");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

        PortalConfig config = PortalConfig.getInstance(getServletContext());
        AuthenticatedUser caller = authenticate(request, response, config);
        if (caller == null) {
            return;
        }

        String path = request.getPathInfo() == null ? "" : request.getPathInfo();
        try {
            if ("/consents".equals(path) || "/consents/".equals(path)) {
                listConsents(request, response, config, caller);
                return;
            }
            Matcher item = ITEM_PATH.matcher(path);
            if (item.matches()) {
                getConsent(response, config, caller, item.group(1));
                return;
            }
            sendNotFound(response, path);
        } catch (IOException e) {
            LOG.error("Self-service consent request failed.", e);
            sendUpstreamFailure(response);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sendUpstreamFailure(response);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {

        PortalConfig config = PortalConfig.getInstance(getServletContext());
        AuthenticatedUser caller = authenticate(request, response, config);
        if (caller == null) {
            return;
        }

        String path = request.getPathInfo() == null ? "" : request.getPathInfo();
        try {
            Matcher approve = APPROVE_PATH.matcher(path);
            if (approve.matches()) {
                approveConsent(request, response, config, caller, approve.group(1));
                return;
            }
            Matcher reject = REJECT_PATH.matcher(path);
            if (reject.matches()) {
                rejectConsent(response, config, caller, reject.group(1));
                return;
            }
            Matcher revoke = REVOKE_PATH.matcher(path);
            if (revoke.matches()) {
                revokeConsent(response, config, caller, revoke.group(1));
                return;
            }
            sendNotFound(response, path);
        } catch (IOException e) {
            LOG.error("Self-service consent action failed.", e);
            sendUpstreamFailure(response);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sendUpstreamFailure(response);
        }
    }

    private void listConsents(HttpServletRequest request, HttpServletResponse response, PortalConfig config,
                               AuthenticatedUser caller) throws IOException, InterruptedException {

        StringBuilder query = new StringBuilder("?userIds=").append(encode(caller.getUserId()));
        for (String param : new String[]{"consentStatuses", "purposeName", "groupIds", "elementName",
                "elementVersion", "sort", "fromTime", "toTime", "limit", "offset"}) {
            String value = request.getParameter(param);
            if (value != null && !value.isEmpty()) {
                query.append('&').append(param).append('=').append(encode(value));
            }
        }

        ConsentServerClient.Result result = new ConsentServerClient(config)
                .get("/api/v1/consents" + query, caller.getRawOrgId());
        relay(result, response);
    }

    private void getConsent(HttpServletResponse response, PortalConfig config, AuthenticatedUser caller,
                             String consentId) throws IOException, InterruptedException {

        ConsentServerClient client = new ConsentServerClient(config);
        ConsentServerClient.Result current = client.get("/api/v1/consents/" + encode(consentId),
                caller.getRawOrgId());
        if (current.getStatus() == HttpServletResponse.SC_OK
                && !ConsentPayloadUtil.consentBelongsTo(current.getBody(), caller.getUserId())) {
            LOG.warn("Consent " + LogUtil.safe(consentId) + " does not belong to caller "
                    + LogUtil.safe(caller.getUserId()));
            sendConsentNotFound(response);
            return;
        }
        relay(current, response);
    }

    private void approveConsent(HttpServletRequest request, HttpServletResponse response, PortalConfig config,
                                 AuthenticatedUser caller, String consentId) throws IOException,
            InterruptedException {

        JsonNode selections;
        try {
            String body = readBody(request);
            selections = body.isEmpty() ? HttpUtil.mapper().createArrayNode() : HttpUtil.mapper().readTree(body);
            if (!selections.isArray()) {
                throw new IOException("expected a JSON array");
            }
        } catch (IOException e) {
            HttpUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, PortalConstants.ERROR_INVALID_PAYLOAD,
                    "invalid request payload");
            return;
        }

        ConsentServerClient client = new ConsentServerClient(config);
        ConsentServerClient.Result current = client.get("/api/v1/consents/" + encode(consentId),
                caller.getRawOrgId());
        if (current.getStatus() != HttpServletResponse.SC_OK) {
            relay(current, response);
            return;
        }
        JsonNode consent = HttpUtil.mapper().readTree(current.getBody());
        if (!ConsentPayloadUtil.consentBelongsTo(current.getBody(), caller.getUserId())) {
            sendConsentNotFound(response);
            return;
        }

        ObjectNode payload;
        try {
            payload = ConsentPayloadUtil.buildApprovalUpdatePayload(consent, selections, caller.getUserId());
        } catch (IOException e) {
            HttpUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, PortalConstants.ERROR_INVALID_PAYLOAD,
                    "invalid request payload");
            return;
        }
        String groupId = consent.path("groupId").asText("");

        ConsentServerClient.Result result = client.put("/api/v1/consents/" + encode(consentId),
                HttpUtil.mapper().writeValueAsString(payload), caller.getRawOrgId(), groupId);
        sendAcknowledgement(response, result);
    }

    private void rejectConsent(HttpServletResponse response, PortalConfig config, AuthenticatedUser caller,
                                String consentId) throws IOException, InterruptedException {

        ConsentServerClient client = new ConsentServerClient(config);
        ConsentServerClient.Result current = client.get("/api/v1/consents/" + encode(consentId),
                caller.getRawOrgId());
        if (current.getStatus() != HttpServletResponse.SC_OK) {
            relay(current, response);
            return;
        }
        JsonNode consent = HttpUtil.mapper().readTree(current.getBody());
        if (!ConsentPayloadUtil.consentBelongsTo(current.getBody(), caller.getUserId())) {
            sendConsentNotFound(response);
            return;
        }

        ObjectNode payload;
        try {
            payload = ConsentPayloadUtil.buildRejectionUpdatePayload(consent, caller.getUserId());
        } catch (IOException e) {
            HttpUtil.sendError(response, HttpServletResponse.SC_CONFLICT, "INVALID_CONSENT_STATE",
                    "this consent cannot be rejected in its current state.");
            return;
        }
        String groupId = consent.path("groupId").asText("");

        ConsentServerClient.Result result = client.put("/api/v1/consents/" + encode(consentId),
                HttpUtil.mapper().writeValueAsString(payload), caller.getRawOrgId(), groupId);
        sendAcknowledgement(response, result);
    }

    private void revokeConsent(HttpServletResponse response, PortalConfig config, AuthenticatedUser caller,
                                String consentId) throws IOException, InterruptedException {

        ConsentServerClient client = new ConsentServerClient(config);
        ConsentServerClient.Result current = client.get("/api/v1/consents/" + encode(consentId),
                caller.getRawOrgId());
        if (current.getStatus() != HttpServletResponse.SC_OK) {
            relay(current, response);
            return;
        }
        String groupId = ConsentPayloadUtil.ownedConsentGroupId(current.getBody(), caller.getUserId());
        if (groupId == null) {
            sendConsentNotFound(response);
            return;
        }

        ObjectNode payload = HttpUtil.mapper().createObjectNode();
        payload.put("actionBy", caller.getUserId());
        ConsentServerClient.Result result = client.post("/api/v1/consents/" + encode(consentId) + "/revoke",
                HttpUtil.mapper().writeValueAsString(payload), caller.getRawOrgId(), groupId);
        sendAcknowledgement(response, result);
    }

    /**
     * Approve/reject/revoke answer with the updated consent body; the SPA
     * only checks for success, but an explicit acknowledgement keeps every
     * action response shaped the same regardless of what the Consent Server
     * chose to echo back.
     */
    private void sendAcknowledgement(HttpServletResponse response, ConsentServerClient.Result result)
            throws IOException {

        if (!result.isSuccess()) {
            relay(result, response);
            return;
        }
        ObjectNode body = HttpUtil.mapper().createObjectNode();
        body.put("status", "OK");
        HttpUtil.sendJson(response, HttpServletResponse.SC_OK, body);
    }

    private void relay(ConsentServerClient.Result result, HttpServletResponse response) throws IOException {

        response.setStatus(result.getStatus());
        if (result.getBody() != null && !result.getBody().isEmpty()) {
            response.setContentType(PortalConstants.CONTENT_TYPE_JSON);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(result.getBody());
        }
    }

    private void sendConsentNotFound(HttpServletResponse response) throws IOException {

        HttpUtil.sendError(response, HttpServletResponse.SC_NOT_FOUND, "CONSENT_NOT_FOUND", "consent not found");
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

    private static String encode(String value) {

        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
