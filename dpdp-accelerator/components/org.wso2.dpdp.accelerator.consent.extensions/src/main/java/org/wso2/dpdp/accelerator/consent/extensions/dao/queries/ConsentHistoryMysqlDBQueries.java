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

/**
 * MySQL dialect query provider for consent history. No query text differs from the ANSI/H2
 * baseline today (see {@code consent-history/h2.sql} vs {@code consent-history/mysql.sql} - the
 * DML is identical, only the DDL diverges), so this class overrides nothing; it exists as the
 * override point {@link ConsentHistoryQueryFactory} routes to for a future MySQL-specific quirk.
 * Mirrors {@code ComplaintMysqlDBQueries}.
 */
public class ConsentHistoryMysqlDBQueries extends ConsentHistoryCommonDBQueries {
}
