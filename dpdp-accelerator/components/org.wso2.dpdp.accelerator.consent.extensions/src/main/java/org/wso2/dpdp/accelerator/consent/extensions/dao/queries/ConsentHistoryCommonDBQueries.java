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

package org.wso2.dpdp.accelerator.consent.extensions.dao.queries;

import org.wso2.dpdp.accelerator.consent.extensions.dao.constants.ConsentHistoryDAOConstants;

/**
 * ANSI-baseline SQL for the consent history tables. Dialect-specific subclasses override only the
 * queries that actually diverge; {@link ConsentHistoryQueryFactory} resolves which one to use per
 * connection. Instance methods (not {@code static final} constants) are what make that override
 * possible without touching callers.
 */
public class ConsentHistoryCommonDBQueries {

    public String getInsertStatusAuditQuery() {

        return "INSERT INTO " + ConsentHistoryDAOConstants.STATUS_AUDIT_TABLE + " ("
                + ConsentHistoryDAOConstants.COLUMN_AUDIT_ID + ", " + ConsentHistoryDAOConstants.COLUMN_CONSENT_ID
                + ", " + ConsentHistoryDAOConstants.COLUMN_ORG_ID + ", "
                + ConsentHistoryDAOConstants.COLUMN_PREVIOUS_STATUS + ", "
                + ConsentHistoryDAOConstants.COLUMN_CURRENT_STATUS + ", "
                + ConsentHistoryDAOConstants.COLUMN_ACTION_TYPE + ", " + ConsentHistoryDAOConstants.COLUMN_ACTION_BY
                + ", " + ConsentHistoryDAOConstants.COLUMN_ACTION_TIME + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    }

    public String getInsertHistorySnapshotQuery() {

        return "INSERT INTO " + ConsentHistoryDAOConstants.HISTORY_TABLE + " ("
                + ConsentHistoryDAOConstants.COLUMN_HISTORY_ID + ", " + ConsentHistoryDAOConstants.COLUMN_CONSENT_ID
                + ", " + ConsentHistoryDAOConstants.COLUMN_ORG_ID + ", "
                + ConsentHistoryDAOConstants.COLUMN_ACTION_TYPE + ", " + ConsentHistoryDAOConstants.COLUMN_SNAPSHOT
                + ", " + ConsentHistoryDAOConstants.COLUMN_ACTION_BY + ", "
                + ConsentHistoryDAOConstants.COLUMN_ACTION_TIME + ") VALUES (?, ?, ?, ?, ?, ?, ?)";
    }

    public String getStatusAuditHistoryQuery() {

        return "SELECT " + ConsentHistoryDAOConstants.COLUMN_AUDIT_ID + ", "
                + ConsentHistoryDAOConstants.COLUMN_CONSENT_ID + ", " + ConsentHistoryDAOConstants.COLUMN_ORG_ID
                + ", " + ConsentHistoryDAOConstants.COLUMN_PREVIOUS_STATUS + ", "
                + ConsentHistoryDAOConstants.COLUMN_CURRENT_STATUS + ", "
                + ConsentHistoryDAOConstants.COLUMN_ACTION_TYPE + ", " + ConsentHistoryDAOConstants.COLUMN_ACTION_BY
                + ", " + ConsentHistoryDAOConstants.COLUMN_ACTION_TIME + " FROM "
                + ConsentHistoryDAOConstants.STATUS_AUDIT_TABLE + " WHERE "
                + ConsentHistoryDAOConstants.COLUMN_CONSENT_ID
                + " = ? AND " + ConsentHistoryDAOConstants.COLUMN_ORG_ID + " = ? ORDER BY "
                + ConsentHistoryDAOConstants.COLUMN_ACTION_TIME + " DESC LIMIT ? OFFSET ?";
    }

    public String getStatusAuditHistoryCountQuery() {

        return "SELECT COUNT(*) AS " + ConsentHistoryDAOConstants.COLUMN_TOTAL_COUNT + " FROM "
                + ConsentHistoryDAOConstants.STATUS_AUDIT_TABLE + " WHERE "
                + ConsentHistoryDAOConstants.COLUMN_CONSENT_ID
                + " = ? AND " + ConsentHistoryDAOConstants.COLUMN_ORG_ID + " = ?";
    }

    public String getConsentHistoryQuery() {

        return "SELECT " + ConsentHistoryDAOConstants.COLUMN_HISTORY_ID + ", "
                + ConsentHistoryDAOConstants.COLUMN_CONSENT_ID + ", " + ConsentHistoryDAOConstants.COLUMN_ORG_ID
                + ", " + ConsentHistoryDAOConstants.COLUMN_ACTION_TYPE + ", "
                + ConsentHistoryDAOConstants.COLUMN_SNAPSHOT
                + ", " + ConsentHistoryDAOConstants.COLUMN_ACTION_BY + ", "
                + ConsentHistoryDAOConstants.COLUMN_ACTION_TIME
                + " FROM " + ConsentHistoryDAOConstants.HISTORY_TABLE + " WHERE "
                + ConsentHistoryDAOConstants.COLUMN_CONSENT_ID + " = ? AND " + ConsentHistoryDAOConstants.COLUMN_ORG_ID
                + " = ? ORDER BY " + ConsentHistoryDAOConstants.COLUMN_ACTION_TIME + " DESC LIMIT ? OFFSET ?";
    }

    public String getConsentHistoryCountQuery() {

        return "SELECT COUNT(*) AS " + ConsentHistoryDAOConstants.COLUMN_TOTAL_COUNT + " FROM "
                + ConsentHistoryDAOConstants.HISTORY_TABLE + " WHERE " + ConsentHistoryDAOConstants.COLUMN_CONSENT_ID
                + " = ? AND " + ConsentHistoryDAOConstants.COLUMN_ORG_ID + " = ?";
    }
}
