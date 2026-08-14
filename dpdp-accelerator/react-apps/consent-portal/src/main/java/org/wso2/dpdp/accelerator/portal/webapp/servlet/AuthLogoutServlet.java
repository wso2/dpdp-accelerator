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

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.wso2.dpdp.accelerator.portal.webapp.service.OAuthService;
import org.wso2.dpdp.accelerator.portal.webapp.util.AuthUtil;
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
 * Clears the session cookies and returns the OIDC logout URL for the SPA to
 * navigate to, completing single logout at the Identity Server.
 */
@WebServlet(urlPatterns = "/auth/logout")
public class AuthLogoutServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {

        PortalConfig config = PortalConfig.getInstance(getServletContext());
        String idToken = AuthUtil.resolveIdToken(request);
        String postLogoutRedirectUri =
                config.getIdentityServerBaseUrl() + config.getPortalBasePath() + "/auth/callback";

        CookieUtil.clearAllAuthCookies(response, config.getPortalBasePath(), config.isCookieSecure());
        // The acting-as mask token is bound to whoever was signed in when it was
        // minted, not to this login session, so it must be cleared here too --
        // otherwise it would silently survive into a different person's session
        // on the same browser.
        CookieUtil.clearCookie(response, PortalConstants.ACTING_TOKEN_COOKIE, config.getPortalBasePath(),
                config.isCookieSecure());
        CookieUtil.clearCookie(response, PortalConstants.ACTING_STATE_COOKIE, config.getPortalBasePath(),
                config.isCookieSecure());

        ObjectNode body = HttpUtil.mapper().createObjectNode();
        body.put("logoutUrl", OAuthService.getInstance()
                .buildLogoutUrl(config, idToken, postLogoutRedirectUri));
        HttpUtil.sendJson(response, HttpServletResponse.SC_OK, body);
    }
}
