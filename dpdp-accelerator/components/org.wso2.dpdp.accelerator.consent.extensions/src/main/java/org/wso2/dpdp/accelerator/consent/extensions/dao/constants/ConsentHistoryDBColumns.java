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

package org.wso2.dpdp.accelerator.consent.extensions.dao.constants;

/**
 * Column names for {@code DPDP_CONSENT_STATUS_AUDIT} and {@code DPDP_CONSENT_HISTORY}, split out
 * of {@link ConsentHistoryDAOConstants} to mirror {@code EventNotificationDBColumns}/
 * {@code ComplaintDBColumns} - table names and columns are kept in separate classes there too.
 */
public final class ConsentHistoryDBColumns {

    public static final String COLUMN_AUDIT_ID = "AUDIT_ID";
    public static final String COLUMN_HISTORY_ID = "HISTORY_ID";
    public static final String COLUMN_CONSENT_ID = "CONSENT_ID";
    public static final String COLUMN_ORG_ID = "ORG_ID";
    public static final String COLUMN_PREVIOUS_STATUS = "PREVIOUS_STATUS";
    public static final String COLUMN_CURRENT_STATUS = "CURRENT_STATUS";
    public static final String COLUMN_ACTION_TYPE = "ACTION_TYPE";
    public static final String COLUMN_ACTION_BY = "ACTION_BY";
    public static final String COLUMN_ACTION_TIME = "ACTION_TIME";
    public static final String COLUMN_SNAPSHOT = "SNAPSHOT";
    public static final String COLUMN_TOTAL_COUNT = "TOTAL_COUNT";

    private ConsentHistoryDBColumns() {

    }
}
