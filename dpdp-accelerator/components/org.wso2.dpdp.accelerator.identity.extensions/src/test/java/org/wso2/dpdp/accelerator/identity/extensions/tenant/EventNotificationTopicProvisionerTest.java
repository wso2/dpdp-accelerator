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

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.carbon.stratos.common.exception.StratosException;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.DefaultTopic;
import org.wso2.dpdp.accelerator.event.notifications.service.TopicService;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class EventNotificationTopicProvisionerTest {

    @Mock
    private TopicService topicService;

    @Mock
    private DPDPConfigurationService configurationService;

    @BeforeMethod
    public void setUp() {

        MockitoAnnotations.openMocks(this);
        DPDPIdentityExtensionDataHolder.getInstance().setTopicService(topicService);
        DPDPIdentityExtensionDataHolder.getInstance().setConfigurationService(configurationService);
    }

    @Test
    public void testProvisionSystemTopicsEnsuresEveryDefaultTopic() throws Exception {

        EventNotificationTopicProvisioner.provisionSystemTopics("tenant.example");

        for (DefaultTopic defaultTopic : DefaultTopic.values()) {
            verify(topicService).ensureSystemTopic(
                    "tenant.example", defaultTopic.getName(), defaultTopic.getDescription());
        }
    }

    @Test
    public void testProvisionSystemTopicsAttemptsAllTopicsBeforeReportingFailure() throws Exception {

        doThrow(new IllegalStateException("database unavailable"))
                .when(topicService).ensureSystemTopic(
                        "tenant.example", DefaultTopic.CONSENT_UPDATE.getName(),
                        DefaultTopic.CONSENT_UPDATE.getDescription());

        try {
            EventNotificationTopicProvisioner.provisionSystemTopics("tenant.example");
            fail("Expected system topic provisioning to fail.");
        } catch (StratosException e) {
            assertTrue(e.getMessage().contains(DefaultTopic.CONSENT_UPDATE.getName()));
            assertEquals(e.getCause().getMessage(), "database unavailable");
        }

        verify(topicService, times(DefaultTopic.values().length))
                .ensureSystemTopic(anyString(), anyString(), anyString());
    }

    @Test
    public void testReconcileExistingTenantSystemTopicsDelegatesWhenEnabled() {

        when(configurationService.isEventNotificationSystemTopicsAutoCreateEnabled()).thenReturn(true);
        when(configurationService.isEventNotificationSystemTopicsVerifyOnStartupEnabled()).thenReturn(true);

        EventNotificationTopicProvisioner.reconcileExistingTenantSystemTopics();

        verify(topicService).reconcileSystemTopicsAcrossTenants();
    }

    @Test
    public void testReconcileExistingTenantSystemTopicsSkipsWhenAutoCreateDisabled() {

        when(configurationService.isEventNotificationSystemTopicsAutoCreateEnabled()).thenReturn(false);
        when(configurationService.isEventNotificationSystemTopicsVerifyOnStartupEnabled()).thenReturn(true);

        EventNotificationTopicProvisioner.reconcileExistingTenantSystemTopics();

        verify(topicService, never()).reconcileSystemTopicsAcrossTenants();
    }

    @Test
    public void testReconcileExistingTenantSystemTopicsSkipsWhenVerifyOnStartupDisabled() {

        when(configurationService.isEventNotificationSystemTopicsAutoCreateEnabled()).thenReturn(true);
        when(configurationService.isEventNotificationSystemTopicsVerifyOnStartupEnabled()).thenReturn(false);

        EventNotificationTopicProvisioner.reconcileExistingTenantSystemTopics();

        verify(topicService, never()).reconcileSystemTopicsAcrossTenants();
    }

    @Test
    public void testReconcileExistingTenantSystemTopicsSkipsWhenServicesNull() {

        DPDPIdentityExtensionDataHolder.getInstance().setConfigurationService(null);
        EventNotificationTopicProvisioner.reconcileExistingTenantSystemTopics();
        verify(topicService, never()).reconcileSystemTopicsAcrossTenants();

        DPDPIdentityExtensionDataHolder.getInstance().setConfigurationService(configurationService);
        when(configurationService.isEventNotificationSystemTopicsAutoCreateEnabled()).thenReturn(true);
        when(configurationService.isEventNotificationSystemTopicsVerifyOnStartupEnabled()).thenReturn(true);
        DPDPIdentityExtensionDataHolder.getInstance().setTopicService(null);
        EventNotificationTopicProvisioner.reconcileExistingTenantSystemTopics();
    }
}
