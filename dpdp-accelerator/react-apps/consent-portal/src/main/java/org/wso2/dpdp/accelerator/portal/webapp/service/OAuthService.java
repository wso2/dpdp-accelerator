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

package org.wso2.dpdp.accelerator.portal.webapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.wso2.dpdp.accelerator.portal.webapp.exception.TokenRequestException;
import org.wso2.dpdp.accelerator.portal.webapp.util.HttpUtil;
import org.wso2.dpdp.accelerator.portal.webapp.util.PortalConfig;
import org.wso2.dpdp.accelerator.portal.webapp.util.PortalConstants;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * OAuth 2.0 client for the Identity Server token endpoint: authorization-code
 * (with PKCE) and refresh-token grants, plus authorize/logout URL builders.
 */
public final class OAuthService {

    private static final OAuthService INSTANCE = new OAuthService();
    private static final SecureRandom RANDOM = new SecureRandom();

    // The Identity Server JVM sets javax.net.ssl.trustStore to the carbon
    // client truststore, which the default SSL context honours.
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private OAuthService() {
    }

    public static OAuthService getInstance() {

        return INSTANCE;
    }

    public HttpClient httpClient() {

        return httpClient;
    }

    public static String generateRandomToken() {

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String codeChallengeS256(String codeVerifier) {

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public String buildAuthorizeUrl(PortalConfig config, String redirectUri, String state, String codeChallenge) {

        Map<String, String> params = new LinkedHashMap<>();
        params.put("response_type", "code");
        params.put("client_id", config.getClientId());
        params.put("redirect_uri", redirectUri);
        params.put("scope", config.getScopes());
        params.put("state", state);
        params.put("code_challenge", codeChallenge);
        params.put("code_challenge_method", "S256");
        return config.getIdentityServerBaseUrl() + "/oauth2/authorize?" + encodeForm(params);
    }

    public String buildLogoutUrl(PortalConfig config, String idToken, String postLogoutRedirectUri) {

        Map<String, String> params = new LinkedHashMap<>();
        if (idToken != null && !idToken.isEmpty()) {
            params.put("id_token_hint", idToken);
        }
        params.put("post_logout_redirect_uri", postLogoutRedirectUri);
        return config.getIdentityServerBaseUrl() + "/oidc/logout?" + encodeForm(params);
    }

    public JsonNode exchangeAuthorizationCode(PortalConfig config, String code, String redirectUri,
                                              String codeVerifier) throws TokenRequestException {

        Map<String, String> params = new LinkedHashMap<>();
        params.put("grant_type", "authorization_code");
        params.put("code", code);
        params.put("redirect_uri", redirectUri);
        params.put("code_verifier", codeVerifier);
        return sendTokenRequest(config, params);
    }

    public JsonNode refreshTokens(PortalConfig config, String refreshToken) throws TokenRequestException {

        Map<String, String> params = new LinkedHashMap<>();
        params.put("grant_type", "refresh_token");
        params.put("refresh_token", refreshToken);
        return sendTokenRequest(config, params);
    }

    private JsonNode sendTokenRequest(PortalConfig config, Map<String, String> params)
            throws TokenRequestException {

        String clientCredentials = config.getClientId() + ":" + config.getClientSecret();
        String basicAuth = Base64.getEncoder().encodeToString(clientCredentials.getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getIdentityServerInternalBaseUrl() + "/oauth2/token"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Basic " + basicAuth)
                .header("Content-Type", PortalConstants.CONTENT_TYPE_FORM)
                .POST(HttpRequest.BodyPublishers.ofString(encodeForm(params)))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new TokenRequestException("Token request failed with status " + response.statusCode());
            }
            return HttpUtil.mapper().readTree(response.body());
        } catch (IOException e) {
            throw new TokenRequestException("Token request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TokenRequestException("Token request interrupted", e);
        }
    }

    public static String encodeForm(Map<String, String> params) {

        return params.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }
}
