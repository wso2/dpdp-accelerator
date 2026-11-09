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

package org.wso2.dpdp.accelerator.identity.extensions.consent;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.carbon.consent.mgt.core.PrivilegedConsentManager;
import org.wso2.carbon.consent.mgt.core.model.ConsentAuthorization;
import org.wso2.carbon.consent.mgt.core.model.ConsentPurpose;
import org.wso2.carbon.consent.mgt.core.model.Receipt;
import org.wso2.carbon.consent.mgt.core.model.ReceiptInput;
import org.wso2.carbon.consent.mgt.core.model.ReceiptService;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.consent.extensions.service.ConsentHistoryService;
import org.wso2.dpdp.accelerator.event.notifications.common.listener.DPDPLifecycleEventListener;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link DPDPConsentManagementListener}.
 */
public class DPDPConsentManagementListenerTest {

    private static final String CARBON_CONFIG_DIR_PROPERTY = "carbon.config.dir.path";
    private static final String MINIMAL_CARBON_XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<Server xmlns=\"http://wso2.org/projects/carbon/carbon.xml\">"
                    + "<Name>WSO2 Identity Server</Name>"
                    + "<ServerKey>IS</ServerKey>"
                    + "<Version>7.3.0</Version>"
                    + "<HostName>localhost</HostName>"
                    + "<MgtHostName>localhost</MgtHostName>"
                    + "<Ports><Offset>0</Offset></Ports>"
                    + "<Security><NetworkAuthenticatorConfig/></Security>"
                    + "</Server>";

    @Mock
    private DPDPConfigurationService configurationService;

    @Mock
    private PrivilegedConsentManager consentManager;

    @Mock
    private ConsentHistoryService consentHistoryService;

    @Mock
    private DPDPLifecycleEventListener lifecycleEventListener;

    private DPDPConsentManagementListener listener;

    @BeforeMethod
    public void setUp() {

        MockitoAnnotations.openMocks(this);
        DPDPIdentityExtensionDataHolder holder = DPDPIdentityExtensionDataHolder.getInstance();
        holder.setConfigurationService(configurationService);
        holder.setPrivilegedConsentManager(consentManager);
        holder.setConsentHistoryService(consentHistoryService);
        holder.setLifecycleEventListener(lifecycleEventListener);
        listener = new DPDPConsentManagementListener();
    }

    @AfterMethod
    public void tearDown() {

        DPDPIdentityExtensionDataHolder holder = DPDPIdentityExtensionDataHolder.getInstance();
        holder.setPrivilegedConsentManager(null);
        holder.setConsentHistoryService(null);
        holder.setLifecycleEventListener(null);
        System.clearProperty(CARBON_CONFIG_DIR_PROPERTY);
    }

    @Test
    public void isEnableDelegatesToConfigurationService() {

        when(configurationService.isConsentHistoryEnabled()).thenReturn(true);
        assertTrue(listener.isEnable());

        when(configurationService.isConsentHistoryEnabled()).thenReturn(false);
        assertFalse(listener.isEnable());
    }

    @Test
    public void getDefaultOrderIdReturnsAFixedValue() {

        assertEquals(listener.getDefaultOrderId(), 100);
    }

    @Test
    public void postAddConsentNotifiesOnConsentCreatedWithResolvedStatusAndPurposes() throws Exception {

        ConsentPurpose marketing = new ConsentPurpose();
        marketing.setPurpose("marketing");
        ConsentPurpose analytics = new ConsentPurpose();
        analytics.setPurpose("analytics");
        ReceiptService service = new ReceiptService();
        service.setPurposes(Arrays.asList(marketing, analytics));
        Receipt receipt = new Receipt();
        receipt.setServices(Collections.singletonList(service));

        when(consentManager.getReceiptWithExtendedSchema("consent-active")).thenReturn(receipt);
        when(consentManager.getReceiptWithExtendedSchema("consent-pending")).thenReturn(receipt);
        when(consentManager.getConsentAuthorizations("consent-active")).thenReturn(Collections.emptyList());
        when(consentManager.getConsentAuthorizations("consent-pending")).thenReturn(Collections.emptyList());

        ReceiptInput directInput = new ReceiptInput();
        directInput.setConsentReceiptId("consent-active");

        ReceiptInput pendingInput = new ReceiptInput();
        pendingInput.setConsentReceiptId("consent-pending");
        pendingInput.setAuthorizations(Collections.singletonList(new ConsentAuthorization()));

        withCarbonContext("alice", () -> {
            listener.postAddConsent(directInput, "carbon.super");
            listener.postAddConsent(pendingInput, "carbon.super");
        });

        verify(lifecycleEventListener).onConsentCreated(
                "carbon.super", "consent-active", "ACTIVE", "alice", Arrays.asList("marketing", "analytics"));
        verify(lifecycleEventListener).onConsentCreated(
                "carbon.super", "consent-pending", "PENDING", "alice", Arrays.asList("marketing", "analytics"));
    }

    @Test
    public void postAddConsentStillNotifiesOnConsentCreatedWhenReceiptReadFails() throws Exception {

        when(consentManager.getReceiptWithExtendedSchema("consent-broken"))
                .thenThrow(new RuntimeException("receipt read failed"));

        ReceiptInput input = new ReceiptInput();
        input.setConsentReceiptId("consent-broken");

        withCarbonContext("bob", () -> listener.postAddConsent(input, "carbon.super"));

        verify(lifecycleEventListener).onConsentCreated(
                "carbon.super", "consent-broken", "ACTIVE", "bob", null);
    }

    private static void withCarbonContext(String username, Runnable action) throws Exception {

        Path configDir = Files.createTempDirectory("dpdp-carbon-listener-test");
        Files.write(configDir.resolve("carbon.xml"), MINIMAL_CARBON_XML.getBytes(StandardCharsets.UTF_8));
        System.setProperty(CARBON_CONFIG_DIR_PROPERTY, configDir.toString());
        PrivilegedCarbonContext.startTenantFlow();
        try {
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain("carbon.super");
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setUsername(username);
            action.run();
        } finally {
            PrivilegedCarbonContext.endTenantFlow();
        }
    }
}
