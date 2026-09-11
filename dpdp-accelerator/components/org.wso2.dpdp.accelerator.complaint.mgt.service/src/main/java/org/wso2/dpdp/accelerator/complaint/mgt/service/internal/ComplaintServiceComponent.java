/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.dpdp.accelerator.complaint.mgt.service.internal;

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
import org.wso2.carbon.identity.application.mgt.ApplicationManagementService;
import org.wso2.carbon.identity.event.services.IdentityEventService;
import org.wso2.carbon.identity.organization.management.service.OrganizationManager;
import org.wso2.carbon.identity.role.v2.mgt.core.RoleManagementService;
import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintDAOProvider;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintAttachmentService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintEventService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.impl.ComplaintAttachmentServiceImpl;
import org.wso2.dpdp.accelerator.complaint.mgt.service.impl.ComplaintEventServiceImpl;
import org.wso2.dpdp.accelerator.complaint.mgt.service.impl.ComplaintServiceImpl;
import org.wso2.dpdp.accelerator.complaint.mgt.service.notification.EmailNotificationClient;
import org.wso2.dpdp.accelerator.complaint.mgt.service.notification.NoOpNotificationClient;
import org.wso2.dpdp.accelerator.complaint.mgt.service.notification.NotificationClient;

/**
 * Constructs the complaint service graph and publishes its public OSGi service contracts,
 * mirroring {@code EventNotificationServiceComponent} in the event-notifications module - this
 * bundle previously ran as a plain (non-OSGi) jar, with complaint.mgt.endpoint constructing
 * {@code ComplaintServiceImpl}/{@code ComplaintEventServiceImpl}/{@code
 * ComplaintAttachmentServiceImpl} directly via their no-arg constructors instead of resolving
 * them as OSGi services.
 */
@Component(
        name = "org.wso2.dpdp.accelerator.complaint.mgt.service.internal.ComplaintServiceComponent",
        immediate = true
)
public class ComplaintServiceComponent {

    private static final Log LOG = LogFactory.getLog(ComplaintServiceComponent.class);


    private ServiceRegistration<ComplaintService> complaintServiceRegistration;
    private ServiceRegistration<ComplaintEventService> complaintEventServiceRegistration;
    private ServiceRegistration<ComplaintAttachmentService> complaintAttachmentServiceRegistration;

    @Activate
    protected void activate(ComponentContext context) {
        ComplaintDAOProvider daoProvider = ComplaintServiceDataHolder.getInstance().getDaoProvider();
        boolean emailNotificationsEnabled = ComplaintServiceDataHolder.getInstance().getConfigurationService()
                .isComplaintsEmailNotificationsEnabled();
        NotificationClient notificationClient = createNotificationClient(emailNotificationsEnabled);
        LOG.info("Complaint email notifications are " + (emailNotificationsEnabled ? "enabled" : "disabled")
                + "; using " + notificationClient.getClass().getSimpleName() + ".");
        ComplaintService complaintService = new ComplaintServiceImpl(
                daoProvider.getComplaintDAO(), daoProvider.getComplaintEventDAO(), notificationClient);
        ComplaintEventService complaintEventService = new ComplaintEventServiceImpl(
                daoProvider.getComplaintEventDAO(), daoProvider.getComplaintDAO(), complaintService,
                notificationClient);
        ComplaintAttachmentService complaintAttachmentService = new ComplaintAttachmentServiceImpl(
                daoProvider.getComplaintAttachmentDAO(), daoProvider.getComplaintEventDAO(), complaintService);

        complaintServiceRegistration = context.getBundleContext().registerService(
                ComplaintService.class, complaintService, null);
        complaintEventServiceRegistration = context.getBundleContext().registerService(
                ComplaintEventService.class, complaintEventService, null);
        complaintAttachmentServiceRegistration = context.getBundleContext().registerService(
                ComplaintAttachmentService.class, complaintAttachmentService, null);
        LOG.debug("Complaint services are activated successfully.");
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {

        unregister(complaintAttachmentServiceRegistration);
        complaintAttachmentServiceRegistration = null;
        unregister(complaintEventServiceRegistration);
        complaintEventServiceRegistration = null;
        unregister(complaintServiceRegistration);
        complaintServiceRegistration = null;
        LOG.debug("Complaint services are deactivated.");
    }

    @Reference(
            service = ComplaintDAOProvider.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.STATIC,
            unbind = "unsetComplaintDAOProvider"
    )
    protected void setComplaintDAOProvider(ComplaintDAOProvider daoProvider) {

        ComplaintServiceDataHolder.getInstance().setDaoProvider(daoProvider);
    }

    protected void unsetComplaintDAOProvider(ComplaintDAOProvider daoProvider) {

        ComplaintServiceDataHolder dataHolder = ComplaintServiceDataHolder.getInstance();
        if (dataHolder.getDaoProvider() == daoProvider) {
            dataHolder.setDaoProvider(null);
        }
    }

    @Reference(
            service = DPDPConfigurationService.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetConfigurationService"
    )
    protected void setConfigurationService(DPDPConfigurationService configurationService) {

        LOG.debug("Setting the DPDP Configuration Service.");
        ComplaintServiceDataHolder.getInstance().setConfigurationService(configurationService);
    }

    protected void unsetConfigurationService(DPDPConfigurationService configurationService) {

        LOG.debug("Unsetting the DPDP Configuration Service.");
        ComplaintServiceDataHolder.getInstance().setConfigurationService(null);
    }

    // The five references below are all optional: they're only consumed by
    // EmailNotificationClient to resolve complaint notification recipients, and a notification
    // failure must never block this bundle's core complaint functionality from activating.

    @Reference(
            service = IdentityEventService.class,
            cardinality = ReferenceCardinality.OPTIONAL,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetIdentityEventService"
    )
    protected void setIdentityEventService(IdentityEventService identityEventService) {

        LOG.debug("Setting the Identity Event Service.");
        ComplaintServiceDataHolder.getInstance().setIdentityEventService(identityEventService);
    }

    protected void unsetIdentityEventService(IdentityEventService identityEventService) {

        LOG.debug("Unsetting the Identity Event Service.");
        ComplaintServiceDataHolder.getInstance().setIdentityEventService(null);
    }

    @Reference(
            service = RealmService.class,
            cardinality = ReferenceCardinality.OPTIONAL,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetRealmService"
    )
    protected void setRealmService(RealmService realmService) {

        LOG.debug("Setting the Realm Service.");
        ComplaintServiceDataHolder.getInstance().setRealmService(realmService);
    }

    protected void unsetRealmService(RealmService realmService) {

        LOG.debug("Unsetting the Realm Service.");
        ComplaintServiceDataHolder.getInstance().setRealmService(null);
    }

    @Reference(
            service = ApplicationManagementService.class,
            cardinality = ReferenceCardinality.OPTIONAL,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetApplicationManagementService"
    )
    protected void setApplicationManagementService(ApplicationManagementService applicationManagementService) {

        LOG.debug("Setting the Application Management Service.");
        ComplaintServiceDataHolder.getInstance().setApplicationManagementService(applicationManagementService);
    }

    protected void unsetApplicationManagementService(ApplicationManagementService applicationManagementService) {

        LOG.debug("Unsetting the Application Management Service.");
        ComplaintServiceDataHolder.getInstance().setApplicationManagementService(null);
    }

    @Reference(
            service = RoleManagementService.class,
            cardinality = ReferenceCardinality.OPTIONAL,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetRoleManagementService"
    )
    protected void setRoleManagementService(RoleManagementService roleManagementService) {

        LOG.debug("Setting the Role Management Service.");
        ComplaintServiceDataHolder.getInstance().setRoleManagementService(roleManagementService);
    }

    protected void unsetRoleManagementService(RoleManagementService roleManagementService) {

        LOG.debug("Unsetting the Role Management Service.");
        ComplaintServiceDataHolder.getInstance().setRoleManagementService(null);
    }

    @Reference(
            service = OrganizationManager.class,
            cardinality = ReferenceCardinality.OPTIONAL,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetOrganizationManager"
    )
    protected void setOrganizationManager(OrganizationManager organizationManager) {

        LOG.debug("Setting the Organization Manager.");
        ComplaintServiceDataHolder.getInstance().setOrganizationManager(organizationManager);
    }

    protected void unsetOrganizationManager(OrganizationManager organizationManager) {

        LOG.debug("Unsetting the Organization Manager.");
        ComplaintServiceDataHolder.getInstance().setOrganizationManager(null);
    }

    /** Extracted so the selection logic itself is unit-testable without an OSGi runtime. */
    static NotificationClient createNotificationClient(boolean emailNotificationsEnabled) {

        return emailNotificationsEnabled ? new EmailNotificationClient() : new NoOpNotificationClient();
    }

    private static void unregister(ServiceRegistration<?> registration) {
        if (registration != null) {
            registration.unregister();
        }
    }
}
