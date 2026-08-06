package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.config;

import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.util.DBUtil;
import org.wso2.dpdp.accelerator.complaint.mgt.service.util.PriorityMapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Startup logic (deployment.toml loading + DB schema init) for the Tomcat-deployed WAR,
 * triggered by AppContextListener on servlet context initialization.
 */
public final class AppBootstrap {

    private static final Logger LOGGER = Logger.getLogger(AppBootstrap.class.getName());

    private AppBootstrap() {
    }

    public static void loadDeploymentConfig() {
        File file = resolveDeploymentConfigFile();
        if (file == null) {
            LOGGER.info("No deployment.toml found; using built-in defaults for all configuration.");
            return;
        }

        try {
            TomlParseResult result = Toml.parse(file.toPath());
            if (result.hasErrors()) {
                result.errors().forEach(error -> LOGGER.warning("deployment.toml parse error: " + error));
                return;
            }
            LOGGER.info("Loading configuration from: " + file.getAbsolutePath());

            TomlTable database = result.getTable("database");
            if (database != null) {
                setSystemPropertyIfAbsent("CO_DB_TYPE", database.getString("type"));
                setSystemPropertyIfAbsent("CO_DB_URL", database.getString("url"));
                setSystemPropertyIfAbsent("CO_DB_USER", database.getString("user"));
                setSystemPropertyIfAbsent("CO_DB_PASS", database.getString("password"));
            }

            TomlTable attachment = result.getTable("attachment");
            Long maxSizeBytes = attachment == null ? null : attachment.getLong("maxSizeBytes");
            if (maxSizeBytes != null) {
                setSystemPropertyIfAbsent("CO_MAX_ATTACHMENT_SIZE_BYTES", String.valueOf(maxSizeBytes));
            }

            TomlTable statutory = result.getTable("statutory");
            Long dueDatePeriodDays = statutory == null ? null : statutory.getLong("dueDatePeriodDays");
            if (dueDatePeriodDays != null) {
                setSystemPropertyIfAbsent("CO_STATUTORY_DUE_PERIOD_DAYS", String.valueOf(dueDatePeriodDays));
            }

            TomlTable categoryPriority = result.getTable("categoryPriority");
            if (categoryPriority != null) {
                Map<String, String> overrides = new HashMap<>();
                for (String key : categoryPriority.keySet()) {
                    String value = categoryPriority.getString(key);
                    if (value != null) {
                        overrides.put(key, value);
                    }
                }
                PriorityMapper.configure(overrides);
                LOGGER.info("Loaded " + overrides.size() + " category-to-priority mappings.");
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not read deployment.toml: " + e.getMessage(), e);
        }
    }

    /**
     * Looks for deployment.toml in, in order: an explicit -Ddeployment.config.path override, the
     * CO_DEPLOYMENT_CONFIG_PATH env var, the current working directory (repo root during local
     * dev), the Identity Server's own repository/conf/deployment.toml (found via the carbon.home
     * system property wso2server.sh always sets), and finally $CATALINA_BASE or $CATALINA_HOME
     * conf/ directories so a plain Tomcat deployment can pick it up from a mounted volume.
     */
    private static File resolveDeploymentConfigFile() {
        String explicitPath = System.getProperty("deployment.config.path", System.getenv("CO_DEPLOYMENT_CONFIG_PATH"));
        if (explicitPath != null) {
            File file = new File(explicitPath);
            if (file.isFile()) {
                return file;
            }
        }

        File cwdFile = new File("deployment.toml");
        if (cwdFile.isFile()) {
            return cwdFile;
        }

        String carbonHome = System.getProperty("carbon.home");
        if (carbonHome != null) {
            File file = new File(carbonHome, "repository/conf/deployment.toml");
            if (file.isFile()) {
                return file;
            }
        }

        for (String catalinaDir : new String[]{System.getenv("CATALINA_BASE"), System.getenv("CATALINA_HOME")}) {
            if (catalinaDir == null) {
                continue;
            }
            File file = new File(catalinaDir, "conf/deployment.toml");
            if (file.isFile()) {
                return file;
            }
        }
        return null;
    }

    public static void initDatabase() {
        // Only used as DBUtil's DriverManager fallback default - applied when no
        // jdbc/ComplaintDB resource is bound (see META-INF/context.xml) and no CO_DB_*
        // override is already set. A properly configured deployment goes through the JNDI
        // datasource instead; see DBUtil#getConnection().
        String dbType = System.getProperty("CO_DB_TYPE", System.getenv("CO_DB_TYPE"));

        String defaultUrl = "jdbc:h2:mem:complaint_db;DB_CLOSE_DELAY=-1;MODE=MySQL";
        String defaultUser = "sa";
        String defaultPass = "";

        if ("mysql".equalsIgnoreCase(dbType)) {
            defaultUrl = "jdbc:mysql://localhost:3306/complaint_db?createDatabaseIfNotExist=true&useSSL=false"
                    + "&allowPublicKeyRetrieval=true";
            defaultUser = "root";
            defaultPass = "root";
        }

        System.setProperty("CO_DB_URL", System.getProperty("CO_DB_URL",
                System.getenv("CO_DB_URL") != null ? System.getenv("CO_DB_URL") : defaultUrl));
        System.setProperty("CO_DB_USER", System.getProperty("CO_DB_USER",
                System.getenv("CO_DB_USER") != null ? System.getenv("CO_DB_USER") : defaultUser));
        System.setProperty("CO_DB_PASS", System.getProperty("CO_DB_PASS",
                System.getenv("CO_DB_PASS") != null ? System.getenv("CO_DB_PASS") : defaultPass));

        try (Connection conn = DBUtil.getConnection();
             Statement stmt = conn.createStatement()) {

            String dbUrl = conn.getMetaData().getURL();
            boolean isMysql = dbUrl.startsWith("jdbc:mysql:");
            LOGGER.info("Connected to database (" + (isMysql ? "MySQL" : "H2") + "): " + dbUrl);

            InputStream is = AppBootstrap.class.getClassLoader().getResourceAsStream("dbscripts/mysql.sql");
            if (is == null) {
                is = DBUtil.class.getClassLoader().getResourceAsStream("dbscripts/mysql.sql");
            }

            if (is != null) {
                StringBuilder sqlBuilder = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.startsWith("--") || line.isEmpty()) continue;
                        sqlBuilder.append(line).append(" ");
                    }
                }

                String[] statements = sqlBuilder.toString().split(";");
                for (String sql : statements) {
                    String statementToExecute = isMysql ? sql.trim() : sanitizeSqlForH2(sql.trim());
                    if (!statementToExecute.isEmpty()) {
                        try {
                            stmt.execute(statementToExecute);
                        } catch (Exception ex) {
                            LOGGER.warning("Warning executing DDL statement [" + statementToExecute + "]: "
                                    + ex.getMessage());
                        }
                    }
                }

                LOGGER.info("Database schema initialized successfully.");
            } else {
                LOGGER.warning("mysql.sql script not found on classpath, skipping DDL execution.");
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error initializing database tables", e);
        }
    }

    private static void setSystemPropertyIfAbsent(String property, String value) {
        if (value != null && System.getProperty(property) == null) {
            System.setProperty(property, value);
        }
    }

    private static String sanitizeSqlForH2(String sql) {
        if (sql == null) return "";
        return sql.replaceAll("(?i)ENGINE\\s*=\\s*InnoDB", "")
                  .replaceAll("(?i)DEFAULT\\s+CHARSET\\s*=\\s*utf8mb4", "")
                  .replaceAll("(?i)COLLATE\\s*=?\\s*utf8mb4_[^\\s,;()]+", "")
                  .replaceAll("(?i)CHARACTER\\s+SET\\s+utf8mb4", "")
                  .replaceAll("(?i)ON\\s+UPDATE\\s+CURRENT_TIMESTAMP", "")
                  .replaceAll("_utf8mb4'", "'")
                  .trim();
    }
}