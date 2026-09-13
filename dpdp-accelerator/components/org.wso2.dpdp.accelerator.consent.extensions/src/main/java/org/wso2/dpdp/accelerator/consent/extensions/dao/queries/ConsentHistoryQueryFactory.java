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

package org.wso2.dpdp.accelerator.consent.extensions.dao.queries;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory resolving DB-dialect specific query providers for the consent history feature. Mirrors
 * {@code ComplaintQueryFactory}/{@code EventNotificationQueryFactory} - only {@code mysql} has a
 * dedicated provider today since the feature only ships {@code h2.sql}/{@code mysql.sql}; every
 * other dialect (including H2) falls back to the ANSI baseline.
 */
public class ConsentHistoryQueryFactory {

    private static final Map<String, ConsentHistoryCommonDBQueries> PROVIDER_MAP = new ConcurrentHashMap<>();

    private ConsentHistoryQueryFactory() {
    }

    public static ConsentHistoryCommonDBQueries getQueryProvider(String dbType) {
        String key = (dbType != null && !dbType.trim().isEmpty())
                ? dbType.trim().toLowerCase(Locale.ROOT) : "default";
        return PROVIDER_MAP.computeIfAbsent(key, k -> {
            if (k.contains("mysql")) {
                return new ConsentHistoryMysqlDBQueries();
            }
            return new ConsentHistoryCommonDBQueries();
        });
    }

    public static ConsentHistoryCommonDBQueries getQueryProvider(Connection conn) {
        if (conn != null) {
            try {
                DatabaseMetaData metaData = conn.getMetaData();
                if (metaData != null && metaData.getDatabaseProductName() != null) {
                    return getQueryProvider(metaData.getDatabaseProductName());
                }
            } catch (Exception ignored) {
                // Fallback to default
            }
        }
        return getQueryProvider();
    }

    public static ConsentHistoryCommonDBQueries getQueryProvider() {
        return getQueryProvider("default");
    }
}
