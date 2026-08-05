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
import org.wso2.dpdp.accelerator.portal.webapp.util.PortalConfig;
import org.wso2.dpdp.accelerator.portal.webapp.util.PortalConstants;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Completes the OIDC authorization-code flow: validates state, exchanges the
 * code (client secret + PKCE verifier), issues the split-token cookies and
 * redirects to the SPA. Arriving without a code is treated as the
 * post-logout redirect and clears the session cookies.
 */
@WebServlet(urlPatterns = "/auth/callback")
public class OAuthCallbackServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Log LOG = LogFactory.getLog(OAuthCallbackServlet.class);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

        PortalConfig config = PortalConfig.getInstance(getServletContext());
        String portalHome = config.getPortalBasePath() + "/";
        String code = request.getParameter("code");

        if (code == null || code.isEmpty()) {
            // Post-logout redirect (or an authorize error such as access_denied).
            CookieUtil.clearAllAuthCookies(response, config.getPortalBasePath(), config.isCookieSecure());
            response.sendRedirect(portalHome);
            return;
        }

        String transaction = CookieUtil.getCookieValue(request, PortalConstants.AUTH_TRANSACTION_COOKIE);
        String state = request.getParameter("state");
        String codeVerifier = null;
        if (transaction != null && state != null) {
            String decoded = new String(Base64.getUrlDecoder().decode(transaction), StandardCharsets.UTF_8);
            int separator = decoded.indexOf(':');
            if (separator > 0 && decoded.substring(0, separator).equals(state)) {
                codeVerifier = decoded.substring(separator + 1);
            }
        }
        CookieUtil.clearCookie(response, PortalConstants.AUTH_TRANSACTION_COOKIE,
                config.getPortalBasePath() + "/auth", config.isCookieSecure());
        if (codeVerifier == null) {
            LOG.warn("OAuth callback rejected: state mismatch or missing login transaction.");
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid login transaction.");
            return;
        }

        String redirectUri = config.getIdentityServerBaseUrl() + config.getPortalBasePath() + "/auth/callback";
        JsonNode tokens;
        try {
            tokens = OAuthService.getInstance()
                    .exchangeAuthorizationCode(config, code, redirectUri, codeVerifier);
        } catch (TokenRequestException e) {
            LOG.error("Authorization code exchange failed.", e);
            response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Token exchange failed.");
            return;
        }

        issueTokenCookies(response, config, tokens);
        response.sendRedirect(portalHome);
    }

    static void issueTokenCookies(HttpServletResponse response, PortalConfig config, JsonNode tokens) {

        String path = config.getPortalBasePath();
        boolean secure = config.isCookieSecure();
        int accessTokenMaxAge = tokens.path("expires_in").asInt(3600);

        CookieUtil.addSplitTokenCookies(response, PortalConstants.ACCESS_TOKEN_PART1_COOKIE,
                PortalConstants.ACCESS_TOKEN_PART2_COOKIE, tokens.path("access_token").asText(),
                path, accessTokenMaxAge, true, secure);
        if (tokens.hasNonNull("refresh_token")) {
            CookieUtil.addSplitTokenCookies(response, PortalConstants.REFRESH_TOKEN_PART1_COOKIE,
                    PortalConstants.REFRESH_TOKEN_PART2_COOKIE, tokens.path("refresh_token").asText(),
                    path, PortalConstants.REFRESH_COOKIE_MAX_AGE_SECONDS, true, secure);
        }
        if (tokens.hasNonNull("id_token")) {
            // Both ID token parts stay script-readable: the SPA decodes the
            // payload for the profile menu (frontend/src/utils/authClient.ts).
            CookieUtil.addSplitTokenCookies(response, PortalConstants.ID_TOKEN_PART1_COOKIE,
                    PortalConstants.ID_TOKEN_PART2_COOKIE, tokens.path("id_token").asText(),
                    path, PortalConstants.REFRESH_COOKIE_MAX_AGE_SECONDS, false, secure);
        }
    }
}
