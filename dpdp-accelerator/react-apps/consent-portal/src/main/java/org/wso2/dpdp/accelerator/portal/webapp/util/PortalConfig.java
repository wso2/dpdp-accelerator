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

package org.wso2.dpdp.accelerator.portal.webapp.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import javax.servlet.ServletContext;

/**
 * Portal configuration resolved from an optional override file
 * ({@code <IS_HOME>/repository/conf/dpdp-portal.properties}) with webapp
 * context-param defaults as the fallback.
 */
public final class PortalConfig {

    private static final Log LOG = LogFactory.getLog(PortalConfig.class);
    private static final String OVERRIDE_FILE = "dpdp-portal.properties";
    private static volatile PortalConfig instance;

    private final Properties overrides = new Properties();
    private final ServletContext servletContext;

    public static final String IDENTITY_SERVER_BASE_URL = "identity.server.base.url";
    public static final String IDENTITY_SERVER_INTERNAL_BASE_URL = "identity.server.internal.base.url";
    public static final String OAUTH_CLIENT_ID = "oauth.client.id";
    public static final String OAUTH_CLIENT_SECRET = "oauth.client.secret";
    public static final String OAUTH_SCOPES = "oauth.scopes";
    public static final String COOKIE_SECURE = "cookie.secure";

    private PortalConfig(ServletContext servletContext) {

        this.servletContext = servletContext;
        String carbonHome = System.getProperty("carbon.home");
        if (carbonHome != null) {
            Path overridePath = Paths.get(carbonHome, "repository", "conf", OVERRIDE_FILE);
            if (Files.isReadable(overridePath)) {
                try (InputStream in = Files.newInputStream(overridePath)) {
                    overrides.load(in);
                    LOG.info("Loaded portal configuration overrides from " + overridePath);
                } catch (IOException e) {
                    LOG.warn("Failed to load portal configuration overrides from " + overridePath, e);
                }
            }
        }
    }

    public static PortalConfig getInstance(ServletContext servletContext) {

        if (instance == null) {
            synchronized (PortalConfig.class) {
                if (instance == null) {
                    instance = new PortalConfig(servletContext);
                }
            }
        }
        return instance;
    }

    public String get(String key) {

        String value = overrides.getProperty(key);
        if (value == null || value.isEmpty()) {
            value = servletContext.getInitParameter(key);
        }
        return value;
    }

    public String get(String key, String defaultValue) {

        String value = get(key);
        return (value == null || value.isEmpty()) ? defaultValue : value;
    }

    public String getIdentityServerBaseUrl() {

        return trimTrailingSlash(get(IDENTITY_SERVER_BASE_URL, "https://localhost:9443"));
    }

    public String getIdentityServerInternalBaseUrl() {

        return trimTrailingSlash(get(IDENTITY_SERVER_INTERNAL_BASE_URL, getIdentityServerBaseUrl()));
    }

    public String getClientId() {

        return get(OAUTH_CLIENT_ID);
    }

    public String getClientSecret() {

        return get(OAUTH_CLIENT_SECRET);
    }

    public String getScopes() {

        return get(OAUTH_SCOPES, "openid internal_login");
    }

    public boolean isCookieSecure() {

        return Boolean.parseBoolean(get(COOKIE_SECURE, "true"));
    }

    public String getPortalBasePath() {

        return servletContext.getContextPath();
    }

    private static String trimTrailingSlash(String value) {

        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
