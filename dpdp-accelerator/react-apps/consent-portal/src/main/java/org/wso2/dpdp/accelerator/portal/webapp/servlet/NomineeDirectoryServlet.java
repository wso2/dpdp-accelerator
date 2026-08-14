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

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.dpdp.accelerator.portal.webapp.client.ScimClient;
import org.wso2.dpdp.accelerator.portal.webapp.exception.TokenValidationException;
import org.wso2.dpdp.accelerator.portal.webapp.model.AuthenticatedUser;
import org.wso2.dpdp.accelerator.portal.webapp.model.UserSummary;
import org.wso2.dpdp.accelerator.portal.webapp.service.TokenValidator;
import org.wso2.dpdp.accelerator.portal.webapp.util.AuthUtil;
import org.wso2.dpdp.accelerator.portal.webapp.util.HttpUtil;
import org.wso2.dpdp.accelerator.portal.webapp.util.PortalConfig;
import org.wso2.dpdp.accelerator.portal.webapp.util.PortalConstants;

import java.io.IOException;
import java.util.List;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Directory lookups backing the nominee feature: resolving a candidate's
 * email or an already-known id to a display identity, and an admin-only
 * search used when activating nominations. Backed by Identity Server's SCIM2
 * API via {@link ScimClient}, never by anything the browser asserts about
 * itself.
 */
@WebServlet(urlPatterns = {"/nominees/lookup", "/users/*", "/admin/users/search"})
public class NomineeDirectoryServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Log LOG = LogFactory.getLog(NomineeDirectoryServlet.class);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

        PortalConfig config = PortalConfig.getInstance(getServletContext());
        AuthenticatedUser caller = authenticate(request, response, config);
        if (caller == null) {
            return;
        }

        ScimClient scimClient = new ScimClient(config);
        String servletPath = request.getServletPath();

        try {
            if ("/nominees/lookup".equals(servletPath)) {
                lookupByEmail(request, response, scimClient);
                return;
            }
            if ("/users".equals(servletPath)) {
                lookupById(request, response, scimClient);
                return;
            }
            if ("/admin/users/search".equals(servletPath)) {
                searchUsers(request, response, config, scimClient, caller);
                return;
            }
            HttpUtil.sendError(response, HttpServletResponse.SC_NOT_FOUND, PortalConstants.ERROR_NOT_FOUND,
                    "No such portal API route: " + servletPath);
        } catch (IOException e) {
            LOG.error("Nominee directory lookup failed.", e);
            HttpUtil.sendError(response, HttpServletResponse.SC_BAD_GATEWAY, PortalConstants.ERROR_UPSTREAM,
                    "The identity service is unavailable.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            HttpUtil.sendError(response, HttpServletResponse.SC_BAD_GATEWAY, PortalConstants.ERROR_UPSTREAM,
                    "The identity service is unavailable.");
        }
    }

    private void lookupByEmail(HttpServletRequest request, HttpServletResponse response, ScimClient scimClient)
            throws IOException, InterruptedException {

        String email = request.getParameter("email");
        if (email == null || email.isBlank()) {
            HttpUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, PortalConstants.ERROR_INVALID_PAYLOAD,
                    "email is required");
            return;
        }
        UserSummary user = scimClient.findByEmail(email.trim());
        if (user == null) {
            HttpUtil.sendError(response, HttpServletResponse.SC_NOT_FOUND, PortalConstants.ERROR_NOT_FOUND,
                    "no registered user with that email");
            return;
        }
        writeUser(response, user);
    }

    private void lookupById(HttpServletRequest request, HttpServletResponse response, ScimClient scimClient)
            throws IOException, InterruptedException {

        String pathInfo = request.getPathInfo();
        String id = pathInfo == null ? "" : pathInfo.replaceFirst("^/", "");
        if (id.isBlank()) {
            HttpUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, PortalConstants.ERROR_INVALID_PAYLOAD,
                    "id is required");
            return;
        }
        UserSummary user = scimClient.findById(id);
        if (user == null) {
            HttpUtil.sendError(response, HttpServletResponse.SC_NOT_FOUND, PortalConstants.ERROR_NOT_FOUND,
                    "user not found");
            return;
        }
        writeUser(response, user);
    }

    private void searchUsers(HttpServletRequest request, HttpServletResponse response, PortalConfig config,
                              ScimClient scimClient, AuthenticatedUser caller) throws IOException,
            InterruptedException {

        String query = request.getParameter("q");
        if (query == null || query.isBlank()) {
            HttpUtil.sendJson(response, HttpServletResponse.SC_OK, HttpUtil.mapper().createArrayNode());
            return;
        }
        if (!scimClient.isUserInRole(config.getAdminRoleName(), caller.getUserId())) {
            HttpUtil.sendError(response, HttpServletResponse.SC_FORBIDDEN, PortalConstants.ERROR_ADMIN_REQUIRED,
                    "this action requires an administrator");
            return;
        }
        List<UserSummary> results = scimClient.search(query.trim());
        ArrayNode body = HttpUtil.mapper().createArrayNode();
        for (UserSummary user : results) {
            body.add(toJson(user));
        }
        HttpUtil.sendJson(response, HttpServletResponse.SC_OK, body);
    }

    private void writeUser(HttpServletResponse response, UserSummary user) throws IOException {

        HttpUtil.sendJson(response, HttpServletResponse.SC_OK, toJson(user));
    }

    private ObjectNode toJson(UserSummary user) {

        ObjectNode node = HttpUtil.mapper().createObjectNode();
        node.put("id", user.getId());
        node.put("name", user.getName());
        node.put("email", user.getEmail());
        return node;
    }

    private AuthenticatedUser authenticate(HttpServletRequest request, HttpServletResponse response,
                                            PortalConfig config) throws IOException {

        String accessToken = AuthUtil.resolveAccessToken(request);
        if (accessToken == null) {
            HttpUtil.sendError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    PortalConstants.ERROR_UNAUTHORIZED, "Authentication is required.");
            return null;
        }
        try {
            return TokenValidator.getInstance(config).validate(accessToken);
        } catch (TokenValidationException e) {
            LOG.debug("Access token validation failed.", e);
            HttpUtil.sendError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    PortalConstants.ERROR_UNAUTHORIZED, "Access token is invalid or expired.");
            return null;
        }
    }
}
