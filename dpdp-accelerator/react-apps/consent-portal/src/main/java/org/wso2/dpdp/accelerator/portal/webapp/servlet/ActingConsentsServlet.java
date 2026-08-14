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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.dpdp.accelerator.portal.webapp.client.ConsentServerClient;
import org.wso2.dpdp.accelerator.portal.webapp.client.NomineeServiceClient;
import org.wso2.dpdp.accelerator.portal.webapp.exception.TokenValidationException;
import org.wso2.dpdp.accelerator.portal.webapp.model.MaskToken;
import org.wso2.dpdp.accelerator.portal.webapp.service.MaskTokenVerifier;
import org.wso2.dpdp.accelerator.portal.webapp.service.ScopeMapper;
import org.wso2.dpdp.accelerator.portal.webapp.util.CookieUtil;
import org.wso2.dpdp.accelerator.portal.webapp.util.HttpUtil;
import org.wso2.dpdp.accelerator.portal.webapp.util.LogUtil;
import org.wso2.dpdp.accelerator.portal.webapp.util.PortalConfig;
import org.wso2.dpdp.accelerator.portal.webapp.util.PortalConstants;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Acting-mode consent operations, exercised with a verified impersonation
 * ("mask") token instead of the caller's own login token. Every route runs
 * through {@link #enforce}: cryptographic verification, the scope ceiling
 * fixed when the mask token was minted, and a live re-check against Nominee
 * Service's nomination gate, so a deactivation or permission edit an owner
 * makes takes effect on the nominee's very next request rather than waiting
 * for the token to expire.
 */
@WebServlet(urlPatterns = {"/acting-api/consents", "/acting-api/consents/*"})
public class ActingConsentsServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Log LOG = LogFactory.getLog(ActingConsentsServlet.class);

    private static final String PERMISSION_CONSENT_VIEW = "CONSENT_VIEW";
    private static final String PERMISSION_CONSENT_REVOKE = "CONSENT_REVOKE";
    private static final String PERMISSION_CONSENT_APPROVE = "CONSENT_APPROVE";

    private static final Pattern ITEM_PATH = Pattern.compile("^/([^/]+)$");
    private static final Pattern REVOKE_PATH = Pattern.compile("^/([^/]+)/revoke$");
    private static final Pattern APPROVE_PATH = Pattern.compile("^/([^/]+)/approve$");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.isEmpty() || "/".equals(pathInfo)) {
            listConsents(request, response);
            return;
        }
        Matcher item = ITEM_PATH.matcher(pathInfo);
        if (item.matches()) {
            getConsent(request, response, item.group(1));
            return;
        }
        sendNotFound(response, pathInfo);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {

        String pathInfo = request.getPathInfo() == null ? "" : request.getPathInfo();
        Matcher revoke = REVOKE_PATH.matcher(pathInfo);
        if (revoke.matches()) {
            revokeConsent(request, response, revoke.group(1));
            return;
        }
        Matcher approve = APPROVE_PATH.matcher(pathInfo);
        if (approve.matches()) {
            approveConsent(request, response, approve.group(1));
            return;
        }
        sendNotFound(response, pathInfo);
    }

    // ------------------------------------------------------------------ routes

    private void listConsents(HttpServletRequest request, HttpServletResponse response) throws IOException {

        PortalConfig config = PortalConfig.getInstance(getServletContext());
        MaskToken mask = enforce(request, response, config, ScopeMapper.PORTAL_CONSENTS_READ_SELF,
                PERMISSION_CONSENT_VIEW, null);
        if (mask == null) {
            return;
        }

        StringBuilder query = new StringBuilder("?userIds=").append(encode(mask.getOwner()));
        for (String param : new String[]{"consentStatuses", "purposeName", "groupIds", "fromTime", "toTime",
                "limit", "offset"}) {
            String value = request.getParameter(param);
            if (value != null && !value.isEmpty()) {
                query.append('&').append(param).append('=').append(encode(value));
            }
        }

        try {
            ConsentServerClient.Result result = new ConsentServerClient(config)
                    .get("/api/v1/consents" + query, mask.getOrgId());
            relay(result, response);
        } catch (IOException | InterruptedException e) {
            handleUpstreamFailure(response, e);
        }
    }

    private void getConsent(HttpServletRequest request, HttpServletResponse response, String consentId)
            throws IOException {

        PortalConfig config = PortalConfig.getInstance(getServletContext());
        MaskToken mask = enforce(request, response, config, ScopeMapper.PORTAL_CONSENTS_READ_SELF,
                PERMISSION_CONSENT_VIEW, consentId);
        if (mask == null) {
            return;
        }

        try {
            ConsentServerClient client = new ConsentServerClient(config);
            ConsentServerClient.Result current = client.get("/api/v1/consents/" + encode(consentId),
                    mask.getOrgId());
            if (current.getStatus() == HttpServletResponse.SC_OK && !consentBelongsTo(current.getBody(),
                    mask.getOwner())) {
                LOG.warn("Consent " + LogUtil.safe(consentId) + " does not belong to owner "
                        + LogUtil.safe(mask.getOwner()));
                sendConsentNotFound(response);
                return;
            }
            relay(current, response);
        } catch (IOException | InterruptedException e) {
            handleUpstreamFailure(response, e);
        }
    }

    private void revokeConsent(HttpServletRequest request, HttpServletResponse response, String consentId)
            throws IOException {

        PortalConfig config = PortalConfig.getInstance(getServletContext());
        MaskToken mask = enforce(request, response, config, ScopeMapper.PORTAL_CONSENTS_WRITE_SELF,
                PERMISSION_CONSENT_REVOKE, consentId);
        if (mask == null) {
            return;
        }

        try {
            ConsentServerClient client = new ConsentServerClient(config);
            ConsentServerClient.Result current = client.get("/api/v1/consents/" + encode(consentId),
                    mask.getOrgId());
            if (current.getStatus() != HttpServletResponse.SC_OK) {
                relay(current, response);
                return;
            }
            String groupId = ownedConsentGroupId(current.getBody(), mask.getOwner());
            if (groupId == null) {
                LOG.warn("Consent " + LogUtil.safe(consentId) + " does not belong to owner "
                        + LogUtil.safe(mask.getOwner()));
                sendConsentNotFound(response);
                return;
            }

            ObjectNode payload = HttpUtil.mapper().createObjectNode();
            payload.put("actionBy", mask.getNominee());
            ConsentServerClient.Result result = client.post(
                    "/api/v1/consents/" + encode(consentId) + "/revoke",
                    HttpUtil.mapper().writeValueAsString(payload), mask.getOrgId(), groupId);
            relay(result, response);
        } catch (IOException | InterruptedException e) {
            handleUpstreamFailure(response, e);
        }
    }

    private void approveConsent(HttpServletRequest request, HttpServletResponse response, String consentId)
            throws IOException {

        PortalConfig config = PortalConfig.getInstance(getServletContext());
        MaskToken mask = enforce(request, response, config, ScopeMapper.PORTAL_CONSENTS_APPROVE_SELF,
                PERMISSION_CONSENT_APPROVE, consentId);
        if (mask == null) {
            return;
        }

        JsonNode selections;
        try {
            String body = readBody(request);
            selections = body.isEmpty() ? HttpUtil.mapper().createArrayNode() : HttpUtil.mapper().readTree(body);
            if (!selections.isArray()) {
                throw new IOException("expected a JSON array");
            }
            for (JsonNode selection : selections) {
                if (isBlank(selection, "purposeId") || isBlank(selection, "purposeVersion")
                        || isBlank(selection, "elementId") || isBlank(selection, "elementVersion")) {
                    throw new IOException("invalid approval selection");
                }
            }
        } catch (IOException e) {
            HttpUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, PortalConstants.ERROR_INVALID_PAYLOAD,
                    "invalid request payload");
            return;
        }

        try {
            ConsentServerClient client = new ConsentServerClient(config);
            ConsentServerClient.Result current = client.get("/api/v1/consents/" + encode(consentId),
                    mask.getOrgId());
            if (current.getStatus() != HttpServletResponse.SC_OK) {
                relay(current, response);
                return;
            }
            JsonNode consent = HttpUtil.mapper().readTree(current.getBody());
            if (!consentBelongsTo(current.getBody(), mask.getOwner())) {
                LOG.warn("Consent " + LogUtil.safe(consentId) + " does not belong to owner "
                        + LogUtil.safe(mask.getOwner()));
                sendConsentNotFound(response);
                return;
            }

            ObjectNode payload;
            try {
                payload = buildApprovalUpdatePayload(consent, selections, mask.getOwner());
            } catch (IOException e) {
                HttpUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                        PortalConstants.ERROR_INVALID_PAYLOAD, "invalid request payload");
                return;
            }
            String groupId = consent.path("groupId").asText("");

            ConsentServerClient.Result result = client.put("/api/v1/consents/" + encode(consentId),
                    HttpUtil.mapper().writeValueAsString(payload), mask.getOrgId(), groupId);
            relay(result, response);
        } catch (IOException | InterruptedException e) {
            handleUpstreamFailure(response, e);
        }
    }

    // ------------------------------------------------------------- authorization

    /**
     * The single authorization decision for every acting route. Order matters:
     * verify the token cryptographically first, so nothing downstream ever
     * reads an unauthenticated claim; then the token's scope ceiling; then the
     * live gate. Every failure path denies.
     */
    private MaskToken enforce(HttpServletRequest request, HttpServletResponse response, PortalConfig config,
                               String requiredScope, String requiredPermission, String resourceId)
            throws IOException {

        String rawToken = maskTokenFrom(request, config);
        if (rawToken == null || rawToken.isEmpty()) {
            HttpUtil.sendError(response, HttpServletResponse.SC_UNAUTHORIZED, PortalConstants.ERROR_INVALID_TOKEN,
                    "missing impersonation token");
            return null;
        }

        MaskToken mask;
        try {
            mask = MaskTokenVerifier.getInstance(config).verify(rawToken);
        } catch (TokenValidationException e) {
            LOG.warn("Mask token rejected.", e);
            HttpUtil.sendError(response, HttpServletResponse.SC_UNAUTHORIZED, PortalConstants.ERROR_INVALID_TOKEN,
                    "invalid impersonation token");
            return null;
        }

        String claimedOwner = request.getHeader(PortalConstants.ACTING_OWNER_HEADER);
        if (claimedOwner != null && !claimedOwner.isBlank() && !claimedOwner.trim().equals(mask.getOwner())) {
            LOG.warn("Caller expected owner " + LogUtil.safe(claimedOwner) + " but token carries "
                    + LogUtil.safe(mask.getOwner()));
            HttpUtil.sendError(response, HttpServletResponse.SC_CONFLICT,
                    PortalConstants.ERROR_ACTING_OWNER_MISMATCH, "this acting session is for a different owner");
            return null;
        }

        NomineeServiceClient nomineeService = new NomineeServiceClient(config);

        if (!mask.hasScope(requiredScope)) {
            LOG.warn("Mask token for owner " + LogUtil.safe(mask.getOwner()) + " lacks required scope "
                    + requiredScope);
            nomineeService.recordAction(mask.getOwner(), mask.getNominee(), requiredPermission, resourceId, false,
                    "token does not carry " + requiredScope);
            HttpUtil.sendError(response, HttpServletResponse.SC_FORBIDDEN, PortalConstants.ERROR_INSUFFICIENT_SCOPE,
                    "impersonation token does not carry " + requiredScope);
            return null;
        }

        NomineeServiceClient.GateDecision decision;
        try {
            decision = nomineeService.permissions(mask.getOwner(), mask.getNominee());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.error("Nomination gate call failed for owner " + LogUtil.safe(mask.getOwner()), e);
            HttpUtil.sendError(response, HttpServletResponse.SC_BAD_GATEWAY, PortalConstants.ERROR_UPSTREAM,
                    "permission check failed");
            return null;
        }
        if (!decision.isActive()) {
            nomineeService.recordAction(mask.getOwner(), mask.getNominee(), requiredPermission, resourceId, false,
                    "no active nomination");
            HttpUtil.sendError(response, HttpServletResponse.SC_FORBIDDEN,
                    PortalConstants.ERROR_NOT_ACTIVE_NOMINEE, "no active nomination for this owner");
            return null;
        }
        if (!decision.hasPermission(requiredPermission)) {
            nomineeService.recordAction(mask.getOwner(), mask.getNominee(), requiredPermission, resourceId, false,
                    "permission not granted");
            HttpUtil.sendError(response, HttpServletResponse.SC_FORBIDDEN, PortalConstants.ERROR_PERMISSION_DENIED,
                    "owner did not grant " + requiredPermission + " to this nominee");
            return null;
        }

        nomineeService.recordAction(mask.getOwner(), mask.getNominee(), requiredPermission, resourceId, true, "");
        return mask;
    }

    private static String maskTokenFrom(HttpServletRequest request, PortalConfig config) {

        String cookie = CookieUtil.getCookieValue(request, PortalConstants.ACTING_TOKEN_COOKIE);
        if (cookie != null && !cookie.isBlank()) {
            return cookie.trim();
        }
        String header = request.getHeader("Authorization");
        if (header == null || header.length() < 7 || !header.regionMatches(true, 0, "bearer ", 0, 7)) {
            return null;
        }
        return header.substring(7).trim();
    }

    // ---------------------------------------------------------- consent shaping

    /** Reports whether ownerId holds an authorization on the described consent. */
    private static boolean consentBelongsTo(String body, String ownerId) {

        try {
            JsonNode consent = HttpUtil.mapper().readTree(body);
            for (JsonNode authorization : consent.path("authorizations")) {
                if (ownerId.equals(authorization.path("userId").asText(null))) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    /** Returns the consent's trusted group id if userId holds an authorization on it, else null. */
    private static String ownedConsentGroupId(String body, String userId) throws IOException {

        JsonNode consent = HttpUtil.mapper().readTree(body);
        String trimmedUser = userId.trim();
        for (JsonNode authorization : consent.path("authorizations")) {
            String authUser = authorization.path("userId").asText(null);
            if (authUser != null && authUser.trim().equals(trimmedUser)) {
                return consent.path("groupId").asText("").trim();
            }
        }
        return null;
    }

    /**
     * Builds the consent update payload for an approval action, mirroring the
     * Go BFF's {@code me.Service.BuildApprovalUpdatePayload}: mandatory
     * elements are always approved, an optional element is approved only when
     * it matches a selection, and the approval is recorded as an
     * "authorisation" entry for the owner (who performed it is carried by the
     * audit trail, not this record).
     */
    private static ObjectNode buildApprovalUpdatePayload(JsonNode consent, JsonNode selections, String ownerId)
            throws IOException {

        Set<String> selectedKeys = new LinkedHashSet<>();
        for (JsonNode selection : selections) {
            selectedKeys.add(approvalKey(selection.path("purposeId").asText(""),
                    selection.path("purposeVersion").asText(""), selection.path("elementId").asText(""),
                    selection.path("elementVersion").asText("")));
        }
        Set<String> matchedKeys = new LinkedHashSet<>();

        ArrayNode updatedPurposes = HttpUtil.mapper().createArrayNode();
        for (JsonNode purpose : consent.path("purposes")) {
            String purposeId = purpose.path("purposeId").asText("");
            String purposeVersion = purpose.path("version").asText("");

            ObjectNode updatedPurpose = HttpUtil.mapper().createObjectNode();
            updatedPurpose.put("name", purpose.path("name").asText(""));
            updatedPurpose.put("version", purposeVersion);
            ArrayNode updatedElements = updatedPurpose.putArray("elements");

            for (JsonNode element : purpose.path("elements")) {
                boolean mandatory = element.path("mandatory").asBoolean(false);
                boolean approved = element.path("approved").asBoolean(false);
                if (mandatory) {
                    approved = true;
                } else {
                    String key = approvalKey(purposeId, purposeVersion, element.path("elementId").asText(""),
                            element.path("version").asText(""));
                    if (selectedKeys.contains(key)) {
                        approved = true;
                        matchedKeys.add(key);
                    }
                }
                ObjectNode updatedElement = updatedElements.addObject();
                updatedElement.put("name", element.path("name").asText(""));
                updatedElement.put("namespace", element.path("namespace").asText(""));
                updatedElement.put("version", element.path("version").asText(""));
                updatedElement.put("approved", approved);
                if (element.has("value")) {
                    updatedElement.set("value", element.get("value"));
                }
            }
            updatedPurposes.add(updatedPurpose);
        }
        if (matchedKeys.size() != selectedKeys.size()) {
            throw new IOException("invalid approval selection");
        }

        ArrayNode updatedAuthorizations = HttpUtil.mapper().createArrayNode();
        boolean ownerAuthorizationUpdated = false;
        for (JsonNode authorization : consent.path("authorizations")) {
            String authUserId = authorization.path("userId").asText(null);
            ObjectNode updated = HttpUtil.mapper().createObjectNode();
            if (authUserId != null && authUserId.trim().toLowerCase(java.util.Locale.ROOT)
                    .equals(ownerId.trim().toLowerCase(java.util.Locale.ROOT))) {
                updated.put("userId", ownerId);
                updated.put("type", "authorisation");
                updated.put("status", "APPROVED");
                updated.set("resources", HttpUtil.mapper().createObjectNode());
                ownerAuthorizationUpdated = true;
            } else {
                if (authUserId != null) {
                    updated.put("userId", authUserId);
                }
                updated.put("type", authorization.path("type").asText(""));
                updated.put("status", authorization.path("status").asText(""));
                JsonNode resources = authorization.get("resources");
                updated.set("resources", resources != null ? resources : HttpUtil.mapper().createObjectNode());
            }
            updatedAuthorizations.add(updated);
        }
        if (!ownerAuthorizationUpdated) {
            ObjectNode ownerAuthorization = HttpUtil.mapper().createObjectNode();
            ownerAuthorization.put("userId", ownerId);
            ownerAuthorization.put("type", "authorisation");
            ownerAuthorization.put("status", "APPROVED");
            ownerAuthorization.set("resources", HttpUtil.mapper().createObjectNode());
            updatedAuthorizations.add(ownerAuthorization);
        }

        ObjectNode payload = HttpUtil.mapper().createObjectNode();
        payload.put("type", consent.path("type").asText(""));
        copyIfPresent(consent, payload, "expirationTime");
        copyIfPresent(consent, payload, "recurringIndicator");
        copyIfPresent(consent, payload, "dataAccessValidityDuration");
        copyIfPresent(consent, payload, "frequency");
        payload.set("purposes", updatedPurposes);
        JsonNode attributes = consent.get("attributes");
        payload.set("attributes", attributes != null ? attributes : HttpUtil.mapper().createObjectNode());
        payload.set("authorizations", updatedAuthorizations);
        return payload;
    }

    private static void copyIfPresent(JsonNode source, ObjectNode target, String field) {

        if (source.has(field) && !source.get(field).isNull()) {
            target.set(field, source.get(field));
        }
    }

    private static String approvalKey(String purposeId, String purposeVersion, String elementId,
                                       String elementVersion) {

        return purposeId + ' ' + purposeVersion + ' ' + elementId + ' ' + elementVersion;
    }

    private static boolean isBlank(JsonNode selection, String field) {

        String value = selection.path(field).asText("");
        return value.trim().isEmpty();
    }

    // ------------------------------------------------------------------- utils

    private void relay(ConsentServerClient.Result result, HttpServletResponse response) throws IOException {

        response.setStatus(result.getStatus());
        if (result.getBody() != null && !result.getBody().isEmpty()) {
            response.setContentType(PortalConstants.CONTENT_TYPE_JSON);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(result.getBody());
        }
    }

    private void handleUpstreamFailure(HttpServletResponse response, Exception e) throws IOException {

        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        LOG.error("Acting consent request failed.", e);
        HttpUtil.sendError(response, HttpServletResponse.SC_BAD_GATEWAY, PortalConstants.ERROR_UPSTREAM,
                "The consent service is unavailable.");
    }

    private void sendConsentNotFound(HttpServletResponse response) throws IOException {

        HttpUtil.sendError(response, HttpServletResponse.SC_NOT_FOUND, PortalConstants.ERROR_CONSENT_NOT_FOUND,
                "consent not found");
    }

    private void sendNotFound(HttpServletResponse response, String path) throws IOException {

        HttpUtil.sendError(response, HttpServletResponse.SC_NOT_FOUND, PortalConstants.ERROR_NOT_FOUND,
                "No such portal API route: " + path);
    }

    private static String encode(String value) {

        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String readBody(HttpServletRequest request) throws IOException {

        try (BufferedReader reader = request.getReader()) {
            return reader.lines().collect(Collectors.joining());
        }
    }
}
