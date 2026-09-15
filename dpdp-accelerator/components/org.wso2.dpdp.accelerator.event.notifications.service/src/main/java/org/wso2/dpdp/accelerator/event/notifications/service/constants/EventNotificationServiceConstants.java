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

package org.wso2.dpdp.accelerator.event.notifications.service.constants;

/**
 * Event Notification Service Constants matching WSO2 Accelerator standards.
 */
public class EventNotificationServiceConstants {

        private EventNotificationServiceConstants() {
        }

        public static final long WEBHOOK_HTTP_TIMEOUT_SECONDS = 5;
        public static final int RETRY_BACKOFF_MULTIPLIER = 3;

        // Error Codes
        public static final String ERROR_CODE_INVALID_REQUEST = "EN-4001";
        public static final String ERROR_CODE_MISSING_REQUIRED_PARAM = "EN-4002";
        public static final String ERROR_CODE_INVALID_STATE = "EN-4003";
        public static final String ERROR_CODE_RESOURCE_NOT_FOUND = "EN-4040";
        public static final String ERROR_CODE_TOPIC_NOT_FOUND = "EN-4041";
        public static final String ERROR_CODE_DELIVERY_NOT_FOUND = "EN-4042";
        public static final String ERROR_CODE_EVENT_NOT_FOUND = "EN-4043";
        public static final String ERROR_CODE_RESOURCE_EXISTS = "EN-4090";
        public static final String ERROR_CODE_WEBHOOK_VERIFICATION_FAILED = "EN-4220";
        public static final String ERROR_CODE_INVALID_SIGNATURE = "EN-4010";
        public static final String ERROR_CODE_EVENT_PUBLISH_FAILED = "EN-5001";
        public static final String ERROR_CODE_INTERNAL_ERROR = "EN-5000";

        // Error Titles
        public static final String ERROR_TITLE_MALFORMED_REQUEST = "Malformed request";
        public static final String ERROR_TITLE_VALIDATION_FAILED = "Validation failed";
        public static final String ERROR_TITLE_INVALID_STATE = "Invalid state";
        public static final String ERROR_TITLE_RESOURCE_NOT_FOUND = "Resource not found";
        public static final String ERROR_TITLE_TOPIC_NOT_FOUND = "Topic not found";
        public static final String ERROR_TITLE_DELIVERY_NOT_FOUND = "Delivery not found";
        public static final String ERROR_TITLE_EVENT_NOT_FOUND = "Event not found";
        public static final String ERROR_TITLE_RESOURCE_EXISTS = "Resource already exists";
        public static final String ERROR_TITLE_DUPLICATE_SUBSCRIPTION = "Duplicate subscription";
        public static final String ERROR_TITLE_TOPIC_ALREADY_EXISTS = "Topic already exists";
        public static final String ERROR_TITLE_WEBHOOK_VERIFICATION_FAILED = "Webhook verification failed";
        public static final String ERROR_TITLE_EVENT_PUBLISH_FAILED = "Event publish failed";
        public static final String ERROR_TITLE_INTERNAL_ERROR = "Internal error";
        public static final String ERROR_TITLE_CONCURRENT_MUTATION = "Concurrent state mutation";
        public static final String ERROR_TITLE_IN_FLIGHT_DELIVERIES = "In-flight deliveries exist";
        public static final String ERROR_TITLE_OPERATION_FORBIDDEN = "Operation forbidden";

        // Error Messages & Descriptions
        public static final String ORG_ID_OR_TOPIC_NAME_MISSING_ERROR_MSG = "Org ID and topic name are required.";
        public static final String TOPIC_ID_MISSING_ERROR_MSG = "Topic ID is required.";
        public static final String TOPIC_ALREADY_EXISTS_ERROR_MSG = "A topic with the specified name already exists for this organization.";
        public static final String FAILED_TO_CREATE_TOPIC_ERROR_MSG = "Failed to create topic in database.";
        public static final String FAILED_TO_DEREGISTER_TOPIC_ERROR_MSG = "Failed to deregister topic.";
        public static final String ORG_ID_MISSING_ERROR_MSG = "Organization ID is required.";
        public static final String GROUP_ID_MISSING_ERROR_MSG = "Group ID is required.";
        public static final String SUBSCRIPTION_ID_MISSING_ERROR_MSG = "Subscription ID is required.";
        public static final String DELIVERY_ID_MISSING_ERROR_MSG = "Delivery ID is required.";
        public static final String EVENT_ID_MISSING_ERROR_MSG = "Event ID is required.";
        public static final String EVENT_NOT_FOUND_ERROR_MSG = "No event exists with the specified ID for this organization.";
        public static final String SUBSCRIPTION_NOT_FOUND_ERROR_MSG = "No subscription exists with the specified ID for this organization.";
        public static final String DELIVERY_NOT_FOUND_ERROR_MSG = "No delivery exists with the specified ID for this subscription.";
        public static final String DELIVERY_MANUAL_RETRY_NOT_ELIGIBLE_ERROR_MSG =
                "Manual retry is only available once after a webhook delivery exhausts all automatic retries.";
        public static final String DELIVERY_MANUAL_RETRY_UNAVAILABLE_ERROR_MSG =
                "The webhook delivery worker is not available.";
        public static final String ONLY_STALE_SUBSCRIPTIONS_VERIFIABLE_ERROR_MSG = "Only subscriptions in 'stale' state can be re-verified.";
        public static final String NO_CALLBACK_URL_ERROR_MSG = "Subscription does not have a callback URL — re-verification is only applicable to webhook subscriptions.";
        public static final String WEBHOOK_VERIFICATION_FAILED_ERROR_MSG = "Webhook intent verification failed for callback URL.";
        public static final String EVENT_PUBLISH_FAILED_ERROR_MSG = "Failed to publish event.";
        public static final String EVENT_TOPIC_NOT_FOUND_ERROR_MSG = "No topic exists with name '%s' for this org.";
        public static final String EVENT_PAYLOAD_REQUIRED_ERROR_MSG = "Event payload is required.";
        public static final String CALLBACK_URL_REQUIRED_ERROR_MSG = "callbackUrl is required when delivery mode is WEBHOOK.";
        public static final String SHARED_SECRET_REQUIRED_ERROR_MSG = "sharedSecret is required for webhook and poll delivery modes.";
        public static final String DUPLICATE_SUBSCRIPTION_ERROR_MSG = "A subscription with the same parameters already exists.";
        public static final String MIXED_DELIVERY_MODE_SUBSCRIPTION_ERROR_MSG = "A subscriber cannot use both webhook and poll delivery modes for the same topic.";
        public static final String FAILED_TO_DELETE_SUBSCRIPTION_ERROR_MSG = "Failed to delete subscription.";
        public static final String CALLBACK_URL_HTTPS_REQUIRED_ERROR_MSG = "callbackUrl must use HTTPS scheme in production environment.";
        public static final String WEBHOOK_CHALLENGE_MISMATCH_ERROR_MSG = "Callback URL did not echo back challenge string.";
        public static final String TOPIC_NOT_FOUND_ERROR_MSG = "No topic exists with ID '%s' for this org.";
        public static final String SYSTEM_TOPIC_DELETE_FORBIDDEN_ERROR_MSG = "System topic '%s' is system-defined and cannot be deleted or deactivated.";
        public static final String SYSTEM_TOPIC_NAME_CONFLICT_ERROR_MSG = "Topic '%s' already exists but is not an active system topic.";
        public static final String TOPIC_ALREADY_DEREGISTERED_ERROR_MSG = "Topic '%s' is already deregistered.";
        public static final String SUBSCRIPTION_IN_FLIGHT_DELIVERIES_ERROR_MSG = "Subscription has pending or in-flight deliveries and cannot be deleted until they complete.";
        public static final String SUBSCRIPTION_CONCURRENT_MODIFICATION_ERROR_MSG = "Subscription status was modified concurrently by another operation.";
        public static final String TOPIC_HAS_ACTIVE_SUBSCRIPTIONS_ERROR_MSG = "Topic '%s' has active subscriptions and cannot be deregistered. Delete or complete all subscriptions for this topic first.";
        public static final String TOPIC_NOT_ACTIVE_ERROR_MSG = "Topic '%s' is not active and cannot accept new subscriptions.";
        public static final String FILTER_PURPOSES_REQUIRED_FOR_SPECIFIC_ERROR_MSG = "filter.purposes must contain at least one entry when filter.type is SPECIFIC.";
        public static final String FILTER_PURPOSES_REQUIRED_FOR_EXCEPT_ERROR_MSG = "filter.purposes must contain at least one entry when filter.type is EXCEPT.";
        public static final String POLL_ACK_ERROR_OVERLAP_ERROR_MSG = "A delivery cannot be present in both ack and setErrs.";
        public static final String POLL_ERROR_DETAIL_REQUIRED_ERROR_MSG = "An error code and description are required for every delivery in setErrs.";
        public static final String COMPLETION_STATUS_REQUIRED_ERROR_MSG = "completionStatus is required.";
        public static final String COMPLETION_EVIDENCE_REQUIRED_ERROR_MSG = "completionEvidence is required.";
        public static final String COMPLETION_EVIDENCE_INVALID_ERROR_MSG = "completionEvidence must be a valid HTTPS URL no longer than 512 characters.";
        public static final String DELIVERY_COMPLETION_INVALID_STATE_ERROR_MSG = "Completion can only be submitted for a delivered webhook delivery.";
        public static final String DELIVERY_COMPLETION_ALREADY_EXISTS_ERROR_MSG = "A completion report already exists for this delivery.";
        public static final String INVALID_SIGNATURE_ERROR_MSG = "The event-signature is invalid.";
        public static final String POLLING_SERVICES_NOT_INITIALIZED_ERROR_MSG = "Polling services are not initialized.";
        public static final String POLLING_REQUEST_BODY_MALFORMED_ERROR_MSG = "Polling request body is malformed.";
        public static final String ORG_MISMATCH_TENANT_CONTEXT_ERROR_MSG =
                "The request organization does not match the tenant context.";
        public static final String LONG_POLLING_NOT_SUPPORTED_ERROR_MSG =
                "Long polling is not supported; returnImmediately must be true.";
        public static final String POLLING_SUBSCRIPTION_NO_SECRET_ERROR_MSG =
                "The polling subscription does not have a shared secret.";
        public static final String DELIVERY_COMPLETION_SERVICES_NOT_INITIALIZED_ERROR_MSG =
                "Delivery completion services are not initialized.";
        public static final String COMPLETION_REQUEST_BODY_REQUIRED_ERROR_MSG = "Completion request body is required.";
        public static final String COMPLETION_REQUEST_BODY_MALFORMED_ERROR_MSG = "Completion request body is malformed.";
        public static final String COMPLETION_STATUS_INVALID_CHOICE_ERROR_MSG =
                "completionStatus must be completed, ack, disputed, or partial.";
        public static final String COMPLETION_COMPLETED_AT_NEGATIVE_ERROR_MSG = "completedAt must not be negative.";
        public static final int MAX_POLL_ERROR_CODE_LENGTH = 64;
        public static final int MAX_POLL_ERROR_DETAIL_LENGTH = 1024;
}
