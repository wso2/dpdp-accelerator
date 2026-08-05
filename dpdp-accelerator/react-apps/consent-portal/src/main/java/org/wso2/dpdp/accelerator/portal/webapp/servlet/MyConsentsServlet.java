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
import org.wso2.dpdp.accelerator.portal.webapp.client.IdentityServerClient;
import org.wso2.dpdp.accelerator.portal.webapp.util.HttpUtil;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Self-service consent endpoints, backed by the Identity Server's end-user
 * consent API ({@code /api/users/v1/me/consents}). The Identity Server derives
 * the subject from the forwarded token, so no user identifier is ever sent from
 * the browser — which also means a caller can only ever see their own consents.
 */
@WebServlet(urlPatterns = "/me/*")
public class MyConsentsServlet extends AbstractProxyServlet {

    private static final long serialVersionUID = 1L;
    private static final Log LOG = LogFactory.getLog(MyConsentsServlet.class);

    private static final Pattern CONSENT_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");
    private static final int DEFAULT_PAGE_SIZE = 10;
    /** Upper bound on rows pulled from the Identity Server for in-memory paging. */
    private static final int MAX_FETCH = 200;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

        String path = request.getPathInfo() == null ? "" : request.getPathInfo();
        IdentityServerClient client = resolveClient(request, response);
        if (client == null) {
            return;
        }

        try {
            if ("/consents".equals(path) || "/consents/".equals(path)) {
                listConsents(request, response, client);
                return;
            }
            String consentId = consentIdFrom(path, "");
            if (consentId != null) {
                relay(client.get(IdentityServerClient.USER_CONSENT_API + "/" + consentId), response);
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

        String path = request.getPathInfo() == null ? "" : request.getPathInfo();
        IdentityServerClient client = resolveClient(request, response);
        if (client == null) {
            return;
        }

        try {
            // The Identity Server authorizes a consent as a whole; the SPA's
            // per-element selection has no equivalent and is deliberately ignored.
            String approveId = consentIdFrom(path, "/approve");
            if (approveId != null) {
                authorize(response, client, approveId, "APPROVED");
                return;
            }
            String rejectId = consentIdFrom(path, "/reject");
            if (rejectId != null) {
                authorize(response, client, rejectId, "REJECTED");
                return;
            }
            String revokeId = consentIdFrom(path, "/revoke");
            if (revokeId != null) {
                IdentityServerClient.Result result = client.post(
                        IdentityServerClient.USER_CONSENT_API + "/" + revokeId + "/revoke", null);
                sendAcknowledgement(response, result);
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

    private void authorize(HttpServletResponse response, IdentityServerClient client, String consentId, String state)
            throws IOException, InterruptedException {

        // The Identity Server accepts authorize on a consent in any state, which
        // would move an already REVOKED or EXPIRED consent back to ACTIVE. A
        // withdrawn consent must stay withdrawn, so only PENDING is authorizable.
        IdentityServerClient.Result current = client.get(
                IdentityServerClient.USER_CONSENT_API + "/" + consentId);
        if (!current.isSuccess()) {
            relayError(current, response);
            return;
        }
        JsonNode consent = HttpUtil.mapper().readTree(current.getBody());
        String currentState = consent.path("state").asText("");
        if (!"PENDING".equals(currentState)) {
            HttpUtil.sendError(response, HttpServletResponse.SC_CONFLICT, "INVALID_CONSENT_STATE",
                    "Only a pending consent can be approved or rejected; this consent is " + currentState + ".");
            return;
        }

        IdentityServerClient.Result result = client.post(
                IdentityServerClient.USER_CONSENT_API + "/" + consentId + "/authorize",
                "{\"state\":\"" + state + "\"}");
        sendAcknowledgement(response, result);
    }

    /**
     * The Identity Server answers authorize and revoke with 200 and an empty
     * body; the SPA parses JSON, so send an explicit acknowledgement.
     */
    private void sendAcknowledgement(HttpServletResponse response, IdentityServerClient.Result result)
            throws IOException {

        if (!result.isSuccess()) {
            relayError(result, response);
            return;
        }
        ObjectNode body = HttpUtil.mapper().createObjectNode();
        body.put("status", "OK");
        HttpUtil.sendJson(response, HttpServletResponse.SC_OK, body);
    }

    /**
     * The Identity Server's self-service list returns only
     * {@code {id, serviceId, state, timestamp}} per row, so each row on the
     * requested page is expanded with a concurrent detail lookup before being
     * wrapped in the search envelope the SPA expects.
     */
    private void listConsents(HttpServletRequest request, HttpServletResponse response, IdentityServerClient client)
            throws IOException, InterruptedException {

        int limit = positiveIntParam(request, "limit", DEFAULT_PAGE_SIZE);
        int offset = Math.max(0, intParam(request, "offset", 0));

        StringBuilder query = new StringBuilder("?limit=").append(Math.min(offset + limit + 1, MAX_FETCH));
        String state = firstValue(request.getParameter("consentStatuses"));
        if (state != null) {
            query.append("&state=").append(URLEncoder.encode(state, StandardCharsets.UTF_8));
        }
        String serviceId = request.getParameter("serviceId");
        if (serviceId != null && !serviceId.isEmpty()) {
            query.append("&serviceId=").append(URLEncoder.encode(serviceId, StandardCharsets.UTF_8));
        }

        IdentityServerClient.Result listResult = client.get(IdentityServerClient.USER_CONSENT_API + query);
        if (!listResult.isSuccess()) {
            relayError(listResult, response);
            return;
        }

        JsonNode summaries = HttpUtil.mapper().readTree(listResult.getBody());
        List<String> pageIds = new ArrayList<>();
        for (int i = offset; i < summaries.size() && pageIds.size() < limit; i++) {
            JsonNode id = summaries.get(i).get("id");
            if (id != null) {
                pageIds.add(id.asText());
            }
        }

        List<CompletableFuture<IdentityServerClient.Result>> lookups = new ArrayList<>();
        for (String id : pageIds) {
            lookups.add(client.getAsync(IdentityServerClient.USER_CONSENT_API + "/" + id));
        }

        ArrayNode data = HttpUtil.mapper().createArrayNode();
        for (int i = 0; i < lookups.size(); i++) {
            IdentityServerClient.Result detail = lookups.get(i).join();
            if (detail.isSuccess()) {
                data.add(HttpUtil.mapper().readTree(detail.getBody()));
            } else {
                // Fall back to the summary so one failed lookup cannot blank the page.
                LOG.warn("Consent detail lookup failed with status " + detail.getStatus());
                data.add(summaries.get(offset + i));
            }
        }

        ObjectNode body = HttpUtil.mapper().createObjectNode();
        body.set("data", data);
        ObjectNode metadata = body.putObject("metadata");
        // The Identity Server list is cursor based and reports no grand total, so
        // this is the count seen so far rather than the total across all pages.
        metadata.put("total", summaries.size());
        metadata.put("offset", offset);
        metadata.put("count", data.size());
        metadata.put("limit", limit);
        HttpUtil.sendJson(response, HttpServletResponse.SC_OK, body);
    }

    /**
     * Extracts the consent id from {@code /consents/{id}<suffix>}, or null when
     * the path does not match or the id is not well formed.
     */
    private static String consentIdFrom(String path, String suffix) {

        String prefix = "/consents/";
        if (!path.startsWith(prefix) || !path.endsWith(suffix)) {
            return null;
        }
        String id = path.substring(prefix.length(), path.length() - suffix.length());
        return CONSENT_ID.matcher(id).matches() ? id : null;
    }

    private static String firstValue(String csv) {

        if (csv == null || csv.isEmpty()) {
            return null;
        }
        int comma = csv.indexOf(',');
        return comma < 0 ? csv : csv.substring(0, comma);
    }

    private static int intParam(HttpServletRequest request, String name, int defaultValue) {

        String raw = request.getParameter(name);
        if (raw == null || raw.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static int positiveIntParam(HttpServletRequest request, String name, int defaultValue) {

        int value = intParam(request, name, defaultValue);
        return value < 1 ? defaultValue : Math.min(value, MAX_FETCH);
    }
}
