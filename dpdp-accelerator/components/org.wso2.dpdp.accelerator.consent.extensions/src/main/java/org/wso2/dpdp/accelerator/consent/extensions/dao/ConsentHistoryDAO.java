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

package org.wso2.dpdp.accelerator.consent.extensions.dao;

import org.wso2.dpdp.accelerator.consent.extensions.dao.models.ConsentHistoryRecord;
import org.wso2.dpdp.accelerator.consent.extensions.dao.models.ConsentStatusAuditRecord;

import java.sql.Connection;
import java.util.List;

/**
 * Every method takes the {@link Connection} as its first parameter - this DAO never opens or
 * manages its own connection, the service layer owns the transaction. Methods throw unchecked
 * {@code ConsentHistoryData*Exception}s (see that package) rather than declaring them, matching
 * the Event Notification/Complaint DAO interfaces.
 */
public interface ConsentHistoryDAO {

    void insertStatusAudit(Connection connection, ConsentStatusAuditRecord record);

    void insertHistorySnapshot(Connection connection, ConsentHistoryRecord record);

    List<ConsentStatusAuditRecord> getStatusAuditHistory(Connection connection, String orgId, String consentId,
            int limit, int offset);

    int getStatusAuditHistoryCount(Connection connection, String orgId, String consentId);

    List<ConsentHistoryRecord> getConsentHistory(Connection connection, String orgId, String consentId, int limit,
            int offset);

    int getConsentHistoryCount(Connection connection, String orgId, String consentId);
}
