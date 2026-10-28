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

package org.wso2.dpdp.accelerator.event.notifications.service.listener;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.DefaultTopic;
import org.wso2.dpdp.accelerator.event.notifications.service.EventPublishService;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertThrows;

/**
 * Each method is a straight, unconditional mapping onto
 * {@link EventPublishService#publishEvent} - no branching, so one assertion per method is enough.
 */
public class DPDPLifecycleEventPublisherTest {

    @Mock
    private EventPublishService eventPublishService;

    private DPDPLifecycleEventPublisher publisher;

    @BeforeMethod
    public void setUp() {

        MockitoAnnotations.openMocks(this);
        publisher = new DPDPLifecycleEventPublisher(eventPublishService);
    }

    @Test
    public void onConsentCreatedPublishesToConsentCreateTopicWithPurposes() {

        List<String> purposes = Arrays.asList("marketing", "analytics");

        publisher.onConsentCreated("tenant.example", "consent-1", "ACTIVE", "jdoe", purposes);

        verify(eventPublishService).publishEvent(eq("tenant.example"), eq("tenant.example"),
                eq(DefaultTopic.CONSENT_CREATE.getName()), eq(purposes), any());
    }

    @Test
    public void onConsentUpdatedPublishesToConsentUpdateTopicWithPurposes() {

        List<String> purposes = Arrays.asList("marketing", "analytics");

        publisher.onConsentUpdated("tenant.example", "consent-1", "ACTIVE", "ACTIVE", "jdoe", purposes);

        verify(eventPublishService).publishEvent(eq("tenant.example"), eq("tenant.example"),
                eq(DefaultTopic.CONSENT_UPDATE.getName()), eq(purposes), any());
    }

    @Test
    public void onConsentRevokedPublishesToConsentRevokeTopicWithPurposes() {

        List<String> purposes = Arrays.asList("marketing");

        publisher.onConsentRevoked("tenant.example", "consent-1", "ACTIVE", "jdoe", purposes);

        verify(eventPublishService).publishEvent(eq("tenant.example"), eq("tenant.example"),
                eq(DefaultTopic.CONSENT_REVOKE.getName()), eq(purposes), any());
    }

    @Test
    public void onConsentExpiredPublishesToConsentExpireTopicWithPurposes() {

        List<String> purposes = Arrays.asList("analytics");

        publisher.onConsentExpired("tenant.example", "consent-1", "ACTIVE", purposes);

        verify(eventPublishService).publishEvent(eq("tenant.example"), eq("tenant.example"),
                eq(DefaultTopic.CONSENT_EXPIRE.getName()), eq(purposes), any());
    }

    @Test
    public void onUserDataChangedPublishesToUserDataChangeTopicWithNullPurposes() {

        publisher.onUserDataChanged("tenant.example", "user-1", Arrays.asList("claim-uri"));

        verify(eventPublishService).publishEvent(eq("tenant.example"), eq("tenant.example"),
                eq(DefaultTopic.USER_DATA_CHANGE.getName()), isNull(), any());
    }

    @Test
    public void onUserAccountDeletedPublishesToUserAccountDeleteTopicWithNullPurposes() {

        publisher.onUserAccountDeleted("tenant.example", "user-1");

        verify(eventPublishService).publishEvent(eq("tenant.example"), eq("tenant.example"),
                eq(DefaultTopic.USER_ACCOUNT_DELETE.getName()), isNull(), any());
    }

    @Test
    public void groupIdIsAlwaysTheOrgId() {

        publisher.onUserAccountDeleted("a-different-tenant", "user-1");

        verify(eventPublishService).publishEvent(eq("a-different-tenant"), eq("a-different-tenant"),
                any(), any(), any());
    }

    @Test
    public void doesNotCatchAFailureFromEventPublishService() {

        doThrow(new RuntimeException("publish failed")).when(eventPublishService)
                .publishEvent(any(), any(), any(), any(), any());

        assertThrows(RuntimeException.class,
                () -> publisher.onUserAccountDeleted("tenant.example", "user-1"));
    }
}
