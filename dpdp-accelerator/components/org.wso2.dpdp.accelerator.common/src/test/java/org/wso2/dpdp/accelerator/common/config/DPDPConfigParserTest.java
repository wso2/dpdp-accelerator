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

import org.wso2.dpdp.accelerator.common.constant.DPDPCommonConstants;
import org.wso2.dpdp.accelerator.common.test.CarbonTestEnvironment;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Writes a real {@code dpdp-accelerator.xml} to a temp "carbon config dir" and parses it for
 * real - {@link DPDPConfigParser} is a singleton read once per JVM, so this class exercises
 * both the configured-value and default-fallback paths from the same instance rather than
 * trying to re-initialize it under different content.
 */
public class DPDPConfigParserTest {

    private static final String CUSTOM_CLIENT_ID = "CUSTOM_TEST_CLIENT_ID";
    private static final int CUSTOM_STATUTORY_DUE_PERIOD_DAYS = 45;
    private static final long CUSTOM_ATTACHMENT_MAX_SIZE_BYTES = 2048L;
    private static final int CUSTOM_ATTACHMENT_MAX_FILES_PER_UPLOAD = 3;
    private static final String CUSTOM_DATASOURCE_NAME = "jdbc/CustomDPDPDataSource";
    private static final Set<Integer> CUSTOM_ALLOWED_CALLBACK_PORTS =
            new HashSet<>(Arrays.asList(80, 9443));

    @BeforeClass
    public void writeConfigFileAndSetCarbonConfigDir() throws IOException {

        Path configDir = Files.createTempDirectory("dpdp-config-test");
        Path configFile = configDir.resolve("dpdp-accelerator.xml");
        Files.write(configFile, ("<DPDPAccelerator xmlns=\"http://wso2.org/projects/carbon/dpdp-accelerator.xml\">"
                + "<JDBCPersistenceManager>"
                + "<DataSource><Name>" + CUSTOM_DATASOURCE_NAME + "</Name></DataSource>"
                + "<ConnectionVerificationTimeout>3</ConnectionVerificationTimeout>"
                + "</JDBCPersistenceManager>"
                + "<ConsentPortal>"
                + "<ClientId>" + CUSTOM_CLIENT_ID + "</ClientId>"
                + "</ConsentPortal>"
                + "<Complaints>"
                + "<StatutoryDuePeriodDays>" + CUSTOM_STATUTORY_DUE_PERIOD_DAYS + "</StatutoryDuePeriodDays>"
                + "<AttachmentMaxSizeBytes>" + CUSTOM_ATTACHMENT_MAX_SIZE_BYTES + "</AttachmentMaxSizeBytes>"
                + "<AttachmentMaxFilesPerUpload>" + CUSTOM_ATTACHMENT_MAX_FILES_PER_UPLOAD
                + "</AttachmentMaxFilesPerUpload>"
                + "</Complaints>"
                + "<EventNotifications>"
                + "<ThreadPoolSize>8</ThreadPoolSize>"
                + "<BaseBackoffSeconds>12</BaseBackoffSeconds>"
                + "<MaxRetries>7</MaxRetries>"
                + "<AllowHttpCallbackUrl>false</AllowHttpCallbackUrl>"
                + "<AllowedCallbackPorts>80,9443</AllowedCallbackPorts>"
                + "<AllowPrivateNetworkCallbackTargets>true</AllowPrivateNetworkCallbackTargets>"
                + "<DeliveryWorkerBatchSize>25</DeliveryWorkerBatchSize>"
                + "<DeliveryWorkerPollSeconds>9</DeliveryWorkerPollSeconds>"
                + "<StuckInFlightThresholdSeconds>15</StuckInFlightThresholdSeconds>"
                + "<MaxVerificationResponseBodyBytes>8192</MaxVerificationResponseBodyBytes>"
                + "<PendingSubscriptionRecoveryThresholdSeconds>90</PendingSubscriptionRecoveryThresholdSeconds>"
                + "<BackgroundWorkerInitialDelaySeconds>11</BackgroundWorkerInitialDelaySeconds>"
                + "<PendingSubscriptionRecoveryIntervalSeconds>31</PendingSubscriptionRecoveryIntervalSeconds>"
                + "<PendingSubscriptionRecoveryBatchSize>21</PendingSubscriptionRecoveryBatchSize>"
                + "<WorkerShutdownTimeoutSeconds>6</WorkerShutdownTimeoutSeconds>"
                + "<PayloadSigning><Enabled>false</Enabled>"
                + "<Audience>custom-event-audience</Audience></PayloadSigning>"
                + "<Polling><DefaultReturnImmediately>true</DefaultReturnImmediately>"
                + "<DefaultMaxEvents>15</DefaultMaxEvents><MaxEventsLimit>75</MaxEventsLimit>"
                + "<RequestHmacValidationEnabled>true</RequestHmacValidationEnabled></Polling>"
                + "</EventNotifications>"
                + "</DPDPAccelerator>").getBytes());
        CarbonTestEnvironment.configure(configDir);
    }

    @Test
    public void readsConfiguredValueFromXml() {

        assertEquals(DPDPConfigParser.getInstance().getConsentPortalClientId(), CUSTOM_CLIENT_ID);
    }

    @Test
    public void readsConfiguredComplaintsStatutoryDuePeriodDaysFromXml() {

        assertEquals(DPDPConfigParser.getInstance().getComplaintsStatutoryDuePeriodDays(),
                CUSTOM_STATUTORY_DUE_PERIOD_DAYS);
    }

    @Test
    public void readsConfiguredComplaintsAttachmentMaxSizeBytesFromXml() {

        assertEquals(DPDPConfigParser.getInstance().getComplaintsAttachmentMaxSizeBytes(),
                CUSTOM_ATTACHMENT_MAX_SIZE_BYTES);
    }

    @Test
    public void readsConfiguredComplaintsAttachmentMaxFilesPerUploadFromXml() {

        assertEquals(DPDPConfigParser.getInstance().getComplaintsAttachmentMaxFilesPerUpload(),
                CUSTOM_ATTACHMENT_MAX_FILES_PER_UPLOAD);
    }

    @Test
    public void readsConfiguredJdbcPersistenceManagerValuesFromXml() {

        DPDPConfigParser parser = DPDPConfigParser.getInstance();
        assertEquals(parser.getJdbcDataSourceName(), CUSTOM_DATASOURCE_NAME);
        assertEquals(parser.getJdbcConnectionVerificationTimeoutSeconds(), 3);
    }

    @Test
    public void fallsBackToDefaultWhenKeyIsAbsent() {

        assertTrue(DPDPConfigParser.getInstance().isConsentPortalProvisioningEnabled());
    }

    @Test
    public void parsesComplaintsEmailNotificationsEnabledValues() throws Exception {

        DPDPConfigParser parser = DPDPConfigParser.getInstance();
        Field configurationField = DPDPConfigParser.class.getDeclaredField("configuration");
        configurationField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> values = (Map<String, Object>) configurationField.get(parser);
        Map<String, Object> backup = new HashMap<>(values);
        try {
            values.remove("Complaints.EmailNotificationsEnabled");
            assertTrue(parser.isComplaintsEmailNotificationsEnabled());

            values.put("Complaints.EmailNotificationsEnabled", "false");
            assertTrue(!parser.isComplaintsEmailNotificationsEnabled());

            values.put("Complaints.EmailNotificationsEnabled", "TRUE");
            assertTrue(parser.isComplaintsEmailNotificationsEnabled());

            values.put("Complaints.EmailNotificationsEnabled", "flase");
            expectThrows(IllegalStateException.class, parser::isComplaintsEmailNotificationsEnabled);
        } finally {
            values.clear();
            values.putAll(backup);
        }
    }

    @Test
    public void fallsBackToConsentHistoryDefaultsWhenKeysAreAbsent() {

        DPDPConfigParser parser = DPDPConfigParser.getInstance();
        assertTrue(parser.isConsentHistoryEnabled());
        assertTrue(parser.isConsentHistorySnapshotEnabled());
    }

    @Test
    public void fallsBackToConsentExpiryDefaultsWhenKeysAreAbsent() {

        DPDPConfigParser parser = DPDPConfigParser.getInstance();
        assertTrue(parser.isConsentExpiryEnabled());
        assertEquals(parser.getConsentExpiryCronValue(), "0 0 0 * * ?");
        assertEquals(parser.getConsentExpiryBatchSize(), 100);
    }

    @Test
    public void readsConfiguredEventNotificationValuesFromXml() {

        DPDPConfigParser parser = DPDPConfigParser.getInstance();
        assertEquals(parser.getEventNotificationThreadPoolSize(), 8);
        assertEquals(parser.getEventNotificationBaseBackoffSeconds(), 12L);
        assertEquals(parser.getEventNotificationMaxRetries(), 7);
        assertTrue(!parser.isEventNotificationHttpCallbackUrlAllowed());
        assertEquals(parser.getEventNotificationAllowedCallbackPorts(), CUSTOM_ALLOWED_CALLBACK_PORTS);
        assertTrue(parser.isEventNotificationPrivateNetworkCallbackTargetsAllowed());
        assertEquals(parser.getEventNotificationDeliveryWorkerBatchSize(), 25);
        assertEquals(parser.getEventNotificationDeliveryWorkerPollSeconds(), 9);
        assertEquals(parser.getEventNotificationStuckInFlightThresholdSeconds(), 15);
        assertEquals(parser.getEventNotificationMaxVerificationResponseBodyBytes(), 8192);
        assertEquals(parser.getEventNotificationPendingSubscriptionRecoveryThresholdSeconds(), 90);
        assertEquals(parser.getEventNotificationBackgroundWorkerInitialDelaySeconds(), 11);
        assertEquals(parser.getEventNotificationPendingSubscriptionRecoveryIntervalSeconds(), 31);
        assertEquals(parser.getEventNotificationPendingSubscriptionRecoveryBatchSize(), 21);
        assertEquals(parser.getEventNotificationWorkerShutdownTimeoutSeconds(), 6);
        assertTrue(!parser.isEventNotificationPayloadSigningEnabled());
        assertEquals(parser.getEventNotificationPayloadSigningAudience(), "custom-event-audience");
        assertTrue(parser.isEventNotificationPollingDefaultReturnImmediately());
        assertEquals(parser.getEventNotificationPollingDefaultMaxEvents(), 15);
        assertEquals(parser.getEventNotificationPollingMaxEventsLimit(), 75);
        assertTrue(parser.isEventNotificationPollingRequestHmacValidationEnabled());
    }

    @Test
    public void configurationServiceDelegatesConsentExpiryToTheSameParser() {

        DPDPConfigurationService service = new DPDPConfigurationServiceImpl();
        assertTrue(service.isConsentExpiryEnabled());
        assertEquals(service.getConsentExpiryCronValue(), "0 0 0 * * ?");
        assertEquals(service.getConsentExpiryBatchSize(), 100);
    }

    @Test
    public void configurationServiceDelegatesToTheSameParser() {

        DPDPConfigurationService service = new DPDPConfigurationServiceImpl();
        assertEquals(service.getConsentPortalClientId(), CUSTOM_CLIENT_ID);
        assertEquals(service.getComplaintsStatutoryDuePeriodDays(), CUSTOM_STATUTORY_DUE_PERIOD_DAYS);
        assertEquals(service.getComplaintsAttachmentMaxSizeBytes(), CUSTOM_ATTACHMENT_MAX_SIZE_BYTES);
        assertEquals(service.getComplaintsAttachmentMaxFilesPerUpload(), CUSTOM_ATTACHMENT_MAX_FILES_PER_UPLOAD);
        assertTrue(service.isComplaintsEmailNotificationsEnabled());
        assertEquals(service.getJdbcConnectionVerificationTimeoutSeconds(), 3);
        assertTrue(service.isConsentPortalProvisioningEnabled());
        assertEquals(service.getEventNotificationThreadPoolSize(), 8);
        assertEquals(service.getEventNotificationBaseBackoffSeconds(), 12L);
        assertEquals(service.getEventNotificationMaxRetries(), 7);
        assertTrue(!service.isEventNotificationHttpCallbackUrlAllowed());
        assertEquals(service.getEventNotificationAllowedCallbackPorts(), CUSTOM_ALLOWED_CALLBACK_PORTS);
        assertTrue(service.isEventNotificationPrivateNetworkCallbackTargetsAllowed());
        assertEquals(service.getEventNotificationDeliveryWorkerBatchSize(), 25);
        assertEquals(service.getEventNotificationDeliveryWorkerPollSeconds(), 9);
        assertEquals(service.getEventNotificationStuckInFlightThresholdSeconds(), 15);
        assertEquals(service.getEventNotificationMaxVerificationResponseBodyBytes(), 8192);
        assertEquals(service.getEventNotificationPendingSubscriptionRecoveryThresholdSeconds(), 90);
        assertEquals(service.getEventNotificationBackgroundWorkerInitialDelaySeconds(), 11);
        assertEquals(service.getEventNotificationPendingSubscriptionRecoveryIntervalSeconds(), 31);
        assertEquals(service.getEventNotificationPendingSubscriptionRecoveryBatchSize(), 21);
        assertEquals(service.getEventNotificationWorkerShutdownTimeoutSeconds(), 6);
        assertTrue(!service.isEventNotificationPayloadSigningEnabled());
        assertEquals(service.getEventNotificationPayloadSigningAudience(), "custom-event-audience");
        assertTrue(service.isEventNotificationPollingDefaultReturnImmediately());
        assertEquals(service.getEventNotificationPollingDefaultMaxEvents(), 15);
        assertEquals(service.getEventNotificationPollingMaxEventsLimit(), 75);
        assertTrue(service.isEventNotificationPollingRequestHmacValidationEnabled());
    }

    @Test
    public void configurationServiceUsesTypedDefaultsWhenParserIsUnavailable() {

        DPDPConfigurationService service = new DPDPConfigurationServiceImpl(false);
        assertEquals(service.getConfigurations().size(), 0);
        assertEquals(service.getComplaintsStatutoryDuePeriodDays(), 90);
        assertEquals(service.getComplaintsAttachmentMaxSizeBytes(),
                DPDPCommonConstants.DEFAULT_COMPLAINTS_ATTACHMENT_MAX_SIZE_BYTES);
        assertEquals(service.getComplaintsAttachmentMaxFilesPerUpload(),
                DPDPCommonConstants.DEFAULT_COMPLAINTS_ATTACHMENT_MAX_FILES_PER_UPLOAD);
        assertEquals(service.isComplaintsEmailNotificationsEnabled(),
                DPDPCommonConstants.DEFAULT_COMPLAINTS_EMAIL_NOTIFICATIONS_ENABLED);
        assertEquals(service.getJdbcConnectionVerificationTimeoutSeconds(), 1);
        assertEquals(service.getEventNotificationThreadPoolSize(), 4);
        assertEquals(service.getEventNotificationBaseBackoffSeconds(), 5L);
        assertEquals(service.getEventNotificationMaxRetries(), 5);
        assertTrue(service.isEventNotificationHttpCallbackUrlAllowed());
        assertEquals(service.getEventNotificationAllowedCallbackPorts(),
                DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_ALLOWED_CALLBACK_PORTS);
        assertTrue(!service.isEventNotificationPrivateNetworkCallbackTargetsAllowed());
        assertEquals(service.getEventNotificationDeliveryWorkerBatchSize(), 50);
        assertEquals(service.getEventNotificationDeliveryWorkerPollSeconds(), 5);
        assertEquals(service.getEventNotificationStuckInFlightThresholdSeconds(), 10);
        assertEquals(service.getEventNotificationMaxVerificationResponseBodyBytes(), 4096);
        assertEquals(service.getEventNotificationPendingSubscriptionRecoveryThresholdSeconds(), 60);
        assertEquals(service.getEventNotificationBackgroundWorkerInitialDelaySeconds(), 10);
        assertEquals(service.getEventNotificationPendingSubscriptionRecoveryIntervalSeconds(), 30);
        assertEquals(service.getEventNotificationPendingSubscriptionRecoveryBatchSize(), 20);
        assertEquals(service.getEventNotificationWorkerShutdownTimeoutSeconds(), 5);
        assertTrue(service.isEventNotificationPayloadSigningEnabled());
        assertEquals(service.getEventNotificationPayloadSigningAudience(), "dpdp-event-notifications");
        assertTrue(service.isEventNotificationPollingDefaultReturnImmediately());
        assertEquals(service.getEventNotificationPollingDefaultMaxEvents(), 20);
        assertEquals(service.getEventNotificationPollingMaxEventsLimit(), 100);
        assertTrue(!service.isEventNotificationPollingRequestHmacValidationEnabled());
    }

    @Test
    public void configurationServiceValidatesConfiguredTypesAndRanges() throws Exception {
        DPDPConfigurationServiceImpl service = new DPDPConfigurationServiceImpl();
        DPDPConfigParser parser = DPDPConfigParser.getInstance();
        Field configurationField = DPDPConfigParser.class.getDeclaredField("configuration");
        configurationField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> values = (Map<String, Object>) configurationField.get(parser);
        Map<String, Object> backup = new HashMap<>(values);
        values.clear();
        values.put("Complaints.StatutoryDuePeriodDays", "45");
        values.put("Complaints.AttachmentMaxSizeBytes", "2048");
        values.put("Complaints.AttachmentMaxFilesPerUpload", "3");
        values.put("EventNotifications.ThreadPoolSize", "8");
        values.put("EventNotifications.BaseBackoffSeconds", "12");
        values.put("EventNotifications.MaxRetries", "3");
        values.put("EventNotifications.AllowHttpCallbackUrl", "false");
        values.put("EventNotifications.AllowedCallbackPorts", "80, 9443");
        values.put("EventNotifications.AllowPrivateNetworkCallbackTargets", "true");
        values.put("EventNotifications.DeliveryWorkerBatchSize", "10");
        values.put("EventNotifications.DeliveryWorkerPollSeconds", "2");
        values.put("EventNotifications.StuckInFlightThresholdSeconds", "0");
        values.put("EventNotifications.MaxVerificationResponseBodyBytes", "2048");
        values.put("EventNotifications.PendingSubscriptionRecoveryThresholdSeconds", "0");
        values.put("EventNotifications.BackgroundWorkerInitialDelaySeconds", "0");
        values.put("EventNotifications.PendingSubscriptionRecoveryIntervalSeconds", "30");
        values.put("EventNotifications.PendingSubscriptionRecoveryBatchSize", "20");
        values.put("EventNotifications.WorkerShutdownTimeoutSeconds", "5");
        values.put("EventNotifications.PayloadSigning.Enabled", "false");
        values.put("EventNotifications.PayloadSigning.Audience", "configured-audience");
        try {
            assertEquals(service.getComplaintsStatutoryDuePeriodDays(), CUSTOM_STATUTORY_DUE_PERIOD_DAYS);
            assertEquals(service.getComplaintsAttachmentMaxSizeBytes(), 2048L);
            assertEquals(service.getComplaintsAttachmentMaxFilesPerUpload(), 3);
            assertEquals(service.getEventNotificationThreadPoolSize(), 8);
        assertEquals(service.getEventNotificationBaseBackoffSeconds(), 12L);
        assertEquals(service.getEventNotificationMaxRetries(), 3);
        assertTrue(!service.isEventNotificationHttpCallbackUrlAllowed());
        assertEquals(service.getEventNotificationAllowedCallbackPorts(), CUSTOM_ALLOWED_CALLBACK_PORTS);
        assertTrue(service.isEventNotificationPrivateNetworkCallbackTargetsAllowed());
        assertEquals(service.getEventNotificationDeliveryWorkerBatchSize(), 10);
        assertEquals(service.getEventNotificationDeliveryWorkerPollSeconds(), 2);
        assertEquals(service.getEventNotificationStuckInFlightThresholdSeconds(), 0);
        assertEquals(service.getEventNotificationMaxVerificationResponseBodyBytes(), 2048);
        assertEquals(service.getEventNotificationPendingSubscriptionRecoveryThresholdSeconds(), 0);
        assertEquals(service.getEventNotificationBackgroundWorkerInitialDelaySeconds(), 0);
        assertEquals(service.getEventNotificationPendingSubscriptionRecoveryIntervalSeconds(), 30);
        assertEquals(service.getEventNotificationPendingSubscriptionRecoveryBatchSize(), 20);
        assertEquals(service.getEventNotificationWorkerShutdownTimeoutSeconds(), 5);
        assertTrue(!service.isEventNotificationPayloadSigningEnabled());
        assertEquals(service.getEventNotificationPayloadSigningAudience(), "configured-audience");

        values.put("Complaints.StatutoryDuePeriodDays", "0");
        expectThrows(IllegalStateException.class, service::getComplaintsStatutoryDuePeriodDays);
        values.put("Complaints.StatutoryDuePeriodDays", String.valueOf(CUSTOM_STATUTORY_DUE_PERIOD_DAYS));

        values.put("Complaints.AttachmentMaxSizeBytes", "-1");
        expectThrows(IllegalStateException.class, service::getComplaintsAttachmentMaxSizeBytes);
        values.put("Complaints.AttachmentMaxSizeBytes", "2048");

        values.put("Complaints.AttachmentMaxFilesPerUpload", "0");
        expectThrows(IllegalStateException.class, service::getComplaintsAttachmentMaxFilesPerUpload);
        values.put("Complaints.AttachmentMaxFilesPerUpload", "3");

        values.put("EventNotifications.ThreadPoolSize", "0");
        expectThrows(IllegalStateException.class, service::getEventNotificationThreadPoolSize);
            values.put("EventNotifications.ThreadPoolSize", "bad");
            expectThrows(IllegalStateException.class, service::getEventNotificationThreadPoolSize);
            values.put("EventNotifications.AllowHttpCallbackUrl", "bad");
            expectThrows(IllegalStateException.class, service::isEventNotificationHttpCallbackUrlAllowed);
            values.put("EventNotifications.AllowedCallbackPorts", "80,bad");
            expectThrows(IllegalStateException.class, service::getEventNotificationAllowedCallbackPorts);
            values.put("EventNotifications.AllowPrivateNetworkCallbackTargets", "bad");
            expectThrows(IllegalStateException.class,
                    service::isEventNotificationPrivateNetworkCallbackTargetsAllowed);
            values.put("EventNotifications.PayloadSigning.Enabled", "bad");
            expectThrows(IllegalStateException.class, service::isEventNotificationPayloadSigningEnabled);
            values.put("EventNotifications.PendingSubscriptionRecoveryIntervalSeconds", "0");
            expectThrows(IllegalStateException.class,
                    service::getEventNotificationPendingSubscriptionRecoveryIntervalSeconds);
        } finally {
            values.clear();
            values.putAll(backup);
        }
    }
}
