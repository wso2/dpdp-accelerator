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

package org.wso2.dpdp.accelerator.consent.extensions.service;

import org.wso2.dpdp.accelerator.consent.extensions.dao.models.ConsentHistoryRecord;
import org.wso2.dpdp.accelerator.consent.extensions.dao.models.ConsentStatusAuditRecord;
import org.wso2.dpdp.accelerator.consent.extensions.service.constants.ConsentHistoryServiceConstants.ActionType;
import org.wso2.dpdp.accelerator.consent.extensions.service.models.PagedResult;

/**
 * Every method takes {@code tenantDomain} explicitly - callers (the consent listener, the
 * endpoint webapp) resolve it themselves; this service never resolves it on its own. Methods
 * throw unchecked {@code ConsentHistoryData*Exception}s rather than declaring them, matching the
 * Event Notification/Complaint service interfaces.
 */
public interface ConsentHistoryService {

    void recordStatusAudit(String tenantDomain, String consentId, String previousStatus, String currentStatus,
            ActionType actionType, String actionBy);

    /**
     * No-ops if snapshot recording is disabled in config.
     */
    void recordHistorySnapshot(String tenantDomain, String consentId, ActionType actionType, String snapshotJson,
            String actionBy);

    PagedResult<ConsentStatusAuditRecord> getStatusAuditHistory(String tenantDomain, String consentId, int limit,
            int offset);

    PagedResult<ConsentHistoryRecord> getConsentHistory(String tenantDomain, String consentId, int limit, int offset);
}
