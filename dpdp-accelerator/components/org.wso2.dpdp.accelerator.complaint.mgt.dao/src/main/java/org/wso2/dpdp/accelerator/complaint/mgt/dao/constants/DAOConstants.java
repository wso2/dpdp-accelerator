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

package org.wso2.dpdp.accelerator.complaint.mgt.dao.constants;

public class DAOConstants {

    private DAOConstants() {
    }

    // Table Names
    public static final String TABLE_COMPLAINT = "COMPLAINT";
    public static final String TABLE_COMPLAINT_EVENT = "COMPLAINT_EVENT";
    public static final String TABLE_COMPLAINT_ATTACHMENT = "COMPLAINT_ATTACHMENT";

    // Column Names
    // Prefixed with COMPLAINT_ (rather than plain EVENT_ID) so it doesn't read as a hook into
    // some generic event-notification system - it is purely the PK of COMPLAINT_EVENT.
    public static final String COLUMN_COMPLAINT_EVENT_ID = "COMPLAINT_EVENT_ID";

    // ComplaintStatus, ComplaintPriority, and ComplaintActorRole each have their own enum type
    // (see the ComplaintStatus/ComplaintPriority/ComplaintActorRole classes in this package) -
    // no parallel String constants here to avoid the two drifting out of sync.

    // ComplaintTimelineEntryType (derived, not stored as a column - see ComplaintEvent mapping)
    public static final String ENTRY_TYPE_STATUS_CHANGE = "STATUS_CHANGE";
    public static final String ENTRY_TYPE_COMMENT = "COMMENT";
    public static final String ENTRY_TYPE_INTERNAL_NOTE = "INTERNAL_NOTE";
}
