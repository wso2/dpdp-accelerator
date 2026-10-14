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

package org.wso2.dpdp.accelerator.event.notifications.common.constants;


/**
 * Common constant definitions for WSO2 DPDP Event Notification Framework.
 */
public class EventNotificationCommonConstants {

    public static final int MAX_VERIFICATION_REQUEST_BYTES = 262144;
    public static final String SUBSCRIPTION_VERIFICATION_TYPE = "subscription.verification";

    private EventNotificationCommonConstants() {
    }

    // Data Access Error Messages
    public static final String ERROR_ADDING_TOPIC = "Error adding topic [%s]";
    public static final String ERROR_GETTING_TOPIC_BY_ID = "Error getting topic by ID [%s]";
    public static final String ERROR_GETTING_TOPIC_BY_ORG_AND_NAME = "Error getting topic by org [%s] and name [%s]";
    public static final String ERROR_UPDATING_TOPIC_STATUS = "Error updating topic status for ID [%s]";
    public static final String ERROR_DELETING_TOPIC = "Error deleting topic [%s]";
    public static final String ERROR_DEREGISTERING_TOPIC = ERROR_DELETING_TOPIC;
    public static final String ERROR_LISTING_TOPICS = "Error listing topics for org [%s]";

    public static final String ERROR_ADDING_SUBSCRIPTION = "Error adding subscription [%s]";
    public static final String ERROR_GETTING_SUBSCRIPTION_BY_ID = "Error getting subscription by ID [%s]";
    public static final String ERROR_UPDATING_SUBSCRIPTION_STATUS = "Error updating subscription status for ID [%s]";
    public static final String ERROR_DELETING_SUBSCRIPTION = "Error deleting subscription [%s]";
    public static final String ERROR_LISTING_SUBSCRIPTIONS = "Error listing subscriptions for org [%s]";
    public static final String ERROR_GETTING_SUBSCRIPTIONS_BY_ORG_AND_TOPIC = "Error getting subscriptions by org [%s] and topic [%s]";
    public static final String ERROR_GETTING_PURPOSES_BY_SUBSCRIPTION_ID = "Error getting purposes for subscription [%s]";
    public static final String ERROR_GETTING_PURPOSES_BY_BATCH_SUBSCRIPTION_IDS = "Error getting purposes for batch subscription IDs";
    public static final String ERROR_CHECKING_PENDING_DELIVERIES_FOR_SUBSCRIPTION = "Error checking pending deliveries for subscription [%s]";
    public static final String ERROR_GETTING_PENDING_SUBSCRIPTIONS_FOR_RECOVERY = "Error getting pending subscriptions for recovery";

    public static final String ERROR_ADDING_DELIVERY_ACK = "Error adding delivery ack [%s]";
    public static final String ERROR_GETTING_DELIVERY_ACK_BY_DELIVERY_ID = "Error getting delivery ack by delivery ID [%s]";

    public static final String ERROR_ADDING_WEBHOOK_DELIVERY = "Error adding webhook delivery [%s]";
    public static final String ERROR_GETTING_WEBHOOK_DELIVERY = "Error getting webhook delivery [%s]";
    public static final String ERROR_GETTING_PENDING_WEBHOOK_DELIVERIES = "Error getting pending webhook deliveries";
    public static final String ERROR_UPDATING_WEBHOOK_DELIVERY_STATUS = "Error updating webhook delivery status for [%s]";
    public static final String ERROR_ADDING_WEBHOOK_DELIVERY_AUDIT = "Error adding webhook delivery audit for delivery [%s]";
    public static final String ERROR_GETTING_WEBHOOK_DELIVERY_AUDITS = "Error getting webhook delivery audits for delivery [%s]";
    public static final String ERROR_ADDING_POLL_DELIVERY = "Error adding poll delivery [%s]";
    public static final String ERROR_GETTING_POLL_DELIVERY = "Error getting poll delivery [%s]";
    public static final String ERROR_GETTING_PENDING_POLL_DELIVERIES = "Error getting pending poll deliveries for group [%s]";
    public static final String ERROR_UPDATING_POLL_DELIVERY_STATUSES = "Error updating poll delivery statuses for group [%s]";
    public static final String ERROR_UPDATING_POLL_DELIVERY_STATUS = "Error updating poll delivery status for [%s]";
    public static final String ERROR_OVERLAPPING_POLL_COMPLETION_DELIVERY_IDS =
            "A delivery ID cannot be present in both acknowledged and error completion lists.";
    public static final String ERROR_LISTING_DELIVERIES_FOR_SUBSCRIPTION = "Error listing deliveries for subscription [%s]";
    public static final String ERROR_GETTING_SUBSCRIPTION_DELIVERY = "Error getting subscription delivery [%s]";
    public static final String ERROR_LISTING_ORG_DELIVERIES = "Error listing org deliveries for org [%s]";
    public static final String ERROR_GETTING_ORG_DELIVERY = "Error getting org delivery [%s]";
    public static final String ERROR_ADDING_EVENT = "Error adding event [%s]";
    public static final String ERROR_GETTING_EVENT_BY_ID = "Error getting event by ID [%s]";
    public static final String ERROR_ADDING_EVENT_PURPOSES = "Error adding event purposes for event [%s]";
    public static final String ERROR_GETTING_EVENT_PURPOSES = "Error getting event purposes for event [%s]";
    public static final String ERROR_HAS_ACTIVE_EVENTS_FOR_TOPIC = "Error checking active events for topic [%s]";
    public static final String ERROR_LISTING_EVENTS = "Error listing events for org [%s]";
    public static final String ERROR_SUBSCRIPTION_OVERLAPPING_PURPOSES = "An active subscription with overlapping purposes already exists for this group/topic.";
    public static final String ERROR_SUBSCRIPTION_MIXED_DELIVERY_MODE =
            "A subscriber cannot use both webhook and poll delivery modes for the same topic.";
    public static final String ERROR_DUPLICATE_SUBSCRIPTION = "A subscription with the same parameters already exists.";
    public static final String ERROR_SUBSCRIPTION_NAME_ALREADY_EXISTS = "A subscription with the name '%s' already exists.";
    public static final String ERROR_SUBSCRIPTION_NAME_NULL_OR_EMPTY = "Subscription name must not be null or empty.";
    public static final String ERROR_DELIVERY_ACK_ALREADY_EXISTS = "Completion evidence already acknowledged for this delivery.";
    public static final String ERROR_TOPIC_HAS_ACTIVE_SUBSCRIPTIONS = "Topic has active subscriptions and cannot be deleted.";
    public static final String ERROR_TOPIC_NOT_ACTIVE = "Topic is not active and cannot accept new subscriptions.";
    public static final String ERROR_TOPIC_NULL = "Topic must not be null.";
    public static final String ERROR_TOPIC_STATUS_NULL = "Topic status must not be null.";
    public static final String ERROR_TOPIC_ALREADY_EXISTS = "Topic already exists: %s";
    public static final String ERROR_UNKNOWN_ENUM_VALUE = "Unknown %s: %s";

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;
    public static final int MAX_COMPLETION_EVIDENCE_URL_LENGTH = 512;
}
