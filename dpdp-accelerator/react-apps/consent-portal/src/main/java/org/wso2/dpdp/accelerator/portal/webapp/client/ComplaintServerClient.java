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
import org.wso2.dpdp.accelerator.portal.webapp.util.MultipartBodyPublisher;
import org.wso2.dpdp.accelerator.portal.webapp.util.PortalConfig;
import org.wso2.dpdp.accelerator.portal.webapp.util.PortalConstants;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Calls the standalone complaint-server API on behalf of the signed-in user by forwarding that
 * user's access token, the same way {@link IdentityServerClient} does — the complaint API's own
 * {@code resource.access_control} rules enforce the token's portal_complaint_* scopes.
 */
public class ComplaintServerClient {

    private final PortalConfig config;
    private final String orgId;
    private final String accessToken;

    public ComplaintServerClient(PortalConfig config, String orgId, String accessToken) {

        this.config = config;
        this.orgId = orgId;
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
                .uri(URI.create(config.getComplaintServerInternalBaseUrl() + path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + accessToken)
                .header("org-id", orgId)
                .header("Accept", PortalConstants.CONTENT_TYPE_JSON);
    }

    public Result get(String path) throws IOException, InterruptedException {

        return send(request(path).GET().build());
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

    public Result postMultipart(String path, List<MultipartBodyPublisher.Part> parts)
            throws IOException, InterruptedException {

        MultipartBodyPublisher.Encoded encoded = MultipartBodyPublisher.encode(parts);
        HttpRequest request = request(path)
                .header("Content-Type", encoded.contentType())
                .POST(encoded.publisher())
                .build();
        return send(request);
    }

    private Result send(HttpRequest request) throws IOException, InterruptedException {

        HttpResponse<String> response = OAuthService.getInstance().httpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
        return new Result(response.statusCode(), response.body());
    }
}
