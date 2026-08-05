package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.wso2.dpdp.accelerator.complaint.mgt.service.util.PriorityMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppBootstrapTest {

    private static final String[] MANAGED_PROPERTIES = {
            "deployment.config.path", "CO_DB_TYPE", "CO_DB_URL", "CO_DB_USER", "CO_DB_PASS",
            "CO_MAX_ATTACHMENT_SIZE_BYTES", "CO_STATUTORY_DUE_PERIOD_DAYS"
    };

    @AfterEach
    void cleanUp() {
        for (String property : MANAGED_PROPERTIES) {
            System.clearProperty(property);
        }
        // loadDeploymentConfig() can replace PriorityMapper's static mapping wholesale with no reset
        // hook, so restore the built-in defaults to avoid leaking state into other test classes.
        Map<String, String> defaults = new HashMap<>();
        defaults.put("DATA_BREACH", "CRITICAL");
        defaults.put("UNAUTHORIZED_DATA_SHARING", "HIGH");
        defaults.put("CONSENT_WITHDRAWN_DATA_STILL_USED", "HIGH");
        defaults.put("PURPOSE_VIOLATION", "HIGH");
        defaults.put("DATA_ACCESS_DENIED", "HIGH");
        defaults.put("DATA_ERASURE_NOT_COMPLETED", "MEDIUM");
        defaults.put("DATA_CORRECTION_NOT_COMPLETED", "MEDIUM");
        defaults.put("CONSENT_LIFECYCLE_ISSUE", "MEDIUM");
        defaults.put("EXCESSIVE_DATA_COLLECTION", "MEDIUM");
        defaults.put("OTHER", "LOW");
        PriorityMapper.configure(defaults);
    }

    // ---- loadDeploymentConfig ----

    @Test
    void loadDeploymentConfigDoesNothingWhenNoFileIsFound() {
        System.setProperty("deployment.config.path", "/does/not/exist/deployment.toml");

        AppBootstrap.loadDeploymentConfig();

        assertNull(System.getProperty("CO_DB_URL"));
    }

    @Test
    void loadDeploymentConfigAppliesEachTableWhenFileIsValid() throws IOException {
        Path file = Files.createTempFile("deployment", ".toml");
        try {
            Files.writeString(file, "[database]\n"
                    + "type = \"mysql\"\n"
                    + "url = \"jdbc:mysql://db-host:3306/complaint_db\"\n"
                    + "user = \"app_user\"\n"
                    + "password = \"app_pass\"\n"
                    + "\n"
                    + "[attachment]\n"
                    + "maxSizeBytes = 5242880\n"
                    + "\n"
                    + "[statutory]\n"
                    + "dueDatePeriodDays = 45\n"
                    + "\n"
                    + "[categoryPriority]\n"
                    + "DATA_BREACH = \"high\"\n"
                    + "CUSTOM_CATEGORY = \"low\"\n");
            System.setProperty("deployment.config.path", file.toString());

            AppBootstrap.loadDeploymentConfig();

            assertEquals("mysql", System.getProperty("CO_DB_TYPE"));
            assertEquals("jdbc:mysql://db-host:3306/complaint_db", System.getProperty("CO_DB_URL"));
            assertEquals("app_user", System.getProperty("CO_DB_USER"));
            assertEquals("app_pass", System.getProperty("CO_DB_PASS"));
            assertEquals("5242880", System.getProperty("CO_MAX_ATTACHMENT_SIZE_BYTES"));
            assertEquals("45", System.getProperty("CO_STATUTORY_DUE_PERIOD_DAYS"));
            assertEquals("HIGH", PriorityMapper.derivePriority("DATA_BREACH"));
            assertEquals("LOW", PriorityMapper.derivePriority("CUSTOM_CATEGORY"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void loadDeploymentConfigNeverOverwritesAnAlreadySetSystemProperty() throws IOException {
        System.setProperty("CO_DB_URL", "jdbc:preexisting-url");
        Path file = Files.createTempFile("deployment", ".toml");
        try {
            Files.writeString(file, "[database]\n"
                    + "url = \"jdbc:mysql://should-not-be-used:3306/db\"\n");
            System.setProperty("deployment.config.path", file.toString());

            AppBootstrap.loadDeploymentConfig();

            assertEquals("jdbc:preexisting-url", System.getProperty("CO_DB_URL"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void loadDeploymentConfigSkipsApplyingConfigWhenFileHasParseErrors() throws IOException {
        Path file = Files.createTempFile("deployment", ".toml");
        try {
            Files.writeString(file, "this is not [ valid toml =\n");
            System.setProperty("deployment.config.path", file.toString());

            AppBootstrap.loadDeploymentConfig();

            assertNull(System.getProperty("CO_DB_URL"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    // ---- initDatabase ----

    @Test
    void initDatabaseCreatesSchemaAgainstConfiguredH2Database() throws Exception {
        String url = "jdbc:h2:mem:app_bootstrap_test;DB_CLOSE_DELAY=-1;MODE=MySQL";
        System.setProperty("CO_DB_URL", url);
        System.setProperty("CO_DB_USER", "sa");
        System.setProperty("CO_DB_PASS", "");

        AppBootstrap.initDatabase();

        try (Connection conn = DriverManager.getConnection(url, "sa", "");
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'")) {
            Set<String> tables = new HashSet<>();
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
            assertTrue(tables.contains("COMPLAINT"));
            assertTrue(tables.contains("COMPLAINT_EVENT"));
            assertTrue(tables.contains("COMPLAINT_ATTACHMENT"));
        }
    }

    @Test
    void initDatabaseFallsBackToDefaultH2UrlWhenNoneConfigured() {
        AppBootstrap.initDatabase();

        assertNotNull(System.getProperty("CO_DB_URL"));
        assertTrue(System.getProperty("CO_DB_URL").startsWith("jdbc:h2:mem:"));
        assertEquals("sa", System.getProperty("CO_DB_USER"));
    }

    @Test
    void initDatabaseUsesMysqlDefaultsWhenDbTypeIsMysqlAndDoesNotThrowWhenUnreachable() {
        System.setProperty("CO_DB_TYPE", "mysql");

        // A real MySQL server is not available in this test environment; initDatabase() must
        // swallow the connection failure (it only logs a warning) rather than propagate it.
        assertDoesNotThrow(AppBootstrap::initDatabase);

        assertTrue(System.getProperty("CO_DB_URL").startsWith("jdbc:mysql:"));
        assertEquals("root", System.getProperty("CO_DB_USER"));
    }
}
