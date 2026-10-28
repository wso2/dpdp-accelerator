/**
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 * <p>
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 *     http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.dpdp.accelerator.event.notifications.common.enums;

/**
 * System topics provisioned for each ordinary WSO2 tenant.
 */
public enum DefaultTopic {

    CONSENT_CREATE("consent.create", "Consent creation and initial state notifications"),
    CONSENT_UPDATE("consent.update", "Consent update and state transition notifications"),
    CONSENT_REVOKE("consent.revoke", "Consent revocation and withdrawal notifications"),
    CONSENT_EXPIRE("consent.expire", "Consent expiration notifications"),
    USER_DATA_CHANGE("user.data.change", "User data modification and profile change notifications"),
    USER_ACCOUNT_DELETE("user.account.delete", "User account deletion and right-to-be-forgotten notifications");

    private final String name;
    private final String description;

    DefaultTopic(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}
