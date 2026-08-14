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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.dpdp.accelerator.portal.webapp.service.OAuthService;
import org.wso2.dpdp.accelerator.portal.webapp.util.HttpUtil;
import org.wso2.dpdp.accelerator.portal.webapp.util.PortalConfig;
import org.wso2.dpdp.accelerator.portal.webapp.util.PortalConstants;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Same-origin passthrough for Nominee Service (frontend/src/features/nominee
 * and the "acting-as" nominee flow's CRUD): the SPA calls this path directly
 * -- never through the acting/{@code /me} servlets above -- because Nominee
 * Service serves plain http and does not name the portal's origin in its own
 * CORS allowlist, so a cross-origin browser call would be blocked outright.
 *
 * The request is forwarded byte for byte, including the caller's split-token
 * {@code Authorization}/{@code Cookie} headers: Nominee Service validates that
 * pair itself, exactly as the rest of the portal does, so nothing here needs
 * to understand the token.
 */
@WebServlet(urlPatterns = "/nominee-service/*")
public class NomineeServiceProxyServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Log LOG = LogFactory.getLog(NomineeServiceProxyServlet.class);

    /**
     * Request headers that must not be forwarded verbatim to the upstream call.
     * "origin" and "referer" are dropped deliberately: this leg is a plain
     * server-to-server HTTP call, not a browser request, so forwarding the
     * browser's Origin only trips Nominee Service's own CORS filter (which
     * does not know, and should not need to know, that the real caller is
     * this same-origin passthrough rather than a cross-origin browser).
     */
    private static final Set<String> SKIP_REQUEST_HEADERS = Set.of("host", "content-length", "connection",
            "origin", "referer");
    /** Response headers the servlet container (or this class) sets itself. */
    private static final Set<String> SKIP_RESPONSE_HEADERS = Set.of("content-length", "connection",
            "transfer-encoding");

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {

        PortalConfig config = PortalConfig.getInstance(getServletContext());
        String baseUrl = config.getNomineeServiceUrl();
        if (baseUrl.isEmpty()) {
            HttpUtil.sendError(response, HttpServletResponse.SC_BAD_GATEWAY, PortalConstants.ERROR_UPSTREAM,
                    "Nominee Service is not configured.");
            return;
        }

        String pathInfo = request.getPathInfo() == null ? "" : request.getPathInfo();
        String query = request.getQueryString();
        String target = baseUrl + pathInfo + (query == null ? "" : "?" + query);

        byte[] body = readBody(request);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(target))
                .timeout(Duration.ofSeconds(30));
        copyRequestHeaders(request, builder);
        builder.method(request.getMethod(), body.length == 0
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(body));

        try {
            HttpResponse<byte[]> upstream = OAuthService.getInstance().httpClient()
                    .send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            response.setStatus(upstream.statusCode());
            upstream.headers().map().forEach((name, values) -> {
                if (!SKIP_RESPONSE_HEADERS.contains(name.toLowerCase(java.util.Locale.ROOT))) {
                    values.forEach(value -> response.addHeader(name, value));
                }
            });
            response.getOutputStream().write(upstream.body());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.error("Nominee Service proxy request failed.", e);
            HttpUtil.sendError(response, HttpServletResponse.SC_BAD_GATEWAY, PortalConstants.ERROR_UPSTREAM,
                    "Nominee Service is unavailable.");
        }
    }

    private static void copyRequestHeaders(HttpServletRequest request, HttpRequest.Builder builder) {

        Enumeration<String> names = request.getHeaderNames();
        if (names == null) {
            names = Collections.emptyEnumeration();
        }
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (SKIP_REQUEST_HEADERS.contains(name.toLowerCase(java.util.Locale.ROOT))) {
                continue;
            }
            Enumeration<String> values = request.getHeaders(name);
            while (values.hasMoreElements()) {
                try {
                    builder.header(name, values.nextElement());
                } catch (IllegalArgumentException e) {
                    // A small set of headers (e.g. restricted ones) cannot be set on
                    // HttpRequest; skip rather than fail the whole proxied call.
                    LOG.debug("Skipping header not permitted on outbound request: " + name);
                }
            }
        }
    }

    private static byte[] readBody(HttpServletRequest request) throws IOException {

        try (java.io.InputStream in = request.getInputStream()) {
            return in.readAllBytes();
        }
    }
}
