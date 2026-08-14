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
import org.wso2.dpdp.accelerator.portal.webapp.service.OAuthService;
import org.wso2.dpdp.accelerator.portal.webapp.service.ScopeMapper;
import org.wso2.dpdp.accelerator.portal.webapp.util.HttpUtil;
import org.wso2.dpdp.accelerator.portal.webapp.util.PortalConfig;
import org.wso2.dpdp.accelerator.portal.webapp.util.PortalConstants;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Performs the WSO2 Identity Server two-step impersonation token exchange
 * (documented "on-behalf-of" flow): a browser-driven authorize step that
 * mints a short-lived subject token naming the owner, followed by a
 * server-to-server RFC 8693 token exchange that turns it into a usable
 * "mask" access token carrying {@code sub=owner}, {@code act.sub=nominee}.
 */
public final class ImpersonationClient {

    /**
     * Requested at mint time as a ceiling, not a grant: Identity Server first
     * narrows this to what the owner may do, then the nomination gate narrows
     * it again to what the owner granted this specific nominee. Only
     * {@code :self} scopes belong here -- the mask token carries the owner as
     * its subject, so "self" resolves to the owner's data.
     */
    private static final List<String> DELEGATABLE_SCOPES = List.of(
            ScopeMapper.PORTAL_CONSENTS_READ_SELF,
            ScopeMapper.PORTAL_CONSENTS_WRITE_SELF,
            ScopeMapper.PORTAL_CONSENTS_APPROVE_SELF,
            ScopeMapper.PORTAL_PROFILE_READ_SELF,
            ScopeMapper.PORTAL_PROFILE_WRITE_SELF,
            ScopeMapper.PORTAL_PROFILE_DELETE_SELF);

    private final PortalConfig config;

    public ImpersonationClient(PortalConfig config) {

        this.config = config;
    }

    /** Result of a successful token exchange. */
    public static final class Token {

        private final String accessToken;
        private final Instant expiresAt;

        Token(String accessToken, Instant expiresAt) {

            this.accessToken = accessToken;
            this.expiresAt = expiresAt;
        }

        public String getAccessToken() {

            return accessToken;
        }

        public Instant getExpiresAt() {

            return expiresAt;
        }
    }

    /**
     * Builds the Identity Server authorize-endpoint URL step 1 of the flow
     * redirects the nominee's browser to. Must be a real top-level navigation:
     * Identity Server identifies the impersonator from its own session cookie,
     * and answers with a cross-origin redirect whose fragment only the browser
     * can read.
     */
    public String buildAuthorizeUrl(String ownerId, String state, String nonce) {

        String scope = "openid " + config.getImpersonationScope() + " " + String.join(" ", DELEGATABLE_SCOPES);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("response_type", "id_token subject_token");
        params.put("client_id", config.getClientId());
        params.put("redirect_uri", config.getImpersonationRedirectUri());
        params.put("requested_subject", ownerId);
        params.put("scope", scope);
        params.put("state", state);
        params.put("nonce", nonce);
        return config.getIdentityServerBaseUrl() + "/oauth2/authorize?" + OAuthService.encodeForm(params);
    }

    /**
     * Step 2: exchanges the subject token (read from the callback fragment)
     * for a usable mask access token. {@code actorToken} is the nominee's own
     * current access token and is mandatory -- Identity Server only treats the
     * request as impersonation when both the subject and actor pairs are
     * present, and compares the actor token's subject against the subject
     * token's {@code may_act} claim, proving the caller really is the nominee
     * named in it. No {@code scope} parameter is sent: Identity Server treats
     * the subject token's scope claim as a ceiling and narrows automatically.
     */
    public Token exchangeSubjectToken(String subjectToken, String actorToken) throws IOException,
            InterruptedException {

        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange");
        form.put("subject_token", subjectToken);
        form.put("subject_token_type", "urn:ietf:params:oauth:token-type:jwt");
        form.put("actor_token", actorToken);
        form.put("actor_token_type", "urn:ietf:params:oauth:token-type:jwt");
        form.put("requested_token_type", "urn:ietf:params:oauth:token-type:access_token");

        String credentials = config.getClientId() + ":" + config.getClientSecret();
        String basicAuth = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getIdentityServerInternalBaseUrl() + "/oauth2/token"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Basic " + basicAuth)
                .header("Content-Type", PortalConstants.CONTENT_TYPE_FORM)
                .POST(HttpRequest.BodyPublishers.ofString(OAuthService.encodeForm(form)))
                .build();

        HttpResponse<String> response = OAuthService.getInstance().httpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Impersonation token exchange failed with status " + response.statusCode()
                    + ": " + response.body());
        }
        JsonNode body = HttpUtil.mapper().readTree(response.body());
        String accessToken = body.path("access_token").asText(null);
        if (accessToken == null || accessToken.isEmpty()) {
            throw new IOException("Impersonation token exchange response missing access_token");
        }
        int expiresIn = body.path("expires_in").asInt(3600);
        return new Token(accessToken, Instant.now().plusSeconds(expiresIn));
    }
}
