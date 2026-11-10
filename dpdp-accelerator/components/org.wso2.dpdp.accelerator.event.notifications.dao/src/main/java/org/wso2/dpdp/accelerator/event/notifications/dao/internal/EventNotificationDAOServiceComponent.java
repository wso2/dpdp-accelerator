/**
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 * <p>
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 *     http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.dpdp.accelerator.event.notifications.dao.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.common.exception.DPDPSystemException;
import org.wso2.dpdp.accelerator.common.util.DatabaseUtils;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryAckDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.EventDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.EventNotificationDAOProvider;
import org.wso2.dpdp.accelerator.event.notifications.dao.SubscriptionDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.TopicDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.impl.DeliveryAckDAOImpl;
import org.wso2.dpdp.accelerator.event.notifications.dao.impl.DeliveryDAOImpl;
import org.wso2.dpdp.accelerator.event.notifications.dao.impl.EventDAOImpl;
import org.wso2.dpdp.accelerator.event.notifications.dao.impl.SubscriptionDAOImpl;
import org.wso2.dpdp.accelerator.event.notifications.dao.impl.TopicDAOImpl;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Creates the Event Notification DAOs and publishes them through a single OSGi provider service.
 */
@Component(
        name = "org.wso2.dpdp.accelerator.event.notifications.dao.internal.EventNotificationDAOServiceComponent",
        service = EventNotificationDAOProvider.class,
        immediate = true
)
public class EventNotificationDAOServiceComponent implements EventNotificationDAOProvider {

    private static final Log LOG = LogFactory.getLog(EventNotificationDAOServiceComponent.class);

    @Activate
    protected void activate() {

        EventNotificationDAODataHolder dataHolder = EventNotificationDAODataHolder.getInstance();
        verifyDatabaseConnection(dataHolder.getConfigurationService());
        dataHolder.setTopicDAO(new TopicDAOImpl());
        dataHolder.setSubscriptionDAO(new SubscriptionDAOImpl(dataHolder.getConfigurationService()));
        dataHolder.setEventDAO(new EventDAOImpl());
        dataHolder.setDeliveryDAO(new DeliveryDAOImpl(dataHolder.getConfigurationService()));
        dataHolder.setDeliveryAckDAO(new DeliveryAckDAOImpl());
        LOG.debug("Event Notification DAO services are activated successfully.");
    }

    /**
     * Fails bundle activation immediately when the shared DPDP datasource is not reachable,
     * rather than surfacing that failure later on the first DAO call.
     */
    private void verifyDatabaseConnection(DPDPConfigurationService configurationService) {

        verifyDatabaseConnection(DatabaseUtils.getDBConnection(), configurationService);
    }

    void verifyDatabaseConnection(Connection connection, DPDPConfigurationService configurationService) {

        try {
            int timeoutSeconds = configurationService.getJdbcConnectionVerificationTimeoutSeconds();
            if (!connection.isValid(timeoutSeconds)) {
                throw new DPDPSystemException("The DPDP database connection is not active.");
            }
            LOG.debug("Verified the DPDP database connection is active.");
        } catch (SQLException e) {
            throw new DPDPSystemException("Error while verifying the DPDP database connection.", e);
        } finally {
            DatabaseUtils.closeConnection(connection);
        }
    }

    @Deactivate
    protected void deactivate() {

        EventNotificationDAODataHolder.getInstance().clear();
        LOG.debug("Event Notification DAO services are deactivated successfully.");
    }

    @Reference(
            service = DPDPConfigurationService.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.STATIC,
            unbind = "unsetDPDPConfigurationService"
    )
    protected void setDPDPConfigurationService(DPDPConfigurationService configurationService) {

        EventNotificationDAODataHolder.getInstance().setConfigurationService(configurationService);
    }

    protected void unsetDPDPConfigurationService(DPDPConfigurationService configurationService) {

        EventNotificationDAODataHolder dataHolder = EventNotificationDAODataHolder.getInstance();
        if (dataHolder.getConfigurationService() == configurationService) {
            dataHolder.setConfigurationService(null);
        }
    }

    @Override
    public TopicDAO getTopicDAO() {

        return EventNotificationDAODataHolder.getInstance().getTopicDAO();
    }

    @Override
    public SubscriptionDAO getSubscriptionDAO() {

        return EventNotificationDAODataHolder.getInstance().getSubscriptionDAO();
    }

    @Override
    public EventDAO getEventDAO() {

        return EventNotificationDAODataHolder.getInstance().getEventDAO();
    }

    @Override
    public DeliveryDAO getDeliveryDAO() {

        return EventNotificationDAODataHolder.getInstance().getDeliveryDAO();
    }

    @Override
    public DeliveryAckDAO getDeliveryAckDAO() {

        return EventNotificationDAODataHolder.getInstance().getDeliveryAckDAO();
    }
}
