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

package org.wso2.dpdp.common.config;

import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads configuration directly out of the Identity Server's deployment.toml, at
 * {@code ${carbon.home}/repository/conf/deployment.toml}. Exists because webapps such as the
 * complaint management API are plain Tomcat webapps rather than OSGi-managed Carbon components,
 * so they have no direct API access to Carbon's own parsed config beans - this reads the same
 * file straight off disk instead.
 *
 * <p>The file is read once and cached for the JVM's lifetime, matching how Carbon itself only
 * picks up deployment.toml changes on restart. Returns the caller's default when
 * {@code carbon.home} isn't set, the file can't be read, or the key isn't present - callers
 * (e.g. running outside this accelerator, in a plain unit test) always get a usable value.
 */
public final class ConfigProvider {

    private static final Logger LOGGER = Logger.getLogger(ConfigProvider.class.getName());
    private static volatile String cachedCarbonHome;
    private static volatile TomlParseResult toml;

    private ConfigProvider() {
    }

    /** Reads a dotted TOML key, e.g. {@code "datasource.ComplaintDB.url"}. */
    public static String getString(String dottedKey, String defaultValue) {
        TomlParseResult result = getToml();
        if (result == null) {
            return defaultValue;
        }
        String value = result.getString(dottedKey);
        return value != null ? value : defaultValue;
    }

    /**
     * Re-parses only when {@code carbon.home} has changed since the last call - in practice
     * that's a startup-time constant that never changes within a JVM's lifetime, so this is a
     * read-once cache for real deployments. Checking the property itself on every call (rather
     * than caching unconditionally forever) is what keeps this class independently testable.
     */
    private static TomlParseResult getToml() {
        String carbonHome = System.getProperty("carbon.home");
        if (carbonHome == null) {
            return null;
        }
        if (carbonHome.equals(cachedCarbonHome)) {
            return toml;
        }
        synchronized (ConfigProvider.class) {
            if (carbonHome.equals(cachedCarbonHome)) {
                return toml;
            }
            Path deploymentToml = Paths.get(carbonHome, "repository", "conf", "deployment.toml");
            TomlParseResult result = null;
            try {
                TomlParseResult parsed = Toml.parse(deploymentToml);
                if (parsed.hasErrors()) {
                    LOGGER.log(Level.WARNING, "Errors parsing " + deploymentToml + ": " + parsed.errors());
                } else {
                    result = parsed;
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Could not read " + deploymentToml, e);
            }
            toml = result;
            cachedCarbonHome = carbonHome;
            return toml;
        }
    }
}
