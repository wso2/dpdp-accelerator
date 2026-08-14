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
import org.wso2.dpdp.accelerator.portal.webapp.model.UserSummary;
import org.wso2.dpdp.accelerator.portal.webapp.service.OAuthService;
import org.wso2.dpdp.accelerator.portal.webapp.util.HttpUtil;
import org.wso2.dpdp.accelerator.portal.webapp.util.PortalConfig;
import org.wso2.dpdp.accelerator.portal.webapp.util.PortalConstants;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SCIM2 directory client for the Identity Server user store, authenticated
 * with a client-credentials grant (as opposed to {@link IdentityServerClient},
 * which forwards the caller's own token). Used to resolve nominee/owner
 * identities and to check admin role membership.
 *
 * The client-credentials token is cached process-wide: it identifies this
 * application, not any particular caller, so every request may share it.
 */
public final class ScimClient {

    private static final String FILTER_EQUALS = "eq";
    private static final String FILTER_CONTAINS = "co";
    private static final Duration TOKEN_LEEWAY = Duration.ofSeconds(60);

    private static volatile String cachedToken;
    private static volatile Instant tokenExpiry = Instant.EPOCH;
    private static final Object TOKEN_LOCK = new Object();

    private final PortalConfig config;

    public ScimClient(PortalConfig config) {

        this.config = config;
    }

    public UserSummary findByEmail(String email) throws IOException, InterruptedException {

        List<UserSummary> matches = findByAttribute("email", FILTER_EQUALS, email);
        for (UserSummary match : matches) {
            if (match.getEmail() != null
                    && match.getEmail().toLowerCase(java.util.Locale.ROOT).equals(email.toLowerCase(
                            java.util.Locale.ROOT))) {
                return match;
            }
        }
        return null;
    }

    public UserSummary findById(String id) throws IOException, InterruptedException {

        HttpResponse<String> response = send(request("/scim2/Users/" + URLEncoder.encode(id, StandardCharsets.UTF_8))
                .GET().build());
        if (response.statusCode() == 404) {
            return null;
        }
        if (response.statusCode() != 200) {
            throw new IOException("SCIM user lookup failed with status " + response.statusCode());
        }
        return toUserSummary(HttpUtil.mapper().readTree(response.body()));
    }

    public List<UserSummary> search(String query) throws IOException, InterruptedException {

        Set<String> seenIds = new LinkedHashSet<>();
        List<UserSummary> results = new ArrayList<>();
        for (String attribute : new String[]{"email", "username"}) {
            for (UserSummary user : findByAttribute(attribute, FILTER_CONTAINS, query)) {
                if (seenIds.add(user.getId())) {
                    results.add(user);
                }
            }
        }
        return results;
    }

    /**
     * Reports whether userId is a member of the named role, either directly
     * or through membership in a group the role is assigned to.
     */
    public boolean isUserInRole(String roleName, String userId) throws IOException, InterruptedException {

        String roleId = findRoleIdByName(roleName);
        if (roleId == null) {
            return false;
        }

        HttpResponse<String> response = send(request("/scim2/v2/Roles/" + URLEncoder.encode(roleId,
                StandardCharsets.UTF_8)).GET().build());
        if (response.statusCode() != 200) {
            throw new IOException("SCIM role lookup failed with status " + response.statusCode());
        }
        JsonNode role = HttpUtil.mapper().readTree(response.body());
        for (JsonNode user : role.path("users")) {
            if (userId.equals(user.path("value").asText())) {
                return true;
            }
        }
        for (JsonNode group : role.path("groups")) {
            if (groupHasMember(group.path("value").asText(), userId)) {
                return true;
            }
        }
        return false;
    }

    private boolean groupHasMember(String groupId, String userId) throws IOException, InterruptedException {

        HttpResponse<String> response = send(request("/scim2/Groups/" + URLEncoder.encode(groupId,
                StandardCharsets.UTF_8)).GET().build());
        if (response.statusCode() != 200) {
            throw new IOException("SCIM group lookup failed with status " + response.statusCode());
        }
        JsonNode group = HttpUtil.mapper().readTree(response.body());
        for (JsonNode member : group.path("members")) {
            if (userId.equals(member.path("value").asText())) {
                return true;
            }
        }
        return false;
    }

    private String findRoleIdByName(String roleName) throws IOException, InterruptedException {

        String filter = "displayName eq \"" + roleName.replace("\"", "\\\"") + "\"";
        String query = "?filter=" + URLEncoder.encode(filter, StandardCharsets.UTF_8);
        HttpResponse<String> response = send(request("/scim2/v2/Roles" + query).GET().build());
        if (response.statusCode() != 200) {
            throw new IOException("SCIM role search failed with status " + response.statusCode());
        }
        JsonNode resources = HttpUtil.mapper().readTree(response.body()).path("Resources");
        if (!resources.isArray() || resources.isEmpty()) {
            return null;
        }
        return resources.get(0).path("id").asText(null);
    }

    private List<UserSummary> findByAttribute(String attribute, String operator, String value)
            throws IOException, InterruptedException {

        String path = filterPathFor(attribute);
        String escaped = value.replace("\"", "\\\"");
        String filter = path + " " + operator + " \"" + escaped + "\"";
        String query = "?filter=" + URLEncoder.encode(filter, StandardCharsets.UTF_8);

        HttpResponse<String> response = send(request("/scim2/Users" + query).GET().build());
        if (response.statusCode() != 200) {
            throw new IOException("SCIM user search failed with status " + response.statusCode());
        }
        JsonNode resources = HttpUtil.mapper().readTree(response.body()).path("Resources");
        List<UserSummary> users = new ArrayList<>();
        for (JsonNode doc : resources) {
            users.add(toUserSummary(doc));
        }
        return users;
    }

    private String filterPathFor(String attribute) {

        if ("email".equals(attribute)) {
            return "emails";
        }
        if ("username".equals(attribute)) {
            return "userName";
        }
        return config.getScimCustomSchemaUrn() + ":" + attribute;
    }

    private UserSummary toUserSummary(JsonNode doc) {

        String id = doc.path("id").asText(null);
        String username = doc.path("userName").asText(null);
        String email = null;
        for (JsonNode entry : doc.path("emails")) {
            if (entry.isTextual()) {
                email = entry.asText();
                break;
            }
            String value = entry.path("value").asText(null);
            if (value != null && !value.isEmpty()) {
                email = value;
                break;
            }
        }
        JsonNode name = doc.path("name");
        String given = name.path("givenName").asText(null);
        String family = name.path("familyName").asText(null);
        String displayName = UserSummary.displayName(given, family, username);
        return new UserSummary(id, displayName, email);
    }

    private HttpRequest.Builder request(String path) throws IOException, InterruptedException {

        String token = accessToken();
        return HttpRequest.newBuilder()
                .uri(URI.create(config.getIdentityServerInternalBaseUrl() + path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token)
                .header("Accept", PortalConstants.CONTENT_TYPE_JSON);
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {

        return OAuthService.getInstance().httpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String accessToken() throws IOException, InterruptedException {

        String token = cachedToken;
        if (token != null && Instant.now().isBefore(tokenExpiry)) {
            return token;
        }
        synchronized (TOKEN_LOCK) {
            if (cachedToken != null && Instant.now().isBefore(tokenExpiry)) {
                return cachedToken;
            }
            fetchToken();
            return cachedToken;
        }
    }

    private void fetchToken() throws IOException, InterruptedException {

        Map<String, String> params = new LinkedHashMap<>();
        params.put("grant_type", "client_credentials");
        params.put("scope", config.getScimDirectoryScopes());

        String credentials = config.getScimClientId() + ":" + config.getScimClientSecret();
        String basicAuth = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getIdentityServerInternalBaseUrl() + "/oauth2/token"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Basic " + basicAuth)
                .header("Content-Type", PortalConstants.CONTENT_TYPE_FORM)
                .POST(HttpRequest.BodyPublishers.ofString(OAuthService.encodeForm(params)))
                .build();

        HttpResponse<String> response = OAuthService.getInstance().httpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("SCIM client-credentials token request failed with status "
                    + response.statusCode());
        }
        JsonNode body = HttpUtil.mapper().readTree(response.body());
        String accessToken = body.path("access_token").asText(null);
        if (accessToken == null || accessToken.isEmpty()) {
            throw new IOException("SCIM client-credentials token response missing access_token");
        }
        int expiresIn = body.path("expires_in").asInt(3600);
        cachedToken = accessToken;
        tokenExpiry = Instant.now().plusSeconds(expiresIn).minus(TOKEN_LEEWAY);
    }
}
