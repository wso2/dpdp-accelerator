/**
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.dpdp.accelerator.event.notifications.dao.constants;

/** Database column names shared by Event Notification DAO row mappings. */
public final class EventNotificationDBColumns {

    public static final String ACK_ID = "ACK_ID";
    public static final String AUDIT_ID = "AUDIT_ID";
    public static final String ATTEMPT_AT = "ATTEMPT_AT";
    public static final String ATTEMPT_COUNT = "ATTEMPT_COUNT";
    public static final String MANUAL_RETRY_USED = "MANUAL_RETRY_USED";
    public static final String CALLBACK_URL = "CALLBACK_URL";
    public static final String COMPLETED_AT = "COMPLETED_AT";
    public static final String COMPLETION_EVIDENCE = "COMPLETION_EVIDENCE";
    public static final String COMPLETION_STATUS = "COMPLETION_STATUS";
    public static final String CREATED_AT = "CREATED_AT";
    public static final String CURRENT_STATUS = "CURRENT_STATUS";
    public static final String DELIVERED_AT = "DELIVERED_AT";
    public static final String DELIVERY_CREATED_AT = "DELIVERY_CREATED_AT";
    public static final String DELIVERY_ID = "DELIVERY_ID";
    public static final String DELIVERY_MODE = "DELIVERY_MODE";
    public static final String DELIVERIES_COUNT = "DELIVERIES_COUNT";
    public static final String DESCRIPTION = "DESCRIPTION";
    public static final String EVENT_ID = "EVENT_ID";
    public static final String ERROR_DETAIL = "ERROR_DETAIL";
    public static final String ERROR_CODE = "ERROR_CODE";
    public static final String GROUP_ID = "GROUP_ID";
    public static final String INITIATED_BY = "INITIATED_BY";
    public static final String NAME = "NAME";
    public static final String NEXT_RETRY_AT = "NEXT_RETRY_AT";
    public static final String OCCURRED_AT = "OCCURRED_AT";
    public static final String ORG_ID = "ORG_ID";
    public static final String PAYLOAD = "PAYLOAD";
    public static final String PURPOSE_FILTER_MODE = "PURPOSE_FILTER_MODE";
    public static final String PURPOSE_NAME = "PURPOSE_NAME";
    public static final String PURPOSE_SET_HASH = "PURPOSE_SET_HASH";
    public static final String RESPONSE_CODE = "RESPONSE_CODE";
    public static final String SHARED_SECRET = "SHARED_SECRET";
    public static final String STATUS = "STATUS";
    public static final String SUBSCRIPTION_ID = "SUBSCRIPTION_ID";
    public static final String TOPIC_ID = "TOPIC_ID";
    public static final String TOPIC_NAME = "TOPIC_NAME";
    public static final String UPDATED_AT = "UPDATED_AT";

    private EventNotificationDBColumns() {
    }
}
