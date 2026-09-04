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

package org.wso2.dpdp.accelerator.common.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.dpdp.accelerator.common.persistence.JDBCPersistenceManager;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Thin static facade over {@link JDBCPersistenceManager}, mirroring the Financial Services
 * accelerator's own {@code DatabaseUtils}, so DAO/service code depends on this rather than the
 * persistence manager directly.
 */
public final class DatabaseUtils {

    private static final Log LOG = LogFactory.getLog(DatabaseUtils.class);

    private DatabaseUtils() {

    }

    public static Connection getDBConnection() {

        return JDBCPersistenceManager.getInstance().getDBConnection();
    }

    public static void commitTransaction(Connection connection) {

        JDBCPersistenceManager.getInstance().commitTransaction(connection);
    }

    public static void rollbackTransaction(Connection connection) {

        JDBCPersistenceManager.getInstance().rollbackTransaction(connection);
    }

    /**
     * Ends any still-open transaction before handing the connection back to the pool. Connections
     * come out of {@link #getDBConnection()} with autocommit off and read paths never commit, so
     * without this they return to the pool mid-transaction - which on MySQL (REPEATABLE READ) pins
     * a snapshot that every later borrower of that connection keeps reading, serving stale rows
     * indefinitely. The pool does not do this for us: Tomcat JDBC only terminates the transaction
     * on return when the datasource sets {@code defaultAutoCommit=false}, which the product's own
     * datasource config does not.
     */
    public static void closeConnection(Connection connection) {

        if (connection == null) {
            return;
        }
        try {
            // Roll back any uncommitted transaction before closing the connection.
            // Write operations are expected to have committed before this method is called.
            if (!connection.getAutoCommit()) {
                connection.rollback();
            }
        } catch (SQLException e) {
            LOG.error("Error while ending the transaction on a DPDP DB connection before close.", e);
        }
        try {
            connection.close();
        } catch (SQLException e) {
            LOG.error("Error while closing a DPDP DB connection.", e);
        }
    }
}
