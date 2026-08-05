package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.config;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.util.logging.Logger;

/**
 * Runs the deployment.toml loading + DB schema init on Tomcat's servlet lifecycle.
 */
public class AppContextListener implements ServletContextListener {

    private static final Logger LOGGER = Logger.getLogger(AppContextListener.class.getName());

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        LOGGER.info("Initializing WSO2 DPDP Complaint Server webapp...");
        AppBootstrap.loadDeploymentConfig();
        AppBootstrap.initDatabase();
        LOGGER.info("WSO2 DPDP Complaint Server webapp initialized.");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // No resources to release; DBUtil opens/closes a connection per request.
    }
}
