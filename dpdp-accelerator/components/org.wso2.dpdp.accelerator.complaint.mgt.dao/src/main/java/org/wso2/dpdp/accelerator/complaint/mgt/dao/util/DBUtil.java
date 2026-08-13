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

package org.wso2.dpdp.accelerator.complaint.mgt.dao.util;

import org.wso2.dpdp.common.config.ConfigProvider;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DBUtil {

    private static final Logger LOGGER = Logger.getLogger(DBUtil.class.getName());
    private static DataSource dataSource = null;

    private DBUtil() {
    }

    public static Connection getConnection() throws SQLException {
        if (dataSource != null) {
            return dataSource.getConnection();
        }
        try {
            InitialContext ctx = new InitialContext();
            dataSource = (DataSource) ctx.lookup("java:comp/env/jdbc/ComplaintDB");
            return dataSource.getConnection();
        } catch (NamingException e) {
            // JNDI is only unavailable outside this accelerator (a plain unit test, or the WAR
            // run standalone) - deployment.toml's [datasource.ComplaintDB] is still consulted
            // first there via ConfigProvider, with the CO_DB_* system properties (set directly
            // by H2TestDbSupport in tests) as the fallback beneath that.
            // The TOML value is written with "&amp;" (see the comment above [datasource.ComplaintDB]
            // in the shipped deployment.toml) so Carbon's verbatim, non-escaping transcription into
            // master-datasources.xml produces a real "&" once that XML is parsed. Read directly off
            // the TOML here instead, so unescape it back to a literal "&" before using it as a URL.
            String dbUrl = ConfigProvider.getString("datasource.ComplaintDB.url",
                    System.getProperty("CO_DB_URL",
                            "jdbc:mysql://localhost:3306/complaint_db?useSSL=false&allowPublicKeyRetrieval=true"))
                    .replace("&amp;", "&");
            String dbUser = ConfigProvider.getString("datasource.ComplaintDB.username",
                    System.getProperty("CO_DB_USER", "root"));
            String dbPass = ConfigProvider.getString("datasource.ComplaintDB.password",
                    System.getProperty("CO_DB_PASS", "root"));
            return DriverManager.getConnection(dbUrl, dbUser, dbPass);
        }
    }

    /** Unit of work run against a single connection inside {@link #executeInTransaction}. */
    @FunctionalInterface
    public interface TransactionalWork {
        void execute(Connection conn) throws SQLException;
    }

    /**
     * Runs the given work against a single connection with auto-commit disabled, committing on success and
     * rolling back if the work throws. Lets callers group multiple DAO writes (e.g. a status update and its
     * audit event) into one atomic transaction.
     */
    public static void executeInTransaction(TransactionalWork work) throws SQLException {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                work.execute(conn);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public static void closeAll(Connection conn, PreparedStatement ps, ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing ResultSet", e);
            }
        }
        if (ps != null) {
            try {
                ps.close();
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing PreparedStatement", e);
            }
        }
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing Connection", e);
            }
        }
    }
}
