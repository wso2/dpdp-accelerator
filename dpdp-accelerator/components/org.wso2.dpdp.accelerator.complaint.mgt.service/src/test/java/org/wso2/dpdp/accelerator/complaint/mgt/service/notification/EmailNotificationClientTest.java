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
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package org.wso2.dpdp.accelerator.complaint.mgt.service.notification;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.wso2.carbon.identity.application.common.model.ServiceProvider;
import org.wso2.carbon.identity.application.mgt.ApplicationManagementService;
import org.wso2.carbon.identity.core.util.IdentityTenantUtil;
import org.wso2.carbon.identity.core.util.IdentityUtil;
import org.wso2.carbon.identity.event.IdentityEventConstants;
import org.wso2.carbon.identity.event.event.Event;
import org.wso2.carbon.identity.event.services.IdentityEventService;
import org.wso2.carbon.identity.organization.management.service.OrganizationManager;
import org.wso2.carbon.identity.role.v2.mgt.core.RoleManagementService;
import org.wso2.carbon.identity.role.v2.mgt.core.model.UserBasicInfo;
import org.wso2.carbon.user.api.UserRealm;
import org.wso2.carbon.user.core.common.AbstractUserStoreManager;
import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.carbon.user.core.tenant.TenantManager;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link EmailNotificationClient} against mocked OSGi services - the real lookups go
 * through {@code PrivilegedCarbonContext.getOSGiService}, which only resolves inside an actual
 * Carbon/OSGi runtime, so the package-private supplier constructor is the test seam. Every
 * recipient (officers, the creator) and every {@code TRIGGER_NOTIFICATION} fire happens directly
 * in this class now - there's no custom event and no {@code identity.extensions} round trip to
 * stand in for.
 */
public class EmailNotificationClientTest {

    private static final String EMAIL_CLAIM = "http://wso2.org/claims/emailaddress";
    private static final String APPLICATION_NAME = "DPDP Consent Portal";
    private static final String DPO_ROLE = "dpdp-consent-dpo";
    private static final String ROLE_AUDIENCE = "organization";
    private static final String ORGANIZATION_ID = "org-id-1";
    private static final String APPLICATION_ID = "app1";
    private static final String ROLE_ID = "role1";
    private static final int TENANT_ID = 1;

    @Mock
    private IdentityEventService identityEventService;

    @Mock
    private RealmService realmService;

    @Mock
    private UserRealm userRealm;

    @Mock
    private AbstractUserStoreManager userStoreManager;

    @Mock
    private TenantManager tenantManager;

    @Mock
    private ApplicationManagementService applicationManagementService;

    @Mock
    private RoleManagementService roleManagementService;

    @Mock
    private OrganizationManager organizationManager;

    @Mock
    private DPDPConfigurationService configurationService;

    private MockedStatic<IdentityUtil> identityUtilMock;
    private AutoCloseable mocks;

    @BeforeMethod
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);

        // IdentityUtil.getServerURL(...) needs IS's own bootstrap-populated
        // ConfigurationContextService, which a bare unit test never has - statically mocked so the
        // action/logo links don't throw building them.
        identityUtilMock = Mockito.mockStatic(IdentityUtil.class);
        identityUtilMock.when(() -> IdentityUtil.getServerURL(anyString(), anyBoolean(), anyBoolean()))
                .thenReturn("https://localhost:9443/consent-portal/");

        // Complaints.EmailNotificationsEnabled defaults to false (opt-in) - every test below is
        // exercising the actual send behavior, so it needs the flag on unless it says otherwise.
        when(configurationService.isComplaintsEmailNotificationsEnabled()).thenReturn(true);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        identityUtilMock.close();
        mocks.close();
    }

    private Complaint complaint() {
        return complaint("OPEN");
    }

    private Complaint complaint(String status) {
        return new Complaint(
                "c1",
                "org1",
                "user1",
                "User One",
                "CMP-2026-00001",
                "DATA_BREACH",
                "CRITICAL",
                status,
                "desc",
                1L,
                2L,
                3L
        );
    }

    private EmailNotificationClient fullClient() {
        return new EmailNotificationClient(
                () -> identityEventService,
                () -> realmService,
                () -> applicationManagementService,
                () -> roleManagementService,
                () -> organizationManager,
                () -> configurationService
        );
    }

    /**
     * Wires the RealmService mock chain both officer/creator resolution and
     * buildTemplatePlaceholders walk.
     */
    private void stubUserRealm() throws Exception {
        IdentityTenantUtil.setRealmService(realmService);
        when(realmService.getTenantManager()).thenReturn(tenantManager);
        when(tenantManager.getTenantId("org1")).thenReturn(TENANT_ID);
        when(realmService.getTenantUserRealm(TENANT_ID)).thenReturn(userRealm);
        when(userRealm.getUserStoreManager()).thenReturn(userStoreManager);
    }

    private void stubEmailClaim(String username, String email) throws Exception {
        when(userStoreManager.getUserClaimValue(username, EMAIL_CLAIM, null))
                .thenReturn(email);
    }

    /**
     * Wires the DPDP Consent Portal organization lookup +
     * dpdp-consent-dpo role membership.
     */
    private void stubOfficerResolution(List<UserBasicInfo> members) throws Exception {
        when(organizationManager.resolveOrganizationId("org1")).thenReturn(ORGANIZATION_ID);

        when(roleManagementService.isExistingRoleName(
                DPO_ROLE, ROLE_AUDIENCE, ORGANIZATION_ID, "org1"))
                .thenReturn(true);

        when(roleManagementService.getRoleIdByName(
                DPO_ROLE, ROLE_AUDIENCE, ORGANIZATION_ID, "org1"))
                .thenReturn(ROLE_ID);

        when(roleManagementService.getUserListOfRole(
                ROLE_ID, "org1"))
                .thenReturn(members);
    }

    @Test
    public void notifyComplaintCreatedFiresTriggerNotificationForEachOfficerAndTheCreator()
            throws Exception {

        stubUserRealm();

        stubOfficerResolution(List.of(
                new UserBasicInfo("o1", "officer1"),
                new UserBasicInfo("o2", "officer2")
        ));

        stubEmailClaim("officer1", "officer1@example.com");
        stubEmailClaim("officer2", "officer2@example.com");
        stubEmailClaim("User One", "user1@example.com");

        fullClient().notifyComplaintCreated(complaint());

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);

        verify(identityEventService, times(3)).handleEvent(captor.capture());

        List<Event> events = captor.getAllValues();

        Assert.assertTrue(events.stream()
                .allMatch(e ->
                        IdentityEventConstants.Event.TRIGGER_NOTIFICATION
                                .equals(e.getEventName())));

        Event officerEvent = events.stream()
                .filter(e ->
                        "officer1@example.com"
                                .equals(e.getEventProperties().get("send-to")))
                .findFirst()
                .orElseThrow();

        Map<String, Object> officerProps = officerEvent.getEventProperties();

        Assert.assertEquals(
                officerProps.get(IdentityEventConstants.EventProperty.USER_NAME),
                "officer1");

        Assert.assertEquals(
                officerProps.get(IdentityEventConstants.EventProperty.TENANT_DOMAIN),
                "org1");

        Assert.assertEquals(
                officerProps.get("TEMPLATE_TYPE"),
                "ComplaintCreated");

        Assert.assertEquals(
                officerProps.get("reference-id"),
                "CMP-2026-00001");

        Assert.assertTrue(
                ((String) officerProps.get("headline-html"))
                        .contains("filed a new complaint"));

        Assert.assertTrue(
                ((String) officerProps.get("action-badge-html"))
                        .contains("Action Needed"));

        Event acknowledgementEvent = events.stream()
                .filter(e ->
                        "user1@example.com"
                                .equals(e.getEventProperties().get("send-to")))
                .findFirst()
                .orElseThrow();

        Map<String, Object> ackProps =
                acknowledgementEvent.getEventProperties();

        Assert.assertEquals(
                ackProps.get(IdentityEventConstants.EventProperty.USER_NAME),
                "User One");

        Assert.assertEquals(
                ackProps.get("TEMPLATE_TYPE"),
                "ComplaintAcknowledged");

        Assert.assertTrue(
                ((String) ackProps.get("headline-html"))
                        .contains("has been received"));
    }

    @Test
    public void notifyComplaintCreatedSendsNothingWhenEmailNotificationsAreDisabled()
            throws Exception {

        stubUserRealm();
        stubOfficerResolution(List.of(new UserBasicInfo("o1", "officer1")));
        stubEmailClaim("officer1", "officer1@example.com");
        stubEmailClaim("User One", "user1@example.com");
        when(configurationService.isComplaintsEmailNotificationsEnabled()).thenReturn(false);

        fullClient().notifyComplaintCreated(complaint());

        verify(identityEventService, never()).handleEvent(any());
    }

    @Test
    public void notifyCommentAddedSendsNothingWhenEmailNotificationsAreDisabled()
            throws Exception {

        stubUserRealm();
        stubOfficerResolution(List.of(new UserBasicInfo("o1", "officer1")));
        stubEmailClaim("officer1", "officer1@example.com");
        when(configurationService.isComplaintsEmailNotificationsEnabled()).thenReturn(false);

        fullClient().notifyCommentAdded(complaint(),
                new ComplaintEvent("e1", "org1", "c1", "user1", "User One", "DATA_PRINCIPAL", true, "a comment",
                        null, null, 1L));

        verify(identityEventService, never()).handleEvent(any());
    }

    @Test
    public void notifyComplaintCreatedSendsOnlyTheAcknowledgementWhenNoOfficersResolve()
            throws Exception {

        stubUserRealm();
        stubEmailClaim("User One", "user1@example.com");

        EmailNotificationClient client =
                new EmailNotificationClient(
                        () -> identityEventService,
                        () -> realmService,
                        () -> null,
                        () -> null
                );

        client.notifyComplaintCreated(complaint());

        ArgumentCaptor<Event> captor =
                ArgumentCaptor.forClass(Event.class);

        verify(identityEventService, times(1))
                .handleEvent(captor.capture());

        Map<String, Object> props =
                captor.getValue().getEventProperties();

        Assert.assertEquals(
                props.get("send-to"),
                "user1@example.com");

        Assert.assertEquals(
                props.get("TEMPLATE_TYPE"),
                "ComplaintAcknowledged");
    }

    @Test
    public void notifyComplaintCreatedSendsNothingWhenRealmServiceIsUnresolvable()
            throws Exception {

        EmailNotificationClient client =
                new EmailNotificationClient(
                        () -> identityEventService,
                        () -> null,
                        () -> applicationManagementService,
                        () -> roleManagementService
                );

        client.notifyComplaintCreated(complaint());

        verify(identityEventService, never())
                .handleEvent(any(Event.class));
    }

    @Test
    public void acknowledgementIsSkippedWhenTheEmailClaimIsUnresolvable()
            throws Exception {

        stubUserRealm();
        stubEmailClaim("User One", null);

        EmailNotificationClient client =
                new EmailNotificationClient(
                        () -> identityEventService,
                        () -> realmService,
                        () -> null,
                        () -> null
                );

        client.notifyComplaintCreated(complaint());

        verify(identityEventService, never())
                .handleEvent(any(Event.class));
    }

    @Test
    public void notifyCommentAddedByCitizenNotifiesOfficers()
            throws Exception {

        stubUserRealm();

        stubOfficerResolution(
                Collections.singletonList(
                        new UserBasicInfo("o1", "officer1")));

        stubEmailClaim(
                "officer1",
                "officer1@example.com");

        ComplaintEvent event = new ComplaintEvent(
                "e1",
                "org1",
                "c1",
                "user1",
                "User One",
                "USER",
                true,
                "hello there",
                null,
                null,
                100L
        );

        fullClient().notifyCommentAdded(complaint(), event);

        ArgumentCaptor<Event> captor =
                ArgumentCaptor.forClass(Event.class);

        verify(identityEventService, times(1))
                .handleEvent(captor.capture());

        Assert.assertEquals(
                captor.getValue().getEventName(),
                IdentityEventConstants.Event.TRIGGER_NOTIFICATION);

        Map<String, Object> props =
                captor.getValue().getEventProperties();

        Assert.assertEquals(
                props.get("send-to"),
                "officer1@example.com");

        Assert.assertEquals(
                props.get("TEMPLATE_TYPE"),
                "ComplaintCommentAdded");

        Assert.assertEquals(
                props.get("message-excerpt"),
                "hello there");

        Assert.assertTrue(
                ((String) props.get("headline-html"))
                        .contains("replied to complaint"));

        Assert.assertTrue(
                ((String) props.get("footer-text"))
                        .contains("Grievance Officer"));
    }

    @Test
    public void notifyCommentAddedByOfficerNotifiesCreator()
            throws Exception {

        stubUserRealm();

        stubEmailClaim(
                "User One",
                "user1@example.com");

        ComplaintEvent event = new ComplaintEvent(
                "e1",
                "org1",
                "c1",
                "officer1",
                "Officer One",
                "COMPLAINT_OFFICER",
                true,
                "hi",
                null,
                null,
                100L
        );

        EmailNotificationClient client =
                new EmailNotificationClient(
                        () -> identityEventService,
                        () -> realmService,
                        () -> null,
                        () -> null
                );

        client.notifyCommentAdded(complaint(), event);

        ArgumentCaptor<Event> captor =
                ArgumentCaptor.forClass(Event.class);

        verify(identityEventService, times(1))
                .handleEvent(captor.capture());

        Map<String, Object> props =
                captor.getValue().getEventProperties();

        Assert.assertEquals(
                props.get("send-to"),
                "user1@example.com");

        Assert.assertEquals(
                props.get(IdentityEventConstants.EventProperty.USER_NAME),
                "User One");

        Assert.assertEquals(
                props.get("TEMPLATE_TYPE"),
                "ComplaintCommentAdded");

        Assert.assertTrue(
                ((String) props.get("footer-text"))
                        .contains("filed this complaint"));
    }

    @Test
    public void notifyCommentAddedSkipsWhenNoRecipientsResolve()
            throws Exception {

        EmailNotificationClient client =
                new EmailNotificationClient(
                        () -> identityEventService,
                        () -> null,
                        () -> null,
                        () -> null
                );

        ComplaintEvent event = new ComplaintEvent(
                "e1",
                "org1",
                "c1",
                "user1",
                "User One",
                "USER",
                true,
                "hi",
                null,
                null,
                100L
        );

        client.notifyCommentAdded(complaint(), event);

        verify(identityEventService, never())
                .handleEvent(any(Event.class));
    }

    @Test
    public void truncatesLongMessagesToAnExcerpt()
            throws Exception {

        stubUserRealm();

        stubOfficerResolution(
                Collections.singletonList(
                        new UserBasicInfo("o1", "officer1")));

        stubEmailClaim(
                "officer1",
                "officer1@example.com");

        String longMessage = "a".repeat(500);

        ComplaintEvent event = new ComplaintEvent(
                "e1",
                "org1",
                "c1",
                "user1",
                "User One",
                "USER",
                true,
                longMessage,
                null,
                null,
                100L
        );

        fullClient().notifyCommentAdded(complaint(), event);

        ArgumentCaptor<Event> captor =
                ArgumentCaptor.forClass(Event.class);

        verify(identityEventService)
                .handleEvent(captor.capture());

        String excerpt =
                (String) captor.getValue()
                        .getEventProperties()
                        .get("message-excerpt");

        Assert.assertTrue(
                excerpt.length() < longMessage.length());

        Assert.assertTrue(
                excerpt.endsWith("..."));
    }

    @Test
    public void actionBadgeIsUpdateForOfficersWhenItsStillTheCitizensTurn()
            throws Exception {

        stubUserRealm();

        stubOfficerResolution(
                Collections.singletonList(
                        new UserBasicInfo("o1", "officer1")));

        stubEmailClaim(
                "officer1",
                "officer1@example.com");

        ComplaintEvent event = new ComplaintEvent(
                "e1",
                "org1",
                "c1",
                "user1",
                "User One",
                "USER",
                true,
                "here is more info",
                "WAITING_ON_CLIENT",
                "WAITING_ON_CLIENT",
                100L
        );

        fullClient().notifyCommentAdded(
                complaint("WAITING_ON_CLIENT"),
                event);

        ArgumentCaptor<Event> captor =
                ArgumentCaptor.forClass(Event.class);

        verify(identityEventService)
                .handleEvent(captor.capture());

        Map<String, Object> props =
                captor.getValue().getEventProperties();

        Assert.assertTrue(
                ((String) props.get("action-badge-html"))
                        .contains("Update"));

        Assert.assertEquals(
                props.get("status-label"),
                "Waiting on Client");
    }

    @Test
    public void actionBadgeIsActionNeededForTheCreatorWhenAnOfficerRequestsMoreInfo()
            throws Exception {

        stubUserRealm();

        stubEmailClaim(
                "User One",
                "user1@example.com");

        ComplaintEvent event = new ComplaintEvent(
                "e1",
                "org1",
                "c1",
                "officer1",
                "Officer One",
                "COMPLAINT_OFFICER",
                true,
                "please clarify",
                "OPEN",
                "WAITING_ON_CLIENT",
                100L
        );

        EmailNotificationClient client =
                new EmailNotificationClient(
                        () -> identityEventService,
                        () -> realmService,
                        () -> null,
                        () -> null
                );

        client.notifyCommentAdded(
                complaint("WAITING_ON_CLIENT"),
                event);

        ArgumentCaptor<Event> captor =
                ArgumentCaptor.forClass(Event.class);

        verify(identityEventService)
                .handleEvent(captor.capture());

        Map<String, Object> props =
                captor.getValue().getEventProperties();

        Assert.assertTrue(
                ((String) props.get("action-badge-html"))
                        .contains("Action Needed"));
    }

    @Test
    public void actionBadgeIsResolvedRegardlessOfRecipientWhenComplaintIsResolved()
            throws Exception {

        stubUserRealm();

        stubOfficerResolution(
                Collections.singletonList(
                        new UserBasicInfo("o1", "officer1")));

        stubEmailClaim(
                "officer1",
                "officer1@example.com");

        ComplaintEvent event = new ComplaintEvent(
                "e1",
                "org1",
                "c1",
                "user1",
                "User One",
                "USER",
                true,
                "thanks",
                "WAITING_ON_CLIENT",
                "RESOLVED",
                100L
        );

        fullClient().notifyCommentAdded(
                complaint("RESOLVED"),
                event);

        ArgumentCaptor<Event> captor =
                ArgumentCaptor.forClass(Event.class);

        verify(identityEventService)
                .handleEvent(captor.capture());

        Map<String, Object> props =
                captor.getValue().getEventProperties();

        Assert.assertTrue(
                ((String) props.get("action-badge-html"))
                        .contains("Resolved"));

        Assert.assertEquals(
                props.get("status-label"),
                "Resolved");
    }

    @Test
    public void neverThrowsWhenTheEventServiceIsUnresolvable() {

        EmailNotificationClient client =
                new EmailNotificationClient(
                        () -> null,
                        () -> null,
                        () -> null,
                        () -> null
                );

        client.notifyComplaintCreated(complaint());
    }

    @Test
    public void neverThrowsWhenTheEventServiceThrows()
            throws Exception {

        stubUserRealm();

        stubOfficerResolution(
                Collections.singletonList(
                        new UserBasicInfo("o1", "officer1")));

        stubEmailClaim(
                "officer1",
                "officer1@example.com");

        stubEmailClaim(
                "User One",
                "user1@example.com");

        doThrow(new RuntimeException("boom"))
                .when(identityEventService)
                .handleEvent(any(Event.class));

        fullClient().notifyComplaintCreated(complaint());

        // One officer + one acknowledgement - each handleEvent call is caught independently.
        verify(identityEventService, times(2))
                .handleEvent(any(Event.class));
    }

    @Test
    public void neverThrowsWhenTheSupplierItselfThrows()
            throws Exception {

        EmailNotificationClient client =
                new EmailNotificationClient(
                        () -> {
                            throw new NoClassDefFoundError(
                                    "org.wso2.carbon.context.PrivilegedCarbonContext");
                        },
                        () -> null,
                        () -> null,
                        () -> null
                );

        client.notifyComplaintCreated(complaint());

        verify(identityEventService, never())
                .handleEvent(any(Event.class));
    }
}

