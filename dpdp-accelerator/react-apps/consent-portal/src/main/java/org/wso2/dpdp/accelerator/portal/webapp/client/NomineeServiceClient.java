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

package org.wso2.dpdp.accelerator.portal.webapp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.dpdp.accelerator.portal.webapp.service.OAuthService;
import org.wso2.dpdp.accelerator.portal.webapp.util.HttpUtil;
import org.wso2.dpdp.accelerator.portal.webapp.util.LogUtil;
import org.wso2.dpdp.accelerator.portal.webapp.util.PortalConfig;
import org.wso2.dpdp.accelerator.portal.webapp.util.PortalConstants;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Calls Nominee Service's internal nomination gate: the live, re-checked-on-
 * every-request decision of whether an acting session may proceed, plus the
 * append-only audit trail of every acting attempt. This is what makes an
 * owner deactivating a nominee take effect on the nominee's very next request
 * rather than waiting for the mask token to expire.
 */
public class NomineeServiceClient {

    private static final Log LOG = LogFactory.getLog(NomineeServiceClient.class);

    public static final String EVENT_SESSION_STARTED = "SESSION_STARTED";
    public static final String EVENT_SESSION_DENIED = "SESSION_DENIED";
    public static final String EVENT_ACTION_PERFORMED = "ACTION_PERFORMED";
    public static final String EVENT_ACTION_DENIED = "ACTION_DENIED";

    private final PortalConfig config;

    public NomineeServiceClient(PortalConfig config) {

        this.config = config;
    }

    /** The gate's live decision for one owner/nominee pair. */
    public static final class GateDecision {

        private final boolean active;
        private final List<String> permissions;

        GateDecision(boolean active, List<String> permissions) {

            this.active = active;
            this.permissions = permissions;
        }

        public boolean isActive() {

            return active;
        }

        public boolean hasPermission(String permission) {

            return permissions.contains(permission);
        }
    }

    public GateDecision permissions(String owner, String nominee) throws IOException, InterruptedException {

        String baseUrl = config.getNomineeServiceUrl();
        String apiKey = config.getNomineeGateApiKey();
        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            throw new IOException("Nomination gate is not configured (nominee.service.url/nominee.gate.api.key).");
        }

        String query = "?owner=" + URLEncoder.encode(owner, StandardCharsets.UTF_8)
                + "&nominee=" + URLEncoder.encode(nominee, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/internal/nominations/permissions" + query))
                .timeout(Duration.ofSeconds(config.getNomineeGateTimeoutSeconds()))
                .header("X-Internal-Key", apiKey)
                .header("Accept", PortalConstants.CONTENT_TYPE_JSON)
                .GET().build();

        HttpResponse<String> response = OAuthService.getInstance().httpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Nomination gate returned HTTP " + response.statusCode());
        }
        JsonNode body = HttpUtil.mapper().readTree(response.body());
        boolean active = body.path("active").asBoolean(false);
        List<String> permissions = new ArrayList<>();
        for (JsonNode permission : body.path("permissions")) {
            permissions.add(permission.asText());
        }
        return new GateDecision(active, permissions);
    }

    /**
     * Appends one audit event. A failure is logged loudly but never thrown:
     * refusing an owner-authorised action because the audit service is briefly
     * unreachable would be the wrong trade, but a silent gap in the trail is
     * not acceptable either -- hence the loud log.
     */
    public void recordAction(String owner, String nominee, String permission, String resourceId, boolean allowed,
                              String reason) {

        String event = allowed ? EVENT_ACTION_PERFORMED : EVENT_ACTION_DENIED;
        StringBuilder detail = new StringBuilder("permission=").append(permission);
        if (resourceId != null && !resourceId.isEmpty()) {
            detail.append(" resource=").append(resourceId);
        }
        if (!allowed && reason != null && !reason.isEmpty()) {
            detail.append(" reason=").append(reason);
        }
        record(owner, nominee, event, detail.toString());
    }

    public void record(String owner, String nominee, String event, String detail) {

        String baseUrl = config.getNomineeServiceUrl();
        String apiKey = config.getNomineeGateApiKey();
        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            LOG.error("Audit not configured, acting event NOT recorded: " + LogUtil.safe(event) + " owner="
                    + LogUtil.safe(owner) + " nominee=" + LogUtil.safe(nominee));
            return;
        }

        try {
            ObjectNode body = HttpUtil.mapper().createObjectNode();
            body.put("ownerId", owner);
            body.put("nomineeId", nominee);
            body.put("event", event);
            if (detail != null && !detail.isEmpty()) {
                body.put("detail", detail);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/internal/nominations/audit"))
                    .timeout(Duration.ofSeconds(config.getNomineeGateTimeoutSeconds()))
                    .header("X-Internal-Key", apiKey)
                    .header("Content-Type", PortalConstants.CONTENT_TYPE_JSON)
                    .POST(HttpRequest.BodyPublishers.ofString(HttpUtil.mapper().writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = OAuthService.getInstance().httpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() > 299) {
                LOG.error("Acting event NOT recorded: " + LogUtil.safe(event) + " owner=" + LogUtil.safe(owner)
                        + " nominee=" + LogUtil.safe(nominee) + " status=" + response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.error("Acting event NOT recorded: " + LogUtil.safe(event) + " owner=" + LogUtil.safe(owner)
                    + " nominee=" + LogUtil.safe(nominee), e);
        }
    }
}
