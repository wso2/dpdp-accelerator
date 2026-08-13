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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Points DBUtil at a fresh in-memory H2 database for a DAO test class. DBUtil.getConnection()
 * always tries a JNDI lookup first; that lookup fails in a plain JUnit JVM (no app-server context
 * bound), so it falls back to DriverManager using the CO_DB_URL/CO_DB_USER/CO_DB_PASS system
 * properties set here - no production code changes needed to make the DAOs testable.
 */
public final class H2TestDbSupport {

    private H2TestDbSupport() {
    }

    /** Points DBUtil at a brand-new named in-memory H2 database and runs the given DDL against it. */
    public static void setUpDatabase(String dbName, String... ddl) throws SQLException {
        String url = "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";
        System.setProperty("CO_DB_URL", url);
        System.setProperty("CO_DB_USER", "sa");
        System.setProperty("CO_DB_PASS", "");

        try (Connection conn = DriverManager.getConnection(url, "sa", "");
                Statement stmt = conn.createStatement()) {
            for (String statement : ddl) {
                stmt.execute(statement);
            }
        }
    }
}
