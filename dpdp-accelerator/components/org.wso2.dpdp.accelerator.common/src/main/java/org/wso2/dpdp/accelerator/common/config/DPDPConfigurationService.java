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

import java.util.Map;
import java.util.Set;

/**
 * Exposes {@code dpdp-accelerator.xml} configuration as an OSGi service, so other bundles
 * consume it via {@code @Reference} injection instead of a static-singleton import.
 */
public interface DPDPConfigurationService {

    Map<String, Object> getConfigurations();

    int getJdbcConnectionVerificationTimeoutSeconds();

    boolean isConsentPortalProvisioningEnabled();

    String getConsentPortalClientId();

    boolean isConsentApiInvokerProvisioningEnabled();

    String getConsentApiInvokerClientId();

    int getComplaintsStatutoryDuePeriodDays();

    long getComplaintsAttachmentMaxSizeBytes();

    int getComplaintsAttachmentMaxFilesPerUpload();

    boolean isComplaintsEmailNotificationsEnabled();

    int getEventNotificationThreadPoolSize();

    long getEventNotificationBaseBackoffSeconds();

    int getEventNotificationMaxRetries();

    boolean isEventNotificationHttpCallbackUrlAllowed();

    Set<Integer> getEventNotificationAllowedCallbackPorts();

    boolean isEventNotificationPrivateNetworkCallbackTargetsAllowed();

    int getEventNotificationDeliveryWorkerBatchSize();

    int getEventNotificationDeliveryWorkerPollSeconds();

    int getEventNotificationStuckInFlightThresholdSeconds();

    int getEventNotificationMaxVerificationResponseBodyBytes();

    int getEventNotificationPendingSubscriptionRecoveryThresholdSeconds();

    int getEventNotificationBackgroundWorkerInitialDelaySeconds();

    int getEventNotificationPendingSubscriptionRecoveryIntervalSeconds();

    int getEventNotificationPendingSubscriptionRecoveryBatchSize();

    int getEventNotificationWorkerShutdownTimeoutSeconds();

    boolean isEventNotificationSystemTopicsAutoCreateEnabled();

    boolean isEventNotificationLifecycleEventsPublishingEnabled();

    boolean isEventNotificationPayloadSigningEnabled();

    String getEventNotificationPayloadSigningAudience();

    boolean isEventNotificationPollingDefaultReturnImmediately();

    int getEventNotificationPollingDefaultMaxEvents();

    int getEventNotificationPollingMaxEventsLimit();

    boolean isEventNotificationPollingRequestHmacValidationEnabled();

    boolean isConsentHistoryEnabled();

    boolean isConsentHistorySnapshotEnabled();

    boolean isConsentExpiryEnabled();

    String getConsentExpiryCronValue();

    int getConsentExpiryBatchSize();
}
