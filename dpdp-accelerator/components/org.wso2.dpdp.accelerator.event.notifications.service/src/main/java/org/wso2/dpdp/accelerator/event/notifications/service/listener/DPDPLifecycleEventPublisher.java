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

import org.wso2.dpdp.accelerator.event.notifications.common.enums.DefaultTopic;
import org.wso2.dpdp.accelerator.event.notifications.common.listener.DPDPLifecycleEventListener;
import org.wso2.dpdp.accelerator.event.notifications.service.EventPublishService;

import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fans the 5 DPDP lifecycle events into the Event Notification Framework, one
 * {@link EventPublishService#publishEvent} call per {@link DefaultTopic}. {@code groupId} is
 * always the tenant domain, per this framework's own convention for system-generated events.
 *
 * <p>Registered by {@code EventNotificationServiceComponent}, gated by
 * {@code EventNotifications.LifecycleEvents.PublishingEnabled}.
 *
 * <p>Does not catch anything itself - every caller already wraps its own try/catch, so catching
 * here too would double-log the same failure.
 */
public class DPDPLifecycleEventPublisher implements DPDPLifecycleEventListener {

    private final EventPublishService eventPublishService;

    public DPDPLifecycleEventPublisher(EventPublishService eventPublishService) {

        this.eventPublishService = eventPublishService;
    }

    @Override
    public void onConsentCreated(String orgId, String consentId, String status, String actionBy,
            List<String> purposes) {

        Map<String, Object> payload = new HashMap<>();
        payload.put("consentId", consentId);
        payload.put("currentStatus", status);
        eventPublishService.publishEvent(orgId, orgId, DefaultTopic.CONSENT_CREATE.getName(), purposes, payload);
    }

    @Override
    public void onConsentUpdated(String orgId, String consentId, String previousStatus, String currentStatus,
            String actionBy, List<String> purposes) {

        Map<String, Object> payload = new HashMap<>();
        payload.put("consentId", consentId);
        payload.put("previousStatus", previousStatus);
        payload.put("currentStatus", currentStatus);
        eventPublishService.publishEvent(orgId, orgId, DefaultTopic.CONSENT_UPDATE.getName(), purposes, payload);
    }

    @Override
    public void onConsentRevoked(String orgId, String consentId, String previousStatus, String actionBy,
            List<String> purposes) {

        Map<String, Object> payload = new HashMap<>();
        payload.put("consentId", consentId);
        payload.put("previousStatus", previousStatus);
        eventPublishService.publishEvent(orgId, orgId, DefaultTopic.CONSENT_REVOKE.getName(), purposes, payload);
    }

    @Override
    public void onConsentExpired(String orgId, String consentId, String previousStatus, List<String> purposes) {

        Map<String, Object> payload = new HashMap<>();
        payload.put("consentId", consentId);
        payload.put("previousStatus", previousStatus);
        eventPublishService.publishEvent(orgId, orgId, DefaultTopic.CONSENT_EXPIRE.getName(), purposes, payload);
    }

    @Override
    public void onUserDataChanged(String orgId, String userId, List<String> changedClaimUris) {

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("changedClaimUris", changedClaimUris);
        // No purposes dimension for user events - subscribers must use ALL-mode filtering.
        eventPublishService.publishEvent(orgId, orgId, DefaultTopic.USER_DATA_CHANGE.getName(), null, payload);
    }

    @Override
    public void onUserAccountDeleted(String orgId, String userId) {

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        eventPublishService.publishEvent(orgId, orgId, DefaultTopic.USER_ACCOUNT_DELETE.getName(), null, payload);
    }

    @Override
    public void onConsentExpired(Connection connection, String orgId, String consentId, String previousStatus,
            List<String> purposes) {

        Map<String, Object> payload = new HashMap<>();
        payload.put("consentId", consentId);
        payload.put("previousStatus", previousStatus);
        eventPublishService.publishEvent(connection, orgId, orgId, DefaultTopic.CONSENT_EXPIRE.getName(), purposes,
                payload);
    }
}
