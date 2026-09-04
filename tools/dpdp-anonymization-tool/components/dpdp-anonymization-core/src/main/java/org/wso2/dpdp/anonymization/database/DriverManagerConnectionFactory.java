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

package org.wso2.dpdp.anonymization.database;

import org.wso2.dpdp.anonymization.config.DatabaseConfig;
import org.wso2.dpdp.anonymization.validation.AnonymizationException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DriverManagerConnectionFactory implements ConnectionFactory {

    private final DatabaseConfig config;

    public DriverManagerConnectionFactory(DatabaseConfig config) throws AnonymizationException {
        this.config = config;
        try {
            Class.forName(config.getDriverClass());
        } catch (ClassNotFoundException e) {
            throw new AnonymizationException("JDBC driver is not available: " + config.getDriverClass(), e);
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(config.getUrl(), config.getUsername(), config.getPassword());
    }
}
