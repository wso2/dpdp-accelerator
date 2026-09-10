/**
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 * <p>
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 *     http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.dpdp.accelerator.common.persistence;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigParser;
import org.wso2.dpdp.accelerator.common.constant.DPDPCommonConstants;
import org.wso2.dpdp.accelerator.common.exception.DPDPCommonRuntimeException;

import javax.naming.InitialContext;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Resolves the shared DPDP datasource and hands out JDBC connections to accelerator modules.
 * Mirrors the Financial Services accelerator's own {@code JDBCPersistenceManager} - a lean
 * singleton with manual commit/rollback, no generic transactional-callback wrapping.
 */
public final class JDBCPersistenceManager {

    private static final Log LOG = LogFactory.getLog(JDBCPersistenceManager.class);
    private static volatile JDBCPersistenceManager instance;
    private static volatile DataSource dataSource;

    private JDBCPersistenceManager() {

        initDataSource();
    }

    public static JDBCPersistenceManager getInstance() {

        if (instance == null) {
            synchronized (JDBCPersistenceManager.class) {
                if (instance == null) {
                    instance = new JDBCPersistenceManager();
                }
            }
        }
        return instance;
    }

    private void initDataSource() {

        if (dataSource != null) {
            return;
        }
        synchronized (JDBCPersistenceManager.class) {
            if (dataSource != null) {
                return;
            }
            String dataSourceName = null;
            try {
                dataSourceName = DPDPConfigParser.getInstance().getJdbcDataSourceName();
                InitialContext context = new InitialContext();
                try {
                    dataSource = (DataSource) context.lookup(dataSourceName);
                } catch (Exception e) {
                    dataSource = (DataSource) context.lookup(DPDPCommonConstants.JDBC_ENV_CONTEXT_PREFIX
                            + dataSourceName);
                }
                LOG.debug("Resolved the shared DPDP datasource: " + dataSourceName);
            } catch (Exception e) {
                throw new DPDPCommonRuntimeException("Unable to resolve the shared DPDP datasource ["
                        + dataSourceName + "]", e);
            }
        }
    }

    /**
     * Returns a connection for the shared DPDP datasource, with autocommit disabled - the
     * caller owns the full transaction lifecycle (commit/rollback/close).
     */
    public Connection getDBConnection() {

        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            connection.setAutoCommit(false);
            return connection;
        } catch (SQLException e) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException closeException) {
                    e.addSuppressed(closeException);
                }
            }
            throw new DPDPCommonRuntimeException("Error while obtaining a DPDP DB connection.", e);
        }
    }

    public DataSource getDataSource() {

        return dataSource;
    }

    public void commitTransaction(Connection connection) {

        try {
            if (connection != null) {
                connection.commit();
            }
        } catch (SQLException e) {
            LOG.error("An error occurred while committing a DPDP transaction.", e);
        }
    }

    public void rollbackTransaction(Connection connection) {

        try {
            if (connection != null) {
                connection.rollback();
            }
        } catch (SQLException e) {
            LOG.error("An error occurred while rolling back a DPDP transaction.", e);
        }
    }
}
