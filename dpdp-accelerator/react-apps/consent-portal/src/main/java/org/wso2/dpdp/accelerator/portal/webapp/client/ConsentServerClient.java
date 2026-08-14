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
import java.util.UUID;

/**
 * Calls the standalone OpenFGC Consent Server that owns consent records for
 * the acting-as (nominee delegation) flow.
 *
 * Unlike {@link IdentityServerClient}, no bearer token is forwarded: the
 * Consent Server trusts the {@code org-id} and, on writes, the trusted
 * {@code group-id} header the BFF sets itself from the verified mask token --
 * never anything the browser sent directly. This mirrors the Go BFF's
 * proxy.Service, which strips Authorization/Cookie before forwarding.
 */
public class ConsentServerClient {

    private final PortalConfig config;

    public ConsentServerClient(PortalConfig config) {

        this.config = config;
    }

    /** A raw upstream response; status and body are relayed after translation. */
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

    public Result get(String pathAndQuery, String orgId) throws IOException, InterruptedException {

        return send(request(pathAndQuery, orgId, null).GET().build());
    }

    public Result post(String path, String jsonBody, String orgId, String groupId)
            throws IOException, InterruptedException {

        HttpRequest.Builder builder = request(path, orgId, groupId)
                .header("Content-Type", PortalConstants.CONTENT_TYPE_JSON);
        builder.POST(jsonBody == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(jsonBody));
        return send(builder.build());
    }

    public Result put(String path, String jsonBody, String orgId, String groupId)
            throws IOException, InterruptedException {

        HttpRequest.Builder builder = request(path, orgId, groupId)
                .header("Content-Type", PortalConstants.CONTENT_TYPE_JSON)
                .method("PUT", HttpRequest.BodyPublishers.ofString(jsonBody));
        return send(builder.build());
    }

    private HttpRequest.Builder request(String pathAndQuery, String orgId, String groupId) {

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(config.getConsentServerUrl() + pathAndQuery))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", PortalConstants.CONTENT_TYPE_JSON)
                .header("X-Correlation-ID", UUID.randomUUID().toString());
        if (orgId != null && !orgId.isEmpty()) {
            builder.header("org-id", orgId);
        }
        if (groupId != null && !groupId.isEmpty()) {
            builder.header("group-id", groupId);
        }
        return builder;
    }

    private Result send(HttpRequest request) throws IOException, InterruptedException {

        HttpResponse<String> response = OAuthService.getInstance().httpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
        return new Result(response.statusCode(), response.body());
    }
}
