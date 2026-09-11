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

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMException;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.utils.CarbonUtils;
import org.wso2.dpdp.accelerator.common.constant.DPDPCommonConstants;
import org.wso2.dpdp.accelerator.common.exception.DPDPCommonRuntimeException;
import org.wso2.securevault.SecretResolver;
import org.wso2.securevault.SecretResolverFactory;
import org.wso2.securevault.commons.MiscellaneousUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;

import javax.xml.stream.XMLStreamException;

/**
 * Config parser for {@code dpdp-accelerator.xml}: reads the file once into a flat,
 * dot-joined-key {@code Map<String, Object>}, then exposes typed getters with defaults over it.
 * A value can be stored in the Secure Vault instead of in plain text, using the usual
 * {@code svns:secretAlias="..."} attribute on its element.
 */
public final class DPDPConfigParser {

    private static final Log LOG = LogFactory.getLog(DPDPConfigParser.class);
    private static final Object LOCK = new Object();
    private static DPDPConfigParser parser;

    private final Map<String, Object> configuration = new HashMap<>();
    private SecretResolver secretResolver;

    private DPDPConfigParser() {

        buildConfiguration();
        LOG.debug("Loaded " + DPDPCommonConstants.CONFIG_FILE_NAME + " with " + configuration.size()
                + " configured value(s).");
    }

    public static DPDPConfigParser getInstance() {

        synchronized (LOCK) {
            if (parser == null) {
                parser = new DPDPConfigParser();
            }
        }
        return parser;
    }

    public Map<String, Object> getConfiguration() {

        return Collections.unmodifiableMap(configuration);
    }

    private void buildConfiguration() {

        File configXml = new File(CarbonUtils.getCarbonConfigDirPath(), DPDPCommonConstants.CONFIG_FILE_NAME);
        try (InputStream inStream = openConfigFile(configXml)) {
            StAXOMBuilder builder = new StAXOMBuilder(inStream);
            OMElement rootElement = builder.getDocumentElement();
            secretResolver = SecretResolverFactory.create(rootElement, true);
            readChildElements(rootElement, new Stack<>());
        } catch (IOException | XMLStreamException | OMException e) {
            LOG.error("Error occurred while building configuration from " + DPDPCommonConstants.CONFIG_FILE_NAME
                    + ". If this accelerator was upgraded in place, re-run bin/merge.sh so the template that "
                    + "renders this file is present.", e);
            throw new DPDPCommonRuntimeException("Error occurred while building configuration from "
                    + DPDPCommonConstants.CONFIG_FILE_NAME, e);
        }
    }

    private InputStream openConfigFile(File configXml) throws IOException {

        if (!configXml.exists()) {
            throw new FileNotFoundException("DPDP accelerator configuration not found at: " + configXml);
        }
        return Files.newInputStream(configXml.toPath());
    }

    private void readChildElements(OMElement parent, Stack<String> nameStack) {

        for (Iterator<OMElement> children = parent.getChildElements(); children.hasNext();) {
            OMElement element = children.next();
            nameStack.push(element.getLocalName());
            String text = element.getText();
            if (text != null && !text.trim().isEmpty()) {
                text = text.trim();
                if (secretResolver != null && secretResolver.isInitialized()) {
                    // A no-op for elements without a secretAlias attribute - returns the
                    // plain text unchanged.
                    text = MiscellaneousUtil.resolve(element, secretResolver);
                }
                configuration.put(String.join(".", nameStack), text);
            }
            readChildElements(element, nameStack);
            nameStack.pop();
        }
    }

    private Optional<String> getConfigurationAsString(String key) {

        return Optional.ofNullable((String) configuration.get(key));
    }

    private int getInt(String configKey, int defaultValue) {

        return getConfigurationAsString(configKey).map(value -> parseInt(configKey, value)).orElse(defaultValue);
    }

    private int parseInt(String configKey, String value) {

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid numeric DPDP configuration: " + configKey, e);
        }
    }

    private int getPositiveInt(String configKey, int defaultValue) {

        int value = getInt(configKey, defaultValue);
        if (value <= 0) {
            throw new IllegalStateException("DPDP configuration must be positive: " + configKey);
        }
        return value;
    }

    private int getNonNegativeInt(String configKey, int defaultValue) {

        int value = getInt(configKey, defaultValue);
        if (value < 0) {
            throw new IllegalStateException("DPDP configuration cannot be negative: " + configKey);
        }
        return value;
    }

    private long getNonNegativeLong(String configKey, long defaultValue) {

        long value = getConfigurationAsString(configKey).map(text -> {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Invalid numeric DPDP configuration: " + configKey, e);
            }
        }).orElse(defaultValue);
        if (value < 0) {
            throw new IllegalStateException("DPDP configuration cannot be negative: " + configKey);
        }
        return value;
    }

    private boolean getValidatedBoolean(String configKey, boolean defaultValue) {

        return getConfigurationAsString(configKey).map(value -> {
            if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                throw new IllegalStateException("Invalid boolean DPDP configuration: " + configKey);
            }
            return Boolean.parseBoolean(value);
        }).orElse(defaultValue);
    }

    private Set<Integer> parseIntSet(String configKey, String rawValue) {

        Set<Integer> values = new HashSet<>();
        for (String part : rawValue.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                values.add(Integer.parseInt(trimmed));
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Invalid numeric DPDP configuration: " + configKey, e);
            }
        }
        return values;
    }

    public String getJdbcDataSourceName() {

        return getConfigurationAsString(DPDPCommonConstants.JDBC_PERSISTENCE_MANAGER_DATA_SOURCE_NAME)
                .orElse(DPDPCommonConstants.DEFAULT_JDBC_DPDP_DATASOURCE_NAME);
    }

    public int getJdbcConnectionVerificationTimeoutSeconds() {

        return getPositiveInt(DPDPCommonConstants.JDBC_PERSISTENCE_MANAGER_CONNECTION_VERIFICATION_TIMEOUT_SECONDS,
                DPDPCommonConstants.DEFAULT_JDBC_CONNECTION_VERIFICATION_TIMEOUT_SECONDS);
    }

    public boolean isConsentPortalProvisioningEnabled() {

        return getConfigurationAsString(DPDPCommonConstants.CONSENT_PORTAL_AUTO_PROVISIONING_ENABLED)
                .map(Boolean::parseBoolean).orElse(true);
    }

    public String getConsentPortalClientId() {

        return getConfigurationAsString(DPDPCommonConstants.CONSENT_PORTAL_CLIENT_ID)
                .orElse("DPDP_CONSENT_PORTAL");
    }

    public boolean isConsentApiInvokerProvisioningEnabled() {

        return getConfigurationAsString(DPDPCommonConstants.CONSENT_API_INVOKER_AUTO_PROVISIONING_ENABLED)
                .map(Boolean::parseBoolean).orElse(true);
    }

    public String getConsentApiInvokerClientId() {

        return getConfigurationAsString(DPDPCommonConstants.CONSENT_API_INVOKER_CLIENT_ID)
                .orElse("DPDP_CONSENT_API_INVOKER");
    }

    public int getComplaintsStatutoryDuePeriodDays() {

        return getPositiveInt(DPDPCommonConstants.COMPLAINTS_STATUTORY_DUE_PERIOD_DAYS,
                DPDPCommonConstants.DEFAULT_COMPLAINTS_STATUTORY_DUE_PERIOD_DAYS);
    }

    public long getComplaintsAttachmentMaxSizeBytes() {

        return getNonNegativeLong(DPDPCommonConstants.COMPLAINTS_ATTACHMENT_MAX_SIZE_BYTES,
                DPDPCommonConstants.DEFAULT_COMPLAINTS_ATTACHMENT_MAX_SIZE_BYTES);
    }

    public int getComplaintsAttachmentMaxFilesPerUpload() {

        return getPositiveInt(DPDPCommonConstants.COMPLAINTS_ATTACHMENT_MAX_FILES_PER_UPLOAD,
                DPDPCommonConstants.DEFAULT_COMPLAINTS_ATTACHMENT_MAX_FILES_PER_UPLOAD);
    }

    public boolean isComplaintsEmailNotificationsEnabled() {

        return getValidatedBoolean(DPDPCommonConstants.COMPLAINTS_EMAIL_NOTIFICATIONS_ENABLED,
                DPDPCommonConstants.DEFAULT_COMPLAINTS_EMAIL_NOTIFICATIONS_ENABLED);
    }

    public boolean isConsentHistoryEnabled() {

        return getConfigurationAsString(DPDPCommonConstants.CONSENT_HISTORY_ENABLED)
                .map(Boolean::parseBoolean).orElse(true);
    }

    public boolean isConsentHistorySnapshotEnabled() {

        return getConfigurationAsString(DPDPCommonConstants.CONSENT_HISTORY_SNAPSHOT_ENABLED)
                .map(Boolean::parseBoolean).orElse(true);
    }

    public boolean isConsentExpiryEnabled() {

        return getConfigurationAsString(DPDPCommonConstants.CONSENT_EXPIRY_ENABLED)
                .map(Boolean::parseBoolean).orElse(true);
    }

    public boolean isEventNotificationSystemTopicsAutoCreateEnabled() {

        return getConfigurationAsString(DPDPCommonConstants.EVENT_NOTIFICATIONS_SYSTEM_TOPICS_AUTO_CREATE_ENABLED)
                .map(Boolean::parseBoolean).orElse(true);
    }

    public boolean isEventNotificationLifecycleEventsPublishingEnabled() {

        return getConfigurationAsString(DPDPCommonConstants.EVENT_NOTIFICATIONS_LIFECYCLE_EVENTS_PUBLISHING_ENABLED)
                .map(Boolean::parseBoolean)
                .orElse(DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_LIFECYCLE_EVENTS_PUBLISHING_ENABLED);
    }

    public boolean isEventNotificationPayloadSigningEnabled() {

        return getValidatedBoolean(DPDPCommonConstants.EVENT_NOTIFICATIONS_PAYLOAD_SIGNING_ENABLED,
                DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_PAYLOAD_SIGNING_ENABLED);
    }

    public String getEventNotificationPayloadSigningAudience() {

        return getConfigurationAsString(DPDPCommonConstants.EVENT_NOTIFICATIONS_PAYLOAD_SIGNING_AUDIENCE)
                .filter(value -> !value.trim().isEmpty())
                .orElse(DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_PAYLOAD_SIGNING_AUDIENCE);
    }

    public boolean isEventNotificationPollingDefaultReturnImmediately() {

        return getValidatedBoolean(DPDPCommonConstants.EVENT_NOTIFICATIONS_POLLING_DEFAULT_RETURN_IMMEDIATELY,
                DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_POLLING_RETURN_IMMEDIATELY);
    }

    public int getEventNotificationPollingDefaultMaxEvents() {

        int defaultMaxEvents = getNonNegativeInt(
                DPDPCommonConstants.EVENT_NOTIFICATIONS_POLLING_DEFAULT_MAX_EVENTS,
                DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_POLLING_MAX_EVENTS);
        if (defaultMaxEvents > getEventNotificationPollingMaxEventsLimit()) {
            throw new IllegalStateException("Default polling max events cannot exceed the polling max events limit.");
        }
        return defaultMaxEvents;
    }

    public int getEventNotificationPollingMaxEventsLimit() {

        return getPositiveInt(DPDPCommonConstants.EVENT_NOTIFICATIONS_POLLING_MAX_EVENTS_LIMIT,
                DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_POLLING_MAX_EVENTS_LIMIT);
    }

    public boolean isEventNotificationPollingRequestHmacValidationEnabled() {

        return getValidatedBoolean(
                DPDPCommonConstants.EVENT_NOTIFICATIONS_POLLING_REQUEST_HMAC_VALIDATION_ENABLED,
                DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_POLLING_REQUEST_HMAC_VALIDATION_ENABLED);
    }

    public int getEventNotificationThreadPoolSize() {

        return getPositiveInt(DPDPCommonConstants.EVENT_NOTIFICATIONS_THREAD_POOL_SIZE,
                DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_THREAD_POOL_SIZE);
    }

    public long getEventNotificationBaseBackoffSeconds() {

        return getNonNegativeLong(DPDPCommonConstants.EVENT_NOTIFICATIONS_BASE_BACKOFF_SECONDS,
                DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_BASE_BACKOFF_SECONDS);
    }

    public int getEventNotificationMaxRetries() {

        return getNonNegativeInt(DPDPCommonConstants.EVENT_NOTIFICATIONS_MAX_RETRIES,
                DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_MAX_RETRIES);
    }

    public boolean isEventNotificationHttpCallbackUrlAllowed() {

        return getValidatedBoolean(DPDPCommonConstants.EVENT_NOTIFICATIONS_ALLOW_HTTP_CALLBACK_URL,
                DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_ALLOW_HTTP_CALLBACK_URL);
    }

    public Set<Integer> getEventNotificationAllowedCallbackPorts() {

        return getConfigurationAsString(DPDPCommonConstants.EVENT_NOTIFICATIONS_ALLOWED_CALLBACK_PORTS)
                .map(value -> parseIntSet(DPDPCommonConstants.EVENT_NOTIFICATIONS_ALLOWED_CALLBACK_PORTS, value))
                .orElse(DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_ALLOWED_CALLBACK_PORTS);
    }

    public boolean isEventNotificationPrivateNetworkCallbackTargetsAllowed() {

        return getValidatedBoolean(DPDPCommonConstants.EVENT_NOTIFICATIONS_ALLOW_PRIVATE_NETWORK_CALLBACK_TARGETS,
                DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_ALLOW_PRIVATE_NETWORK_CALLBACK_TARGETS);
    }

    public int getEventNotificationDeliveryWorkerBatchSize() {

        return getPositiveInt(DPDPCommonConstants.EVENT_NOTIFICATIONS_DELIVERY_WORKER_BATCH_SIZE,
                DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_DELIVERY_WORKER_BATCH_SIZE);
    }

    public int getEventNotificationDeliveryWorkerPollSeconds() {

        return getPositiveInt(DPDPCommonConstants.EVENT_NOTIFICATIONS_DELIVERY_WORKER_POLL_SECONDS,
                DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_DELIVERY_WORKER_POLL_SECONDS);
    }

    public int getEventNotificationStuckInFlightThresholdSeconds() {

        return getNonNegativeInt(DPDPCommonConstants.EVENT_NOTIFICATIONS_STUCK_INFLIGHT_THRESHOLD_SECONDS,
                DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_STUCK_INFLIGHT_THRESHOLD_SECONDS);
    }

    public int getEventNotificationMaxVerificationResponseBodyBytes() {

        return getPositiveInt(DPDPCommonConstants.EVENT_NOTIFICATIONS_MAX_VERIFICATION_RESPONSE_BODY_BYTES,
                DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_MAX_VERIFICATION_RESPONSE_BODY_BYTES);
    }

    public int getEventNotificationPendingSubscriptionRecoveryThresholdSeconds() {

        return getNonNegativeInt(
                DPDPCommonConstants.EVENT_NOTIFICATIONS_PENDING_SUBSCRIPTION_RECOVERY_THRESHOLD_SECONDS,
                DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_PENDING_SUBSCRIPTION_RECOVERY_THRESHOLD_SECONDS);
    }

    public int getEventNotificationBackgroundWorkerInitialDelaySeconds() {

        return getNonNegativeInt(DPDPCommonConstants.EVENT_NOTIFICATIONS_BACKGROUND_WORKER_INITIAL_DELAY_SECONDS,
                DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_BACKGROUND_WORKER_INITIAL_DELAY_SECONDS);
    }

    public int getEventNotificationPendingSubscriptionRecoveryIntervalSeconds() {

        return getPositiveInt(
                DPDPCommonConstants.EVENT_NOTIFICATIONS_PENDING_SUBSCRIPTION_RECOVERY_INTERVAL_SECONDS,
                DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_PENDING_SUBSCRIPTION_RECOVERY_INTERVAL_SECONDS);
    }

    public int getEventNotificationPendingSubscriptionRecoveryBatchSize() {

        return getPositiveInt(DPDPCommonConstants.EVENT_NOTIFICATIONS_PENDING_SUBSCRIPTION_RECOVERY_BATCH_SIZE,
                DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_PENDING_SUBSCRIPTION_RECOVERY_BATCH_SIZE);
    }

    public int getEventNotificationWorkerShutdownTimeoutSeconds() {

        return getPositiveInt(DPDPCommonConstants.EVENT_NOTIFICATIONS_WORKER_SHUTDOWN_TIMEOUT_SECONDS,
                DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_WORKER_SHUTDOWN_TIMEOUT_SECONDS);
    }

    public String getConsentExpiryCronValue() {

        return getConfigurationAsString(DPDPCommonConstants.CONSENT_EXPIRY_CRON_VALUE)
                .orElse(DPDPCommonConstants.DEFAULT_CONSENT_EXPIRY_CRON_VALUE);
    }

    public int getConsentExpiryBatchSize() {

        return getConfigurationAsString(DPDPCommonConstants.CONSENT_EXPIRY_BATCH_SIZE)
                .map(Integer::parseInt).orElse(DPDPCommonConstants.DEFAULT_CONSENT_EXPIRY_BATCH_SIZE);
    }
}
