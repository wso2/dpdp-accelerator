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

package org.wso2.dpdp.accelerator.common.config;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.dpdp.accelerator.common.constant.DPDPCommonConstants;

import java.util.Map;
import java.util.Collections;
import java.util.Set;

public class DPDPConfigurationServiceImpl implements DPDPConfigurationService {

    private static final Log LOG = LogFactory.getLog(DPDPConfigurationServiceImpl.class);
    private final DPDPConfigParser configParser;

    public DPDPConfigurationServiceImpl() {
        this(true);
    }

    public DPDPConfigurationServiceImpl(boolean loadConfiguration) {
        DPDPConfigParser parser;
        if (!loadConfiguration) {
            parser = null;
        } else {
            try {
                parser = DPDPConfigParser.getInstance();
            } catch (RuntimeException e) {
                LOG.debug("DPDP accelerator configuration is unavailable.", e);
                parser = null;
            }
        }
        this.configParser = parser;
    }

    @Override
    public Map<String, Object> getConfigurations() {

        return configParser == null ? Collections.emptyMap() : configParser.getConfiguration();
    }

    @Override
    public int getJdbcConnectionVerificationTimeoutSeconds() {

        return configParser == null ? DPDPCommonConstants.DEFAULT_JDBC_CONNECTION_VERIFICATION_TIMEOUT_SECONDS
                : configParser.getJdbcConnectionVerificationTimeoutSeconds();
    }

    @Override
    public boolean isConsentPortalProvisioningEnabled() {

        return configParser == null || configParser.isConsentPortalProvisioningEnabled();
    }

    @Override
    public String getConsentPortalClientId() {

        return configParser == null ? "DPDP_CONSENT_PORTAL" : configParser.getConsentPortalClientId();
    }

    @Override
    public boolean isConsentApiInvokerProvisioningEnabled() {

        return configParser == null || configParser.isConsentApiInvokerProvisioningEnabled();
    }

    @Override
    public String getConsentApiInvokerClientId() {

        return configParser == null ? "DPDP_CONSENT_API_INVOKER" : configParser.getConsentApiInvokerClientId();
    }

    @Override
    public int getComplaintsStatutoryDuePeriodDays() {

        return configParser == null ? DPDPCommonConstants.DEFAULT_COMPLAINTS_STATUTORY_DUE_PERIOD_DAYS
                : configParser.getComplaintsStatutoryDuePeriodDays();
    }

    @Override
    public long getComplaintsAttachmentMaxSizeBytes() {

        return configParser == null ? DPDPCommonConstants.DEFAULT_COMPLAINTS_ATTACHMENT_MAX_SIZE_BYTES
                : configParser.getComplaintsAttachmentMaxSizeBytes();
    }

    @Override
    public int getComplaintsAttachmentMaxFilesPerUpload() {

        return configParser == null ? DPDPCommonConstants.DEFAULT_COMPLAINTS_ATTACHMENT_MAX_FILES_PER_UPLOAD
                : configParser.getComplaintsAttachmentMaxFilesPerUpload();
    }

    @Override
    public boolean isComplaintsEmailNotificationsEnabled() {

        return configParser == null ? DPDPCommonConstants.DEFAULT_COMPLAINTS_EMAIL_NOTIFICATIONS_ENABLED
                : configParser.isComplaintsEmailNotificationsEnabled();
    }

    @Override
    public int getEventNotificationThreadPoolSize() {

        return configParser == null ? DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_THREAD_POOL_SIZE
                : configParser.getEventNotificationThreadPoolSize();
    }

    @Override
    public long getEventNotificationBaseBackoffSeconds() {

        return configParser == null ? DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_BASE_BACKOFF_SECONDS
                : configParser.getEventNotificationBaseBackoffSeconds();
    }

    @Override
    public int getEventNotificationMaxRetries() {

        return configParser == null ? DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_MAX_RETRIES
                : configParser.getEventNotificationMaxRetries();
    }

    @Override
    public boolean isEventNotificationHttpCallbackUrlAllowed() {

        return configParser == null || configParser.isEventNotificationHttpCallbackUrlAllowed();
    }

    @Override
    public Set<Integer> getEventNotificationAllowedCallbackPorts() {

        return configParser == null ? DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_ALLOWED_CALLBACK_PORTS
                : configParser.getEventNotificationAllowedCallbackPorts();
    }

    @Override
    public boolean isEventNotificationPrivateNetworkCallbackTargetsAllowed() {

        return configParser != null && configParser.isEventNotificationPrivateNetworkCallbackTargetsAllowed();
    }

    @Override
    public int getEventNotificationDeliveryWorkerBatchSize() {

        return configParser == null ? DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_DELIVERY_WORKER_BATCH_SIZE
                : configParser.getEventNotificationDeliveryWorkerBatchSize();
    }

    @Override
    public int getEventNotificationDeliveryWorkerPollSeconds() {

        return configParser == null ? DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_DELIVERY_WORKER_POLL_SECONDS
                : configParser.getEventNotificationDeliveryWorkerPollSeconds();
    }

    @Override
    public int getEventNotificationStuckInFlightThresholdSeconds() {

        return configParser == null
                ? DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_STUCK_INFLIGHT_THRESHOLD_SECONDS
                : configParser.getEventNotificationStuckInFlightThresholdSeconds();
    }

    @Override
    public int getEventNotificationMaxVerificationResponseBodyBytes() {

        return configParser == null
                ? DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_MAX_VERIFICATION_RESPONSE_BODY_BYTES
                : configParser.getEventNotificationMaxVerificationResponseBodyBytes();
    }

    @Override
    public int getEventNotificationPendingSubscriptionRecoveryThresholdSeconds() {

        return configParser == null
                ? DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_PENDING_SUBSCRIPTION_RECOVERY_THRESHOLD_SECONDS
                : configParser.getEventNotificationPendingSubscriptionRecoveryThresholdSeconds();
    }

    @Override
    public int getEventNotificationBackgroundWorkerInitialDelaySeconds() {

        return configParser == null
                ? DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_BACKGROUND_WORKER_INITIAL_DELAY_SECONDS
                : configParser.getEventNotificationBackgroundWorkerInitialDelaySeconds();
    }

    @Override
    public int getEventNotificationPendingSubscriptionRecoveryIntervalSeconds() {

        return configParser == null
                ? DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_PENDING_SUBSCRIPTION_RECOVERY_INTERVAL_SECONDS
                : configParser.getEventNotificationPendingSubscriptionRecoveryIntervalSeconds();
    }

    @Override
    public int getEventNotificationPendingSubscriptionRecoveryBatchSize() {

        return configParser == null
                ? DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_PENDING_SUBSCRIPTION_RECOVERY_BATCH_SIZE
                : configParser.getEventNotificationPendingSubscriptionRecoveryBatchSize();
    }

    @Override
    public int getEventNotificationWorkerShutdownTimeoutSeconds() {

        return configParser == null ? DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_WORKER_SHUTDOWN_TIMEOUT_SECONDS
                : configParser.getEventNotificationWorkerShutdownTimeoutSeconds();
    }

    @Override
    public boolean isEventNotificationSystemTopicsAutoCreateEnabled() {

        return configParser == null || configParser.isEventNotificationSystemTopicsAutoCreateEnabled();
    }

    @Override
    public boolean isEventNotificationLifecycleEventsPublishingEnabled() {

        return configParser == null || configParser.isEventNotificationLifecycleEventsPublishingEnabled();
    }

    @Override
    public boolean isEventNotificationPayloadSigningEnabled() {

        return configParser == null ? DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_PAYLOAD_SIGNING_ENABLED
                : configParser.isEventNotificationPayloadSigningEnabled();
    }

    @Override
    public String getEventNotificationPayloadSigningAudience() {

        return configParser == null ? DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_PAYLOAD_SIGNING_AUDIENCE
                : configParser.getEventNotificationPayloadSigningAudience();
    }

    @Override
    public boolean isEventNotificationPollingDefaultReturnImmediately() {

        return configParser == null
                ? DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_POLLING_RETURN_IMMEDIATELY
                : configParser.isEventNotificationPollingDefaultReturnImmediately();
    }

    @Override
    public int getEventNotificationPollingDefaultMaxEvents() {

        return configParser == null ? DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_POLLING_MAX_EVENTS
                : configParser.getEventNotificationPollingDefaultMaxEvents();
    }

    @Override
    public int getEventNotificationPollingMaxEventsLimit() {

        return configParser == null ? DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_POLLING_MAX_EVENTS_LIMIT
                : configParser.getEventNotificationPollingMaxEventsLimit();
    }

    @Override
    public boolean isEventNotificationPollingRequestHmacValidationEnabled() {

        return configParser == null
                ? DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_POLLING_REQUEST_HMAC_VALIDATION_ENABLED
                : configParser.isEventNotificationPollingRequestHmacValidationEnabled();
    }

    @Override
    public boolean isConsentHistoryEnabled() {

        return configParser == null || configParser.isConsentHistoryEnabled();
    }

    @Override
    public boolean isConsentHistorySnapshotEnabled() {

        return configParser == null || configParser.isConsentHistorySnapshotEnabled();
    }

    @Override
    public boolean isConsentExpiryEnabled() {

        return configParser == null || configParser.isConsentExpiryEnabled();
    }

    @Override
    public String getConsentExpiryCronValue() {

        return configParser == null ? DPDPCommonConstants.DEFAULT_CONSENT_EXPIRY_CRON_VALUE
                : configParser.getConsentExpiryCronValue();
    }

    @Override
    public int getConsentExpiryBatchSize() {

        return configParser == null ? DPDPCommonConstants.DEFAULT_CONSENT_EXPIRY_BATCH_SIZE
                : configParser.getConsentExpiryBatchSize();
    }
}
