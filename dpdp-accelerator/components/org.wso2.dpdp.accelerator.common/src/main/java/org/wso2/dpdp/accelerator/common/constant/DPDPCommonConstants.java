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

package org.wso2.dpdp.accelerator.common.constant;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Config file structure and key constants for {@code dpdp-accelerator.xml}.
 */
public final class DPDPCommonConstants {

    public static final String CONFIG_FILE_NAME = "dpdp-accelerator.xml";

    public static final String JDBC_PERSISTENCE_MANAGER_DATA_SOURCE_NAME = "JDBCPersistenceManager.DataSource.Name";
    public static final String JDBC_PERSISTENCE_MANAGER_CONNECTION_VERIFICATION_TIMEOUT_SECONDS =
            "JDBCPersistenceManager.ConnectionVerificationTimeout";
    public static final String JDBC_ENV_CONTEXT_PREFIX = "java:comp/env/";
    public static final String DEFAULT_JDBC_DPDP_DATASOURCE_NAME = "jdbc/WSO2DPDP_DB";
    public static final int DEFAULT_JDBC_CONNECTION_VERIFICATION_TIMEOUT_SECONDS = 1;

    public static final String CONSENT_PORTAL_AUTO_PROVISIONING_ENABLED = "ConsentPortal.AutoProvisioningEnabled";
    public static final String CONSENT_PORTAL_CLIENT_ID = "ConsentPortal.ClientId";

    public static final String CONSENT_API_INVOKER_AUTO_PROVISIONING_ENABLED =
            "ConsentApiInvoker.AutoProvisioningEnabled";
    public static final String CONSENT_API_INVOKER_CLIENT_ID = "ConsentApiInvoker.ClientId";

    public static final String COMPLAINTS_STATUTORY_DUE_PERIOD_DAYS = "Complaints.StatutoryDuePeriodDays";
    public static final int DEFAULT_COMPLAINTS_STATUTORY_DUE_PERIOD_DAYS = 90;

    public static final String COMPLAINTS_ATTACHMENT_MAX_SIZE_BYTES = "Complaints.AttachmentMaxSizeBytes";
    public static final long DEFAULT_COMPLAINTS_ATTACHMENT_MAX_SIZE_BYTES = 10L * 1024 * 1024;

    public static final String COMPLAINTS_ATTACHMENT_MAX_FILES_PER_UPLOAD = "Complaints.AttachmentMaxFilesPerUpload";
    public static final int DEFAULT_COMPLAINTS_ATTACHMENT_MAX_FILES_PER_UPLOAD = 5;

    public static final String COMPLAINTS_EMAIL_NOTIFICATIONS_ENABLED = "Complaints.EmailNotificationsEnabled";
    public static final boolean DEFAULT_COMPLAINTS_EMAIL_NOTIFICATIONS_ENABLED = false;

    public static final String EVENT_NOTIFICATIONS_THREAD_POOL_SIZE = "EventNotifications.ThreadPoolSize";
    public static final String EVENT_NOTIFICATIONS_BASE_BACKOFF_SECONDS = "EventNotifications.BaseBackoffSeconds";
    public static final String EVENT_NOTIFICATIONS_MAX_RETRIES = "EventNotifications.MaxRetries";
    public static final String EVENT_NOTIFICATIONS_ALLOW_HTTP_CALLBACK_URL =
            "EventNotifications.AllowHttpCallbackUrl";
    public static final String EVENT_NOTIFICATIONS_ALLOWED_CALLBACK_PORTS =
            "EventNotifications.AllowedCallbackPorts";
    public static final String EVENT_NOTIFICATIONS_ALLOW_PRIVATE_NETWORK_CALLBACK_TARGETS =
            "EventNotifications.AllowPrivateNetworkCallbackTargets";
    public static final String EVENT_NOTIFICATIONS_DELIVERY_WORKER_BATCH_SIZE =
            "EventNotifications.DeliveryWorkerBatchSize";
    public static final String EVENT_NOTIFICATIONS_DELIVERY_WORKER_POLL_SECONDS =
            "EventNotifications.DeliveryWorkerPollSeconds";
    public static final String EVENT_NOTIFICATIONS_STUCK_INFLIGHT_THRESHOLD_SECONDS =
            "EventNotifications.StuckInFlightThresholdSeconds";
    public static final String EVENT_NOTIFICATIONS_MAX_VERIFICATION_RESPONSE_BODY_BYTES =
            "EventNotifications.MaxVerificationResponseBodyBytes";
    public static final String EVENT_NOTIFICATIONS_PENDING_SUBSCRIPTION_RECOVERY_THRESHOLD_SECONDS =
            "EventNotifications.PendingSubscriptionRecoveryThresholdSeconds";
    public static final String EVENT_NOTIFICATIONS_BACKGROUND_WORKER_INITIAL_DELAY_SECONDS =
            "EventNotifications.BackgroundWorkerInitialDelaySeconds";
    public static final String EVENT_NOTIFICATIONS_PENDING_SUBSCRIPTION_RECOVERY_INTERVAL_SECONDS =
            "EventNotifications.PendingSubscriptionRecoveryIntervalSeconds";
    public static final String EVENT_NOTIFICATIONS_PENDING_SUBSCRIPTION_RECOVERY_BATCH_SIZE =
            "EventNotifications.PendingSubscriptionRecoveryBatchSize";
    public static final String EVENT_NOTIFICATIONS_WORKER_SHUTDOWN_TIMEOUT_SECONDS =
            "EventNotifications.WorkerShutdownTimeoutSeconds";
    public static final String EVENT_NOTIFICATIONS_SYSTEM_TOPICS_AUTO_CREATE_ENABLED =
            "EventNotifications.SystemTopics.AutoCreateEnabled";
    public static final String EVENT_NOTIFICATIONS_PAYLOAD_SIGNING_ENABLED =
            "EventNotifications.PayloadSigning.Enabled";
    public static final String EVENT_NOTIFICATIONS_PAYLOAD_SIGNING_AUDIENCE =
            "EventNotifications.PayloadSigning.Audience";
    public static final String EVENT_NOTIFICATIONS_LIFECYCLE_EVENTS_PUBLISHING_ENABLED =
            "EventNotifications.LifecycleEvents.PublishingEnabled";
    public static final String EVENT_NOTIFICATIONS_POLLING_DEFAULT_RETURN_IMMEDIATELY =
            "EventNotifications.Polling.DefaultReturnImmediately";
    public static final String EVENT_NOTIFICATIONS_POLLING_DEFAULT_MAX_EVENTS =
            "EventNotifications.Polling.DefaultMaxEvents";
    public static final String EVENT_NOTIFICATIONS_POLLING_MAX_EVENTS_LIMIT =
            "EventNotifications.Polling.MaxEventsLimit";
    public static final String EVENT_NOTIFICATIONS_POLLING_REQUEST_HMAC_VALIDATION_ENABLED =
            "EventNotifications.Polling.RequestHmacValidationEnabled";

    public static final int DEFAULT_EVENT_NOTIFICATIONS_THREAD_POOL_SIZE = 4;
    public static final long DEFAULT_EVENT_NOTIFICATIONS_BASE_BACKOFF_SECONDS = 5L;
    public static final int DEFAULT_EVENT_NOTIFICATIONS_MAX_RETRIES = 5;
    public static final boolean DEFAULT_EVENT_NOTIFICATIONS_ALLOW_HTTP_CALLBACK_URL = true;
    public static final Set<Integer> DEFAULT_EVENT_NOTIFICATIONS_ALLOWED_CALLBACK_PORTS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(-1, 80, 443, 8443)));
    public static final boolean DEFAULT_EVENT_NOTIFICATIONS_ALLOW_PRIVATE_NETWORK_CALLBACK_TARGETS = false;
    public static final int DEFAULT_EVENT_NOTIFICATIONS_DELIVERY_WORKER_BATCH_SIZE = 50;
    public static final int DEFAULT_EVENT_NOTIFICATIONS_DELIVERY_WORKER_POLL_SECONDS = 5;
    public static final int DEFAULT_EVENT_NOTIFICATIONS_STUCK_INFLIGHT_THRESHOLD_SECONDS = 10;
    public static final int DEFAULT_EVENT_NOTIFICATIONS_MAX_VERIFICATION_RESPONSE_BODY_BYTES = 4096;
    public static final int DEFAULT_EVENT_NOTIFICATIONS_PENDING_SUBSCRIPTION_RECOVERY_THRESHOLD_SECONDS = 60;
    public static final int DEFAULT_EVENT_NOTIFICATIONS_BACKGROUND_WORKER_INITIAL_DELAY_SECONDS = 10;
    public static final int DEFAULT_EVENT_NOTIFICATIONS_PENDING_SUBSCRIPTION_RECOVERY_INTERVAL_SECONDS = 30;
    public static final int DEFAULT_EVENT_NOTIFICATIONS_PENDING_SUBSCRIPTION_RECOVERY_BATCH_SIZE = 20;
    public static final int DEFAULT_EVENT_NOTIFICATIONS_WORKER_SHUTDOWN_TIMEOUT_SECONDS = 5;
    public static final boolean DEFAULT_EVENT_NOTIFICATIONS_PAYLOAD_SIGNING_ENABLED = true;
    public static final String DEFAULT_EVENT_NOTIFICATIONS_PAYLOAD_SIGNING_AUDIENCE =
            "dpdp-event-notifications";
    public static final boolean DEFAULT_EVENT_NOTIFICATIONS_LIFECYCLE_EVENTS_PUBLISHING_ENABLED = true;
    public static final boolean DEFAULT_EVENT_NOTIFICATIONS_POLLING_RETURN_IMMEDIATELY = true;
    public static final int DEFAULT_EVENT_NOTIFICATIONS_POLLING_MAX_EVENTS = 20;
    public static final int DEFAULT_EVENT_NOTIFICATIONS_POLLING_MAX_EVENTS_LIMIT = 100;
    public static final boolean DEFAULT_EVENT_NOTIFICATIONS_POLLING_REQUEST_HMAC_VALIDATION_ENABLED = false;

    public static final String CONSENT_HISTORY_ENABLED = "ConsentHistory.Enabled";
    public static final String CONSENT_HISTORY_SNAPSHOT_ENABLED = "ConsentHistory.SnapshotEnabled";

    public static final String CONSENT_EXPIRY_ENABLED = "ConsentExpiry.Enabled";
    public static final String CONSENT_EXPIRY_CRON_VALUE = "ConsentExpiry.CronValue";
    public static final String CONSENT_EXPIRY_BATCH_SIZE = "ConsentExpiry.BatchSize";
    public static final String DEFAULT_CONSENT_EXPIRY_CRON_VALUE = "0 0 0 * * ?";
    public static final int DEFAULT_CONSENT_EXPIRY_BATCH_SIZE = 100;

    private DPDPCommonConstants() {

    }
}
