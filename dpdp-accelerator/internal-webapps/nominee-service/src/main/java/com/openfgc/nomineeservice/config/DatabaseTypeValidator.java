package com.openfgc.nomineeservice.config;

import java.util.List;
import java.util.Set;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Refuses to start on a database this service does not support.
 *
 * <p>{@code NOMINEE_DB_TYPE} chooses a Spring profile. An unrecognised value is
 * not an error to Spring - it simply activates a profile for which no datasource
 * is defined, and the failure surfaces much later as a missing bean or a
 * connection to nothing. Naming the mistake here, before anything starts, is the
 * difference between "postgresql is not supported, use postgres" and a stack
 * trace about an absent DataSource.
 */
public class DatabaseTypeValidator
        implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    private static final Set<String> SUPPORTED = Set.of("mysql", "postgres", "sqlite");

    /** Spellings people reasonably try, mapped to what this service calls them. */
    private static final List<String> HINTS = List.of(
            "postgresql -> postgres",
            "psql -> postgres",
            "sqlite3 -> sqlite",
            "mariadb -> mysql");

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();
        for (String profile : environment.getActiveProfiles()) {
            if ("test".equals(profile) || SUPPORTED.contains(profile)) {
                continue;
            }
            throw new IllegalStateException(String.format(
                    "Unsupported database '%s'. NOMINEE_DB_TYPE must be one of %s.%n"
                            + "Common alternatives: %s.%n"
                            + "Create the schema first with the matching script in dbscripts/.",
                    profile, SUPPORTED, String.join(", ", HINTS)));
        }
    }
}
