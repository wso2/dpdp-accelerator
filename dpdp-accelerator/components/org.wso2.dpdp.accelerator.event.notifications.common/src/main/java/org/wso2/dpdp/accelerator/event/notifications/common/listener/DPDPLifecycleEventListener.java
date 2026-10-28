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

package org.wso2.dpdp.accelerator.event.notifications.common.listener;

import java.sql.Connection;
import java.util.List;

/**
 * DPDP-internal capture point for the notification-worthy lifecycle events: consent
 * created/updated/revoked/expired, and user data changed/account deleted. Not an IS-registered
 * listener - DPDP's own hooks call it after their real work is done, typically to publish into
 * the Event Notification Framework.
 *
 * <p>{@code purposes} is only meaningful for the 4 consent methods (resolved from the consent's
 * {@code Receipt}); the 2 user methods have no processing-purpose dimension, so a
 * purpose-filtered subscription will never match them - expected, not a bug.
 */
public interface DPDPLifecycleEventListener {

    void onConsentCreated(String orgId, String consentId, String status, String actionBy,
            List<String> purposes);

    void onConsentUpdated(String orgId, String consentId, String previousStatus, String currentStatus,
            String actionBy, List<String> purposes);

    void onConsentRevoked(String orgId, String consentId, String previousStatus, String actionBy,
            List<String> purposes);

    void onConsentExpired(String orgId, String consentId, String previousStatus, List<String> purposes);

    void onUserDataChanged(String orgId, String userId, List<String> changedClaimUris);

    void onUserAccountDeleted(String orgId, String userId);

    /**
     * Publishes expiry in the caller's transaction. Implementations must propagate failures and
     * must not commit or close the connection. Older listeners fail explicitly rather than
     * silently publishing in a separate transaction.
     */
    default void onConsentExpired(Connection connection, String orgId, String consentId, String previousStatus,
            List<String> purposes) {

        throw new UnsupportedOperationException("Transactional consent expiry publication is not supported.");
    }
}
