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
import org.wso2.dpdp.accelerator.portal.webapp.exception.TokenValidationException;
import org.wso2.dpdp.accelerator.portal.webapp.model.AuthenticatedUser;
import org.wso2.dpdp.accelerator.portal.webapp.service.ScopeMapper;
import org.wso2.dpdp.accelerator.portal.webapp.service.TokenValidator;
import org.wso2.dpdp.accelerator.portal.webapp.util.AuthUtil;
import org.wso2.dpdp.accelerator.portal.webapp.util.HttpUtil;
import org.wso2.dpdp.accelerator.portal.webapp.util.PortalConfig;
import org.wso2.dpdp.accelerator.portal.webapp.util.PortalConstants;

import java.io.IOException;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Returns the authenticated principal in the shape the SPA validates:
 * {@code {userId, organizationId, scopes[]}} with {@code portal:*} scopes.
 */
@WebServlet(urlPatterns = "/me")
public class MeServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Log LOG = LogFactory.getLog(MeServlet.class);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

        PortalConfig config = PortalConfig.getInstance(getServletContext());
        String accessToken = AuthUtil.resolveAccessToken(request);
        if (accessToken == null) {
            HttpUtil.sendError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    PortalConstants.ERROR_UNAUTHORIZED, "Authentication is required.");
            return;
        }

        AuthenticatedUser user;
        try {
            user = TokenValidator.getInstance(config).validate(accessToken);
        } catch (TokenValidationException e) {
            LOG.debug("Access token validation failed.", e);
            HttpUtil.sendError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    PortalConstants.ERROR_UNAUTHORIZED, "Access token is invalid or expired.");
            return;
        }

        ObjectNode body = HttpUtil.mapper().createObjectNode();
        body.put("userId", user.getUserId());
        body.put("organizationId", user.getOrganizationId() == null ? "carbon.super" : user.getOrganizationId());
        ArrayNode scopes = body.putArray("scopes");
        ScopeMapper.toPortalScopes(user.getScopes()).forEach(scopes::add);
        HttpUtil.sendJson(response, HttpServletResponse.SC_OK, body);
    }
}
