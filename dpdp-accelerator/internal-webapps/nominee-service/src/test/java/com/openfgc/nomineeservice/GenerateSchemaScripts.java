package com.openfgc.nomineeservice;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import jakarta.persistence.Persistence;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes the dialect schema scripts from the entity definitions.
 *
 * <p>The scripts are what create the schema on MySQL, PostgreSQL and SQLite, and
 * the service starts with {@code ddl-auto: validate} against them. Deriving them
 * from the entities rather than maintaining them by hand is what keeps the two
 * from drifting: a mapping change that is not reflected in the scripts would
 * otherwise only surface as a startup failure in whichever environment ran a
 * database this file forgot.
 *
 * <p>Run after changing any entity:
 * {@code mvn -q test-compile exec:java -Dexec.mainClass=com.openfgc.nomineeservice.GenerateSchemaScripts -Dexec.classpathScope=test}
 */
public final class GenerateSchemaScripts {

    private static final Map<String, String> DIALECTS = new LinkedHashMap<>();

    static {
        DIALECTS.put("mysql", "org.hibernate.dialect.MySQLDialect");
        DIALECTS.put("postgres", "org.hibernate.dialect.PostgreSQLDialect");
        DIALECTS.put("sqlite", "org.hibernate.community.dialect.SQLiteDialect");
    }

    private GenerateSchemaScripts() {
    }

    public static void main(String[] args) throws IOException {
        Path dir = Path.of("dbscripts");
        Files.createDirectories(dir);

        for (Map.Entry<String, String> entry : DIALECTS.entrySet()) {
            Path out = dir.resolve("db_schema_" + entry.getKey() + ".sql");
            Files.deleteIfExists(out);
            export(entry.getValue(), out);
            if ("sqlite".equals(entry.getKey())) {
                appendSqliteConstraints(out);
            }
            System.out.println("wrote " + out);
        }
    }

    /**
     * Adds what SQLite cannot take from the generator.
     *
     * <p>Hibernate emits the pairing constraint as an {@code ALTER TABLE}, which
     * SQLite does not support. A unique index is equivalent and is the form
     * SQLite accepts, so the constraint that stops an owner nominating the same
     * person twice holds on every supported database rather than only on two.
     */
    private static void appendSqliteConstraints(Path out) throws IOException {
        String extra = System.lineSeparator()
                + "    create unique index uq_owner_nominee"
                + System.lineSeparator()
                + "        on nominations (ownerId, nomineeId);"
                + System.lineSeparator();
        Files.writeString(out, extra, java.nio.file.StandardOpenOption.APPEND);
    }

    private static void export(String dialect, Path out) {
        Map<String, Object> settings = new HashMap<>();
        settings.put("hibernate.dialect", dialect);
        // Spring Boot applies these at runtime, turning acceptedAt into
        // accepted_at. Generating without them produces a schema that looks
        // right and fails validation on every startup.
        settings.put("hibernate.physical_naming_strategy",
                "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
        settings.put("hibernate.implicit_naming_strategy",
                "org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy");
        settings.put("jakarta.persistence.schema-generation.scripts.action", "create");
        settings.put("jakarta.persistence.schema-generation.scripts.create-target", out.toString());
        settings.put("hibernate.hbm2ddl.delimiter", ";");
        settings.put("hibernate.format_sql", "true");
        Persistence.generateSchema("nominee-schema-gen", settings);
    }
}
