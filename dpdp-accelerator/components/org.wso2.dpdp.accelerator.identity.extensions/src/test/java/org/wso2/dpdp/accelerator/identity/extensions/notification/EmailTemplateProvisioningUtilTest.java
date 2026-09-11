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

package org.wso2.dpdp.accelerator.identity.extensions.notification;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.carbon.identity.governance.exceptions.notiification.NotificationTemplateManagerException;
import org.wso2.carbon.identity.governance.model.NotificationTemplate;
import org.wso2.carbon.identity.governance.service.notification.NotificationTemplateManager;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class EmailTemplateProvisioningUtilTest {

    private static final String TENANT_DOMAIN = "tenant-a.com";
    private static final String EMAIL_CHANNEL = "EMAIL";
    private static final String DEFAULT_LOCALE = "en_US";

    @Mock
    private NotificationTemplateManager notificationTemplateManager;

    @BeforeMethod
    public void setUp() {

        MockitoAnnotations.openMocks(this);
        DPDPIdentityExtensionDataHolder.getInstance().setNotificationTemplateManager(notificationTemplateManager);
    }

    @Test
    public void provisionTemplatesWritesAllThreeTemplates() throws Exception {

        EmailTemplateProvisioningUtil.provisionTemplates(TENANT_DOMAIN);

        verify(notificationTemplateManager).addNotificationTemplateType(
                eq("ComplaintCreated"), eq(EMAIL_CHANNEL),
                eq(TENANT_DOMAIN));
        verify(notificationTemplateManager).addNotificationTemplateType(
                eq("ComplaintCommentAdded"), eq(EMAIL_CHANNEL),
                eq(TENANT_DOMAIN));
        verify(notificationTemplateManager).addNotificationTemplateType(
                eq("ComplaintAcknowledged"), eq(EMAIL_CHANNEL),
                eq(TENANT_DOMAIN));

        ArgumentCaptor<NotificationTemplate> captor = ArgumentCaptor.forClass(NotificationTemplate.class);
        verify(notificationTemplateManager, times(3)).addNotificationTemplate(captor.capture(), eq(TENANT_DOMAIN));
        List<String> types = captor.getAllValues().stream().map(NotificationTemplate::getType).toList();
        assertTrue(types.contains("ComplaintCreated"));
        assertTrue(types.contains("ComplaintCommentAdded"));
        assertTrue(types.contains("ComplaintAcknowledged"));
        for (NotificationTemplate template : captor.getAllValues()) {
            assertEquals(template.getNotificationChannel(), EMAIL_CHANNEL);
            assertEquals(template.getLocale(), DEFAULT_LOCALE);
        }
    }

    @Test
    public void provisionTemplatesStillWritesContentWhenTheTypeIsAlreadyRegistered() throws Exception {

        // addNotificationTemplateType throws once a tenant already has the type registered -
        // unlike addNotificationTemplate, it is not itself upsert-safe. That failure must be
        // swallowed without skipping the content-existence check below it (here, the template's
        // content itself is not yet present - getNotificationTemplate is unstubbed and returns
        // null - so the type being already registered must not by itself skip the write).
        org.mockito.Mockito.doThrow(new NotificationTemplateManagerException("already exists"))
                .when(notificationTemplateManager).addNotificationTemplateType(anyString(), anyString(), anyString());

        EmailTemplateProvisioningUtil.provisionTemplates(TENANT_DOMAIN);

        verify(notificationTemplateManager, times(3)).addNotificationTemplate(
                org.mockito.ArgumentMatchers.any(NotificationTemplate.class), eq(TENANT_DOMAIN));
    }

    @Test
    public void provisionTemplatesDoesNotThrowWhenWritingContentFails() throws Exception {

        org.mockito.Mockito.doThrow(new NotificationTemplateManagerException("boom"))
                .when(notificationTemplateManager).addNotificationTemplate(
                        org.mockito.ArgumentMatchers.any(NotificationTemplate.class), anyString());

        // Must not throw - a provisioning failure for one tenant shouldn't break the caller.
        EmailTemplateProvisioningUtil.provisionTemplates(TENANT_DOMAIN);
    }

    @Test
    public void provisionTemplatesLeavesAnAlreadyExistingTemplateUntouched() throws Exception {

        // A tenant re-update (any metadata change, not just an accelerator upgrade) must never
        // reset an administrator's Console edit back to the bundled default.
        when(notificationTemplateManager.getNotificationTemplate(eq(EMAIL_CHANNEL), anyString(), eq(DEFAULT_LOCALE),
                eq(TENANT_DOMAIN))).thenReturn(new NotificationTemplate());

        EmailTemplateProvisioningUtil.provisionTemplates(TENANT_DOMAIN);

        verify(notificationTemplateManager, org.mockito.Mockito.never()).addNotificationTemplate(
                org.mockito.ArgumentMatchers.any(NotificationTemplate.class), anyString());
    }

    @Test
    public void provisionTemplatesWritesContentWhenTheExistenceCheckItselfFails() throws Exception {

        // getNotificationTemplate failing (e.g. a transient registry error) must not permanently
        // block provisioning for a tenant that genuinely has no template yet - it is treated the
        // same as "not present".
        when(notificationTemplateManager.getNotificationTemplate(eq(EMAIL_CHANNEL), anyString(), eq(DEFAULT_LOCALE),
                eq(TENANT_DOMAIN))).thenThrow(new NotificationTemplateManagerException("lookup failed"));

        EmailTemplateProvisioningUtil.provisionTemplates(TENANT_DOMAIN);

        verify(notificationTemplateManager, times(3)).addNotificationTemplate(
                org.mockito.ArgumentMatchers.any(NotificationTemplate.class), eq(TENANT_DOMAIN));
    }
}
