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
import org.wso2.dpdp.accelerator.portal.webapp.client.ImpersonationClient;
import org.wso2.dpdp.accelerator.portal.webapp.client.NomineeServiceClient;
import org.wso2.dpdp.accelerator.portal.webapp.exception.TokenValidationException;
import org.wso2.dpdp.accelerator.portal.webapp.model.MaskToken;
import org.wso2.dpdp.accelerator.portal.webapp.service.MaskTokenVerifier;
import org.wso2.dpdp.accelerator.portal.webapp.service.OAuthService;
import org.wso2.dpdp.accelerator.portal.webapp.util.AuthUtil;
import org.wso2.dpdp.accelerator.portal.webapp.util.CookieUtil;
import org.wso2.dpdp.accelerator.portal.webapp.util.HttpUtil;
import org.wso2.dpdp.accelerator.portal.webapp.util.LogUtil;
import org.wso2.dpdp.accelerator.portal.webapp.util.PortalConfig;
import org.wso2.dpdp.accelerator.portal.webapp.util.PortalConstants;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.stream.Collectors;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * The acting-as ("impersonation") session lifecycle: starting the Identity
 * Server delegation flow, exchanging its result for a usable mask token, and
 * ending the session. See {@code frontend/src/features/nominee/actingAs}.
 */
@WebServlet(urlPatterns = {"/acting-api/start", "/acting-api/exchange", "/acting-api/stop"})
public class ActingAuthServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Log LOG = LogFactory.getLog(ActingAuthServlet.class);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

        if (!"/acting-api/start".equals(request.getServletPath())) {
            HttpUtil.sendError(response, HttpServletResponse.SC_NOT_FOUND, PortalConstants.ERROR_NOT_FOUND,
                    "No such portal API route: " + request.getServletPath());
            return;
        }
        startActing(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {

        String path = request.getServletPath();
        if ("/acting-api/exchange".equals(path)) {
            exchangeActing(request, response);
            return;
        }
        if ("/acting-api/stop".equals(path)) {
            stopActing(request, response);
            return;
        }
        HttpUtil.sendError(response, HttpServletResponse.SC_NOT_FOUND, PortalConstants.ERROR_NOT_FOUND,
                "No such portal API route: " + path);
    }

    /**
     * Must be a real top-level navigation, not fetch(): Identity Server
     * identifies the impersonator from its own session cookie, and answers
     * with a cross-origin redirect whose fragment only the browser can read.
     */
    private void startActing(HttpServletRequest request, HttpServletResponse response) throws IOException {

        String ownerId = request.getParameter("ownerId");
        if (ownerId == null || ownerId.isBlank()) {
            HttpUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, PortalConstants.ERROR_INVALID_PAYLOAD,
                    "ownerId is required");
            return;
        }

        PortalConfig config = PortalConfig.getInstance(getServletContext());
        String state = OAuthService.generateRandomToken();
        String nonce = OAuthService.generateRandomToken();

        CookieUtil.addCookie(response, PortalConstants.ACTING_STATE_COOKIE, state + "|" + ownerId,
                config.getPortalBasePath(), PortalConstants.ACTING_STATE_MAX_AGE_SECONDS, true,
                config.isCookieSecure(), "Strict");

        String authorizeUrl = new ImpersonationClient(config).buildAuthorizeUrl(ownerId, state, nonce);
        response.sendRedirect(authorizeUrl);
    }

    /**
     * The frontend reads {@code subject_token} from the URL fragment Identity
     * Server redirected to and posts it here. The exchange runs server-side
     * because it needs the client secret, which only the BFF holds -- a
     * subject token leaked from the fragment is not usable on its own.
     */
    private void exchangeActing(HttpServletRequest request, HttpServletResponse response) throws IOException {

        PortalConfig config = PortalConfig.getInstance(getServletContext());
        JsonNode body;
        try {
            body = HttpUtil.mapper().readTree(readBody(request));
        } catch (IOException e) {
            HttpUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, PortalConstants.ERROR_INVALID_PAYLOAD,
                    "invalid request body");
            return;
        }
        String subjectToken = body.path("subjectToken").asText("").trim();
        String requestState = body.path("state").asText("").trim();
        if (subjectToken.isEmpty() || requestState.isEmpty()) {
            HttpUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, PortalConstants.ERROR_INVALID_PAYLOAD,
                    "subjectToken and state are required");
            return;
        }

        String stateCookie = CookieUtil.getCookieValue(request, PortalConstants.ACTING_STATE_COOKIE);
        String[] stateParts = stateCookie == null ? null : stateCookie.split("\\|", 2);
        if (stateParts == null || stateParts.length != 2 || stateParts[0].isEmpty()) {
            HttpUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, PortalConstants.ERROR_INVALID_STATE,
                    "no acting flow in progress");
            return;
        }
        String expectedState = stateParts[0];
        String ownerId = stateParts[1];

        if (!MessageDigest.isEqual(expectedState.getBytes(StandardCharsets.UTF_8),
                requestState.getBytes(StandardCharsets.UTF_8))) {
            CookieUtil.clearCookie(response, PortalConstants.ACTING_STATE_COOKIE, config.getPortalBasePath(),
                    config.isCookieSecure());
            HttpUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, PortalConstants.ERROR_INVALID_STATE,
                    "acting state mismatch");
            return;
        }
        CookieUtil.clearCookie(response, PortalConstants.ACTING_STATE_COOKIE, config.getPortalBasePath(),
                config.isCookieSecure());

        String actorToken = AuthUtil.resolveAccessToken(request);
        if (actorToken == null) {
            HttpUtil.sendError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    PortalConstants.ERROR_NOT_AUTHENTICATED, "you must be signed in to start an acting session.");
            return;
        }

        NomineeServiceClient nomineeService = new NomineeServiceClient(config);
        ImpersonationClient.Token token;
        try {
            token = new ImpersonationClient(config).exchangeSubjectToken(subjectToken, actorToken);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.error("Impersonation token exchange failed for owner " + LogUtil.safe(ownerId), e);
            nomineeService.record(ownerId, "", NomineeServiceClient.EVENT_SESSION_DENIED, "exchange failed");
            HttpUtil.sendError(response, HttpServletResponse.SC_BAD_GATEWAY, PortalConstants.ERROR_UPSTREAM,
                    "identity server is unavailable.");
            return;
        }

        MaskToken mask;
        try {
            mask = MaskTokenVerifier.getInstance(config).verify(token.getAccessToken());
        } catch (TokenValidationException e) {
            LOG.error("Exchanged token failed mask verification for owner " + LogUtil.safe(ownerId), e);
            nomineeService.record(ownerId, "", NomineeServiceClient.EVENT_SESSION_DENIED,
                    "exchanged token failed verification");
            HttpUtil.sendError(response, HttpServletResponse.SC_BAD_GATEWAY, PortalConstants.ERROR_INVALID_TOKEN,
                    "identity server returned an unusable impersonation token.");
            return;
        }

        if (!ownerId.equals(mask.getOwner())) {
            LOG.warn("Exchanged token subject " + LogUtil.safe(mask.getOwner()) + " does not match started session "
                    + LogUtil.safe(ownerId));
            nomineeService.record(ownerId, mask.getNominee(), NomineeServiceClient.EVENT_SESSION_DENIED,
                    "session mismatch");
            HttpUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, PortalConstants.ERROR_INVALID_STATE,
                    "acting session mismatch");
            return;
        }

        long ttlSeconds = mask.getExpiry().getEpochSecond() - Instant.now().getEpochSecond();
        if (ttlSeconds <= 0) {
            HttpUtil.sendError(response, HttpServletResponse.SC_BAD_GATEWAY, PortalConstants.ERROR_INVALID_TOKEN,
                    "identity server returned an expired token.");
            return;
        }

        CookieUtil.addCookie(response, PortalConstants.ACTING_TOKEN_COOKIE, token.getAccessToken(),
                config.getPortalBasePath(), (int) Math.min(ttlSeconds, Integer.MAX_VALUE), true,
                config.isCookieSecure(), "Strict");

        nomineeService.record(mask.getOwner(), mask.getNominee(), NomineeServiceClient.EVENT_SESSION_STARTED,
                "scopes=" + String.join(",", mask.getScopes()));

        ObjectNode responseBody = HttpUtil.mapper().createObjectNode();
        responseBody.put("ownerId", mask.getOwner());
        responseBody.put("nomineeId", mask.getNominee());
        com.fasterxml.jackson.databind.node.ArrayNode scopesNode = responseBody.putArray("scopes");
        for (String scope : mask.getScopes()) {
            scopesNode.add(scope);
        }
        responseBody.put("expiresAt", mask.getExpiry().toString());
        HttpUtil.sendJson(response, HttpServletResponse.SC_OK, responseBody);
    }

    /**
     * Ends the session for this browser only. Identity Server cannot revoke an
     * already-issued impersonation token, which is why its lifetime is kept
     * short and why every acting request re-checks the nomination gate
     * regardless (see {@link ActingConsentsServlet}).
     */
    private void stopActing(HttpServletRequest request, HttpServletResponse response) throws IOException {

        PortalConfig config = PortalConfig.getInstance(getServletContext());
        CookieUtil.clearCookie(response, PortalConstants.ACTING_TOKEN_COOKIE, config.getPortalBasePath(),
                config.isCookieSecure());
        CookieUtil.clearCookie(response, PortalConstants.ACTING_STATE_COOKIE, config.getPortalBasePath(),
                config.isCookieSecure());
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    private static String readBody(HttpServletRequest request) throws IOException {

        try (java.io.BufferedReader reader = request.getReader()) {
            return reader.lines().collect(Collectors.joining());
        }
    }
}
