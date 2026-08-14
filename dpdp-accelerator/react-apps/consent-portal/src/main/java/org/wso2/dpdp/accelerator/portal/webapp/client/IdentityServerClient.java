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

import org.wso2.dpdp.accelerator.portal.webapp.service.OAuthService;
import org.wso2.dpdp.accelerator.portal.webapp.util.PortalConfig;
import org.wso2.dpdp.accelerator.portal.webapp.util.PortalConstants;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Calls the Identity Server consent APIs on behalf of the signed-in user by
 * forwarding that user's access token. The portal never uses admin
 * credentials, so the Identity Server enforces the user's own scopes.
 */
public class IdentityServerClient {

    /** Base path of the end-user (self-service) consent API. */
    public static final String USER_CONSENT_API = "/api/users/v1/me/consents";
    /** Base path of the consent management v2 (administrative) API. */
    public static final String CONSENT_MGT_V2_API = "/api/identity/consent-mgt/v2.0";

    private final PortalConfig config;
    private final String accessToken;

    public IdentityServerClient(PortalConfig config, String accessToken) {

        this.config = config;
        this.accessToken = accessToken;
    }

    /**
     * A raw upstream response; status and body are relayed to the SPA after translation.
     */
    public static class Result {

        private final int status;
        private final String body;

        Result(int status, String body) {

            this.status = status;
            this.body = body;
        }

        public int getStatus() {

            return status;
        }

        public String getBody() {

            return body;
        }

        public boolean isSuccess() {

            return status >= 200 && status < 300;
        }
    }

    private HttpRequest.Builder request(String path) {

        return HttpRequest.newBuilder()
                .uri(URI.create(config.getIdentityServerInternalBaseUrl() + path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", PortalConstants.CONTENT_TYPE_JSON);
    }

    public Result get(String path) throws IOException, InterruptedException {

        return send(request(path).GET().build());
    }

    public CompletableFuture<Result> getAsync(String path) {

        return OAuthService.getInstance().httpClient()
                .sendAsync(request(path).GET().build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> new Result(response.statusCode(), response.body()));
    }

    public Result post(String path, String jsonBody) throws IOException, InterruptedException {

        HttpRequest.Builder builder = request(path);
        if (jsonBody == null) {
            builder.POST(HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", PortalConstants.CONTENT_TYPE_JSON)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        }
        return send(builder.build());
    }

    public Result patch(String path, String jsonBody) throws IOException, InterruptedException {

        return send(request(path)
                .header("Content-Type", PortalConstants.CONTENT_TYPE_JSON)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody))
                .build());
    }

    public Result put(String path, String jsonBody) throws IOException, InterruptedException {

        return send(request(path)
                .header("Content-Type", PortalConstants.CONTENT_TYPE_JSON)
                .method("PUT", HttpRequest.BodyPublishers.ofString(jsonBody))
                .build());
    }

    public Result delete(String path) throws IOException, InterruptedException {

        return send(request(path).DELETE().build());
    }

    private Result send(HttpRequest request) throws IOException, InterruptedException {

        HttpResponse<String> response = OAuthService.getInstance().httpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
        return new Result(response.statusCode(), response.body());
    }
}
