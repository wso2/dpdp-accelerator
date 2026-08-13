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

package org.wso2.dpdp.accelerator.complaint.mgt.dao.queries;

import org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.DAOConstants;

public class QueryConstants {

    private QueryConstants() {
    }

    // ---- COMPLAINT ----
    public static final String ADD_COMPLAINT =
            "INSERT INTO COMPLAINT (COMPLAINT_ID, ORG_ID, USER_ID, REFERENCE_ID, CATEGORY, PRIORITY, STATUS, " +
            "DESCRIPTION, CREATED_TIME, UPDATED_TIME, STATUTORY_DUE_TIME) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    public static final String GET_COMPLAINT_BY_ID =
            "SELECT COMPLAINT_ID, ORG_ID, USER_ID, REFERENCE_ID, CATEGORY, PRIORITY, STATUS, DESCRIPTION, " +
            "CREATED_TIME, UPDATED_TIME, STATUTORY_DUE_TIME FROM COMPLAINT WHERE COMPLAINT_ID = ? AND ORG_ID = ?";

    public static final String COUNT_COMPLAINTS_FOR_YEAR_PREFIX =
            "SELECT COUNT(*) FROM COMPLAINT WHERE ORG_ID = ? AND REFERENCE_ID LIKE ?";

    public static final String UPDATE_COMPLAINT_STATUS =
            "UPDATE COMPLAINT SET STATUS = ?, UPDATED_TIME = ? WHERE COMPLAINT_ID = ? AND ORG_ID = ?";

    public static final String LIST_COMPLAINTS_BASE =
            "SELECT COMPLAINT_ID, ORG_ID, USER_ID, REFERENCE_ID, CATEGORY, PRIORITY, STATUS, DESCRIPTION, " +
            "CREATED_TIME, UPDATED_TIME, STATUTORY_DUE_TIME FROM COMPLAINT WHERE ORG_ID = ? ";

    public static final String COUNT_COMPLAINTS_BASE =
            "SELECT COUNT(*) FROM COMPLAINT WHERE ORG_ID = ? ";

    // ---- COMPLAINT_EVENT ----
    public static final String ADD_COMPLAINT_EVENT =
            "INSERT INTO COMPLAINT_EVENT (" + DAOConstants.COLUMN_COMPLAINT_EVENT_ID + ", ORG_ID, COMPLAINT_ID, " +
            "ACTOR_USER_ID, ACTOR_ROLE, IS_PUBLIC, COMMENT, FROM_STATUS, TO_STATUS, ACTION_TIME) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    public static final String GET_COMPLAINT_EVENT_BY_ID =
            "SELECT " + DAOConstants.COLUMN_COMPLAINT_EVENT_ID + ", ORG_ID, COMPLAINT_ID, ACTOR_USER_ID, " +
            "ACTOR_ROLE, IS_PUBLIC, COMMENT, FROM_STATUS, TO_STATUS, ACTION_TIME FROM COMPLAINT_EVENT " +
            "WHERE " + DAOConstants.COLUMN_COMPLAINT_EVENT_ID + " = ? AND ORG_ID = ? AND COMPLAINT_ID = ?";

    public static final String LIST_COMPLAINT_EVENTS_BASE =
            "SELECT " + DAOConstants.COLUMN_COMPLAINT_EVENT_ID + ", ORG_ID, COMPLAINT_ID, ACTOR_USER_ID, " +
            "ACTOR_ROLE, IS_PUBLIC, COMMENT, FROM_STATUS, TO_STATUS, ACTION_TIME FROM COMPLAINT_EVENT " +
            "WHERE ORG_ID = ? AND COMPLAINT_ID = ? ";

    public static final String COUNT_COMPLAINT_EVENTS_BASE =
            "SELECT COUNT(*) FROM COMPLAINT_EVENT WHERE ORG_ID = ? AND COMPLAINT_ID = ? ";

    // ---- COMPLAINT_ATTACHMENT ----
    public static final String ADD_COMPLAINT_ATTACHMENT =
            "INSERT INTO COMPLAINT_ATTACHMENT (ATTACHMENT_ID, ORG_ID, COMPLAINT_ID, " +
            DAOConstants.COLUMN_COMPLAINT_EVENT_ID + ", FILE_NAME, FILE_CONTENT_TYPE, FILE_DATA, CREATED_TIME) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    public static final String GET_ATTACHMENT_METADATA_BY_ID =
            "SELECT ATTACHMENT_ID, ORG_ID, COMPLAINT_ID, " + DAOConstants.COLUMN_COMPLAINT_EVENT_ID + ", " +
            "FILE_NAME, FILE_CONTENT_TYPE, LENGTH(FILE_DATA) AS SIZE_BYTES, CREATED_TIME " +
            "FROM COMPLAINT_ATTACHMENT WHERE ATTACHMENT_ID = ? AND ORG_ID = ? AND COMPLAINT_ID = ?";

    public static final String GET_ATTACHMENT_WITH_DATA_BY_ID =
            "SELECT ATTACHMENT_ID, ORG_ID, COMPLAINT_ID, " + DAOConstants.COLUMN_COMPLAINT_EVENT_ID + ", " +
            "FILE_NAME, FILE_CONTENT_TYPE, FILE_DATA, CREATED_TIME FROM COMPLAINT_ATTACHMENT " +
            "WHERE ATTACHMENT_ID = ? AND ORG_ID = ? AND COMPLAINT_ID = ?";

    public static final String LIST_ATTACHMENT_METADATA_BY_COMPLAINT =
            "SELECT ATTACHMENT_ID, ORG_ID, COMPLAINT_ID, " + DAOConstants.COLUMN_COMPLAINT_EVENT_ID + ", " +
            "FILE_NAME, FILE_CONTENT_TYPE, LENGTH(FILE_DATA) AS SIZE_BYTES, CREATED_TIME " +
            "FROM COMPLAINT_ATTACHMENT WHERE ORG_ID = ? AND COMPLAINT_ID = ? AND " +
            DAOConstants.COLUMN_COMPLAINT_EVENT_ID + " IS NULL ORDER BY CREATED_TIME ASC";

    public static final String LIST_ATTACHMENT_METADATA_BY_EVENT =
            "SELECT ATTACHMENT_ID, ORG_ID, COMPLAINT_ID, " + DAOConstants.COLUMN_COMPLAINT_EVENT_ID + ", " +
            "FILE_NAME, FILE_CONTENT_TYPE, LENGTH(FILE_DATA) AS SIZE_BYTES, CREATED_TIME " +
            "FROM COMPLAINT_ATTACHMENT WHERE ORG_ID = ? AND COMPLAINT_ID = ? AND " +
            DAOConstants.COLUMN_COMPLAINT_EVENT_ID + " = ? ORDER BY CREATED_TIME ASC";
}
