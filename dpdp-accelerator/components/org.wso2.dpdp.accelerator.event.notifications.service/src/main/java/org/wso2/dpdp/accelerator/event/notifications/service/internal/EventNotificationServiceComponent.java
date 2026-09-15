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

package org.wso2.dpdp.accelerator.event.notifications.service.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.event.notifications.common.listener.DPDPLifecycleEventListener;
import org.wso2.dpdp.accelerator.event.notifications.dao.EventNotificationDAOProvider;
import org.wso2.dpdp.accelerator.event.notifications.service.EventPublishService;
import org.wso2.dpdp.accelerator.event.notifications.service.SubscriptionService;
import org.wso2.dpdp.accelerator.event.notifications.service.TopicService;
import org.wso2.dpdp.accelerator.event.notifications.service.dispatch.SignedEventPayloadFactory;
import org.wso2.dpdp.accelerator.event.notifications.service.impl.EventPublishServiceImpl;
import org.wso2.dpdp.accelerator.event.notifications.service.impl.SubscriptionServiceImpl;
import org.wso2.dpdp.accelerator.event.notifications.service.impl.TopicServiceImpl;
import org.wso2.dpdp.accelerator.event.notifications.service.listener.DPDPLifecycleEventPublisher;
import org.wso2.dpdp.accelerator.event.notifications.service.recovery.DeliveryRecoveryService;

/**
 * Constructs the Event Notification service graph and publishes its public OSGi
 * service contracts.
 */
@Component(
        name = "org.wso2.dpdp.accelerator.event.notifications.service.internal.EventNotificationServiceComponent",
        immediate = true
)
public class EventNotificationServiceComponent {

    private static final Log LOG = LogFactory.getLog(EventNotificationServiceComponent.class);

    private SubscriptionServiceImpl subscriptionService;
    private DeliveryRecoveryService deliveryRecoveryService;
    private volatile EventNotificationDAOProvider daoProvider;

    private ServiceRegistration<TopicService> topicServiceRegistration;
    private ServiceRegistration<SubscriptionService> subscriptionServiceRegistration;
    private ServiceRegistration<EventPublishService> eventPublishServiceRegistration;
    private ServiceRegistration<DPDPLifecycleEventListener> lifecycleEventListenerRegistration;

    @Activate
    protected void activate(ComponentContext context) {
        DPDPConfigurationService configurationService =
                EventNotificationDataHolder.getInstance().getConfigurationService();
        if (configurationService == null) {
            throw new IllegalStateException("DPDP configuration service is unavailable.");
        }

        TopicService topicService = new TopicServiceImpl(daoProvider.getTopicDAO());
        subscriptionService = new SubscriptionServiceImpl(
                daoProvider.getSubscriptionDAO(), daoProvider.getTopicDAO(), daoProvider.getDeliveryDAO(),
                daoProvider.getDeliveryAckDAO(), configurationService);
        EventPublishService eventPublishService = new EventPublishServiceImpl(
                daoProvider.getEventDAO(), daoProvider.getTopicDAO(),
                daoProvider.getDeliveryDAO(), daoProvider.getDeliveryAckDAO(), daoProvider.getSubscriptionDAO(),
                configurationService, new SignedEventPayloadFactory());
        deliveryRecoveryService = new DeliveryRecoveryService(
                daoProvider.getSubscriptionDAO(), daoProvider.getDeliveryDAO(),
                subscriptionService, configurationService);

        try {
            subscriptionService.start();
            deliveryRecoveryService.start();
            subscriptionService.setManualRetryDispatcher(deliveryRecoveryService::submitManualRetry);

            topicServiceRegistration = context.getBundleContext().registerService(
                    TopicService.class, topicService, null);
            subscriptionServiceRegistration = context.getBundleContext().registerService(
                    SubscriptionService.class, subscriptionService, null);
            eventPublishServiceRegistration = context.getBundleContext().registerService(
                    EventPublishService.class, eventPublishService, null);

            if (configurationService.isEventNotificationLifecycleEventsPublishingEnabled()) {
                DPDPLifecycleEventListener lifecycleEventPublisher =
                        new DPDPLifecycleEventPublisher(eventPublishService);
                lifecycleEventListenerRegistration = context.getBundleContext().registerService(
                        DPDPLifecycleEventListener.class, lifecycleEventPublisher, null);
            }
            LOG.info("Event Notification services are activated successfully.");
        } catch (RuntimeException e) {
            unregisterPublishedServices();
            stopManagedServices();
            throw e;
        }
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {
        unregisterPublishedServices();
        stopManagedServices();
        LOG.info("Event Notification services are deactivated.");
    }

    private void unregisterPublishedServices() {
        unregister(lifecycleEventListenerRegistration);
        lifecycleEventListenerRegistration = null;
        unregister(eventPublishServiceRegistration);
        eventPublishServiceRegistration = null;
        unregister(subscriptionServiceRegistration);
        subscriptionServiceRegistration = null;
        unregister(topicServiceRegistration);
        topicServiceRegistration = null;
    }

    @Reference
    protected void setDPDPConfigurationService(DPDPConfigurationService configurationService) {
        EventNotificationDataHolder.getInstance().setConfigurationService(configurationService);
    }

    protected void unsetDPDPConfigurationService(DPDPConfigurationService configurationService) {
        EventNotificationDataHolder dataHolder = EventNotificationDataHolder.getInstance();
        if (dataHolder.getConfigurationService() == configurationService) {
            dataHolder.setConfigurationService(null);
        }
    }

    @Reference(
            service = EventNotificationDAOProvider.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.STATIC,
            unbind = "unsetEventNotificationDAOProvider"
    )
    protected void setEventNotificationDAOProvider(EventNotificationDAOProvider daoProvider) {

        this.daoProvider = daoProvider;
    }

    protected void unsetEventNotificationDAOProvider(EventNotificationDAOProvider daoProvider) {

        if (this.daoProvider == daoProvider) {
            this.daoProvider = null;
        }
    }

    private void stopManagedServices() {
        if (deliveryRecoveryService != null) {
            deliveryRecoveryService.stop();
            deliveryRecoveryService = null;
        }
        if (subscriptionService != null) {
            subscriptionService.stop();
            subscriptionService = null;
        }
    }

    private static void unregister(ServiceRegistration<?> registration) {
        if (registration != null) {
            registration.unregister();
        }
    }
}
