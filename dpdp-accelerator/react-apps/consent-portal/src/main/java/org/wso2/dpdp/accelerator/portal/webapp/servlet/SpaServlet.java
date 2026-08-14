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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Serves the single page application.
 *
 * Mapping this at the context root is what stops the Identity Server and
 * Tomcat from disagreeing about the trailing slash: Tomcat redirects a bare
 * context path to add one, while the Carbon valve redirects it straight back,
 * which loops the browser until it gives up. Owning "/" means the container
 * never issues that redirect. The bundled webapps that work (console,
 * myaccount) declare a root servlet for the same reason.
 *
 * Real files are handed back to the container's default servlet; an
 * extensionless path is assumed to be a client-side route and receives
 * index.html with a 200, so deep links work without dressing an error status
 * up as a page. A missing path that looks like a static asset (it has a file
 * extension, e.g. a stale hashed bundle or a typoed icon request) gets a real
 * 404 instead, rather than silently serving the SPA shell for it.
 */
@WebServlet(urlPatterns = "/")
public class SpaServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final String INDEX = "/index.html";
    private static final String DEFAULT_SERVLET = "default";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String path = request.getServletPath();
        boolean staticFile = path != null && !path.isEmpty() && !"/".equals(path) && resourceExists(path);

        RequestDispatcher container = getServletContext().getNamedDispatcher(DEFAULT_SERVLET);
        if (staticFile && container != null) {
            container.forward(request, response);
            return;
        }

        if (looksLikeStaticAsset(path)) {
            // The path is not echoed into the response: SpotBugs (rightly) flags handing
            // request-controlled text to sendError's message as a reflected-XSS pattern.
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // index.html is streamed rather than dispatched to. A forward or include
        // would re-enter this servlet, since "/" is also the default mapping,
        // and while the webapp redeploys index.html briefly does not exist --
        // which turned that re-entry into unbounded recursion and a blown stack.
        try (InputStream index = getServletContext().getResourceAsStream(INDEX)) {
            if (index == null) {
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "The consent portal is not fully deployed yet.");
                return;
            }
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("text/html");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            // The shell must not be cached: it names the hashed asset bundles.
            response.setHeader("Cache-Control", "no-cache");
            OutputStream out = response.getOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = index.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    @Override
    protected void doHead(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        doGet(request, response);
    }

    private boolean resourceExists(String path) {

        if (path.endsWith("/")) {
            return false;
        }
        try {
            return getServletContext().getResource(path) != null;
        } catch (MalformedURLException e) {
            return false;
        }
    }

    /**
     * A client-side route never contains a dot in its last segment (routes
     * are plain ids/paths), so a dot there means the request was for a file
     * that turned out not to exist, not a route the SPA router should handle.
     */
    private static boolean looksLikeStaticAsset(String path) {

        if (path == null || path.isEmpty() || "/".equals(path)) {
            return false;
        }
        String lastSegment = path.substring(path.lastIndexOf('/') + 1);
        return lastSegment.contains(".");
    }
}
