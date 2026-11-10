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

package org.wso2.dpdp.accelerator.identity.extensions.tenant;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.stratos.common.exception.StratosException;
import org.wso2.carbon.user.api.Tenant;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.DefaultTopic;
import org.wso2.dpdp.accelerator.event.notifications.service.TopicService;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reconciles the system-defined Event Notification topics for one tenant. Every method here
 * assumes it is already running inside the correct tenant's {@code PrivilegedCarbonContext}
 * flow; that setup lives in the caller ({@link DPDPIdentityExtensionTenantMgtListener}), not
 * here.
 */
public final class EventNotificationTopicProvisioner {

    private static final Log LOG = LogFactory.getLog(EventNotificationTopicProvisioner.class);

    private EventNotificationTopicProvisioner() {

    }

    /**
     * Ensures every {@link DefaultTopic} exists for the given tenant, attempting all of them
     * even if one fails, then reporting every failure together.
     */
    public static void provisionSystemTopics(String orgId) throws StratosException {

        TopicService topicService = DPDPIdentityExtensionDataHolder.getInstance().getTopicService();
        String sanitizedOrgId = sanitize(orgId);
        List<String> failedTopics = new ArrayList<>();
        Exception firstFailure = null;

        for (DefaultTopic defaultTopic : DefaultTopic.values()) {
            try {
                topicService.ensureSystemTopic(orgId, defaultTopic.getName(), defaultTopic.getDescription());
                LOG.debug("Reconciled system topic '" + defaultTopic.getName()
                        + "' for tenant: " + sanitizedOrgId);
            } catch (Exception e) {
                LOG.error("Failed to reconcile system topic '" + defaultTopic.getName()
                        + "' for tenant: " + sanitizedOrgId, e);
                failedTopics.add(defaultTopic.getName());
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }

        if (!failedTopics.isEmpty()) {
            throw new StratosException("Failed to provision DPDP system topics " + failedTopics
                    + " for tenant: " + sanitizedOrgId, firstFailure);
        }
    }

    /**
     * Reconciles default system topics across every active tenant known to the tenant manager (plus the super
     * tenant) at server startup, including tenants with no rows in {@code WSO2DPDP_DB.TOPIC}, when both
     * {@code system_topics_auto_create_enabled} and {@code system_topics_verify_on_startup_enabled} are enabled.
     */
    public static void reconcileExistingTenantSystemTopics() {

        DPDPConfigurationService configService =
                DPDPIdentityExtensionDataHolder.getInstance().getConfigurationService();
        if (configService == null
                || !configService.isEventNotificationSystemTopicsAutoCreateEnabled()
                || !configService.isEventNotificationSystemTopicsVerifyOnStartupEnabled()) {
            LOG.debug("Skipping startup system topic verification across existing tenants.");
            return;
        }

        TopicService topicService = DPDPIdentityExtensionDataHolder.getInstance().getTopicService();
        if (topicService == null) {
            LOG.debug("TopicService is unavailable; skipping startup system topic verification.");
            return;
        }

        List<String> tenantDomains;
        try {
            tenantDomains = getActiveTenantDomains();
        } catch (Exception e) {
            LOG.error("Failed to list tenants; running promotion only, without per-tenant provisioning.", e);
            tenantDomains = Collections.emptyList();
        }

        topicService.reconcileSystemTopicsAcrossTenants(tenantDomains);
    }

    private static List<String> getActiveTenantDomains() throws UserStoreException {

        List<String> tenantDomains = new ArrayList<>();
        tenantDomains.add(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME);
        Tenant[] tenants = DPDPIdentityExtensionDataHolder.getInstance().getRealmService()
                .getTenantManager().getAllTenants();
        if (tenants != null) {
            for (Tenant tenant : tenants) {
                if (tenant != null && tenant.isActive() && tenant.getDomain() != null
                        && !MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenant.getDomain())) {
                    tenantDomains.add(tenant.getDomain());
                }
            }
        }
        return tenantDomains;
    }

    private static String sanitize(String value) {

        return value == null ? null : value.replaceAll("[\r\n]", "");
    }
}
