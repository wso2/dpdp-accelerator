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

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.dpdp.accelerator.portal.webapp.exception.TokenRequestException;
import org.wso2.dpdp.accelerator.portal.webapp.service.OAuthService;
import org.wso2.dpdp.accelerator.portal.webapp.util.CookieUtil;
import org.wso2.dpdp.accelerator.portal.webapp.util.HttpUtil;
import org.wso2.dpdp.accelerator.portal.webapp.util.PortalConfig;
import org.wso2.dpdp.accelerator.portal.webapp.util.PortalConstants;

import java.io.IOException;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Rotates the session using the refresh token: the SPA posts refresh-token
 * part 1 (form encoded) and the browser attaches the HttpOnly part 2 cookie.
 * Responds 204 with fresh cookies, or 401 to push the SPA into a new login.
 */
@WebServlet(urlPatterns = "/auth/refresh")
public class AuthRefreshServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Log LOG = LogFactory.getLog(AuthRefreshServlet.class);

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {

        PortalConfig config = PortalConfig.getInstance(getServletContext());
        String part1 = request.getParameter("refresh_token");
        String part2 = CookieUtil.getCookieValue(request, PortalConstants.REFRESH_TOKEN_PART2_COOKIE);
        if (part1 == null || part1.isEmpty() || part2 == null || part2.isEmpty()) {
            HttpUtil.sendError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    PortalConstants.ERROR_UNAUTHORIZED, "Refresh token is unavailable.");
            return;
        }

        JsonNode tokens;
        try {
            tokens = OAuthService.getInstance().refreshTokens(config, part1 + part2);
        } catch (TokenRequestException e) {
            LOG.warn("Refresh token grant failed.", e);
            CookieUtil.clearAllAuthCookies(response, config.getPortalBasePath(), config.isCookieSecure());
            HttpUtil.sendError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    PortalConstants.ERROR_UNAUTHORIZED, "Session refresh failed.");
            return;
        }

        OAuthCallbackServlet.issueTokenCookies(response, config, tokens);
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
