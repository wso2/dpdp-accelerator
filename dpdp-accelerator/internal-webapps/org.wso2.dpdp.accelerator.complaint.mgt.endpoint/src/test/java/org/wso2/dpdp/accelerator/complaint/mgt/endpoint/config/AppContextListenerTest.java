package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.servlet.ServletContextEvent;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class AppContextListenerTest {

    private static final String[] MANAGED_PROPERTIES = {"CO_DB_TYPE", "CO_DB_URL", "CO_DB_USER", "CO_DB_PASS"};

    @Mock
    private ServletContextEvent servletContextEvent;

    private final AppContextListener listener = new AppContextListener();

    @AfterEach
    void cleanUp() {
        for (String property : MANAGED_PROPERTIES) {
            System.clearProperty(property);
        }
    }

    @Test
    void contextInitializedLoadsConfigAndInitializesTheDatabaseWithoutThrowing() {
        assertDoesNotThrow(() -> listener.contextInitialized(servletContextEvent));

        // No deployment.toml is present in this working directory, so initDatabase() should have
        // fallen back to its built-in H2 defaults.
        assertNotNull(System.getProperty("CO_DB_URL"));
        assertTrue(System.getProperty("CO_DB_URL").startsWith("jdbc:h2:mem:"));
    }

    @Test
    void contextDestroyedDoesNotThrow() {
        assertDoesNotThrow(() -> listener.contextDestroyed(servletContextEvent));
    }
}
