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

package org.wso2.dpdp.anonymization.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.wso2.dpdp.anonymization.json.JsonPathExpression;
import org.wso2.dpdp.anonymization.validation.AnonymizationException;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ToolConfigLoader {

    private static final Pattern ENVIRONMENT_PLACEHOLDER = Pattern.compile("^\\$\\{([A-Za-z_][A-Za-z0-9_]*)}$");

    private ToolConfigLoader() {
    }

    public static ToolConfig load(File file) throws AnonymizationException {
        try {
            ToolConfig config = new ObjectMapper().readValue(file, ToolConfig.class);
            if (!blank(config.getEventPayloadRulesFile())) {
                File rulesFile = new File(config.getEventPayloadRulesFile());
                if (!rulesFile.isAbsolute()) {
                    rulesFile = new File(file.getAbsoluteFile().getParentFile(), config.getEventPayloadRulesFile());
                }
                EventPayloadRule[] rules = new ObjectMapper().readValue(rulesFile, EventPayloadRule[].class);
                config.setEventPayloadRules(Arrays.asList(rules));
            }
            validate(config);
            DatabaseConfig database = config.getDatabase();
            database.setUsername(resolveEnvironment(database.getUsername()));
            database.setPassword(resolveEnvironment(database.getPassword()));
            return config;
        } catch (IOException e) {
            throw new AnonymizationException("Could not read configuration: " + file.getAbsolutePath(), e);
        }
    }

    public static void validate(ToolConfig config) throws AnonymizationException {
        if (config == null || config.getDatabase() == null) {
            throw new AnonymizationException("Database configuration is required.");
        }
        DatabaseConfig database = config.getDatabase();
        if (blank(database.getUrl()) || blank(database.getDriverClass())) {
            throw new AnonymizationException("database.url and database.driverClass are required.");
        }
        if (config.getBatchSize() < 1 || config.getBatchSize() > 10000) {
            throw new AnonymizationException("batchSize must be between 1 and 10000.");
        }
        Set<String> rules = new HashSet<>();
        for (EventPayloadRule rule : config.getEventPayloadRules()) {
            if (rule == null || blank(rule.getTopic()) || rule.getPaths().isEmpty()) {
                throw new AnonymizationException("Every event payload rule requires a topic and at least one path.");
            }
            for (String path : rule.getPaths()) {
                JsonPathExpression.parse(path);
                if (!rules.add(rule.getTopic() + "\u0000" + path)) {
                    throw new AnonymizationException("Duplicate event payload rule: " + rule.getTopic() + " " + path);
                }
            }
        }
    }

    private static String resolveEnvironment(String value) throws AnonymizationException {
        if (value == null) {
            return null;
        }
        Matcher matcher = ENVIRONMENT_PLACEHOLDER.matcher(value);
        if (!matcher.matches()) {
            return value;
        }
        String resolved = System.getenv(matcher.group(1));
        if (resolved == null) {
            throw new AnonymizationException("Environment variable " + matcher.group(1) + " is not defined.");
        }
        return resolved;
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
