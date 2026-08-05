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
