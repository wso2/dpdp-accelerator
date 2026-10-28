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

package org.wso2.dpdp.accelerator.identity.extensions.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.core.ServerStartupObserver;
import org.wso2.carbon.stratos.common.beans.TenantInfoBean;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;
import org.wso2.dpdp.accelerator.identity.extensions.tenant.DPDPIdentityExtensionTenantMgtListener;
import org.wso2.dpdp.accelerator.identity.extensions.tenant.EventNotificationTopicProvisioner;

/**
 * Provisions the DPDP Consent Portal for the super tenant only once the entire server - every
 * bundle, every deployment artifact - has finished starting.
 *
 * This exists because {@code DPDPIdentityExtensionServiceComponent}'s {@code @Activate} used to
 * do this directly, racing against {@code APIResourceManagementServiceComponent}'s consent-mgt v2
 * API-resource registration: a mandatory {@code @Reference} on {@code APIResourceManager} only
 * guarantees that service object exists, not that the specific API resources it seeds have been
 * written yet, since that component registers the service before it finishes seeding. Deferring to
 * {@link #completedServerStartup()} removes the race entirely, since every bundle's own activation
 * work - including that seeding - is guaranteed complete by the time it fires.
 *
 * Deliberately scoped to only the super tenant: every other tenant is provisioned by
 * {@link DPDPIdentityExtensionTenantMgtListener#onTenantCreate}/{@code onTenantUpdate}, triggered
 * only once an admin actually creates or updates a tenant - always well after server startup, so
 * that path never raced and does not need to move here.
 */
public class DPDPServerStartupObserver implements ServerStartupObserver {

    private static final Log LOG = LogFactory.getLog(DPDPServerStartupObserver.class);

    @Override
    public void completingServerStartup() {

        // No-op: the super tenant is provisioned after startup completes, not before.
    }

    @Override
    public void completedServerStartup() {

        try {
            EventNotificationTopicProvisioner.reconcileExistingTenantSystemTopics();
        } catch (Exception e) {
            LOG.error("Error reconciling DPDP system topics across existing tenants at startup.", e);
        }

        try {
            TenantInfoBean superTenant = new TenantInfoBean();
            superTenant.setTenantId(MultitenantConstants.SUPER_TENANT_ID);
            superTenant.setTenantDomain(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME);
            superTenant.setAdmin(DPDPIdentityExtensionDataHolder.getInstance().getRealmService()
                    .getBootstrapRealm().getRealmConfiguration().getAdminUserName());
            DPDPIdentityExtensionTenantMgtListener.provisionTenant(superTenant);
        } catch (Exception e) {
            LOG.error("Error provisioning the DPDP Consent Portal for the super tenant.", e);
        }
    }
}
