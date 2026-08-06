package org.wso2.dpdp.accelerator.complaint.mgt.dao.util;

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

    private static final String DEFAULT_JDBC_URL = System.getProperty("CO_DB_URL",
            "jdbc:mysql://localhost:3306/complaint_db?useSSL=false&allowPublicKeyRetrieval=true");
    private static final String DEFAULT_JDBC_USER = System.getProperty("CO_DB_USER", "root");
    private static final String DEFAULT_JDBC_PASS = System.getProperty("CO_DB_PASS", "root");

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
            String dbUrl = System.getProperty("CO_DB_URL", DEFAULT_JDBC_URL);
            String dbUser = System.getProperty("CO_DB_USER", DEFAULT_JDBC_USER);
            String dbPass = System.getProperty("CO_DB_PASS", DEFAULT_JDBC_PASS);
            return DriverManager.getConnection(dbUrl, dbUser, dbPass);
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
