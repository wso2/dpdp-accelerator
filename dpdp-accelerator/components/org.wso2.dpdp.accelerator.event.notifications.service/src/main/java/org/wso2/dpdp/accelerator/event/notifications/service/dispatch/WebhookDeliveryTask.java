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

package org.wso2.dpdp.accelerator.event.notifications.service.dispatch;

import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.common.util.DatabaseUtils;
import org.wso2.dpdp.accelerator.common.util.LogSanitizer;
import org.wso2.dpdp.accelerator.event.notifications.service.constants.EventNotificationServiceConstants;

import org.wso2.dpdp.accelerator.event.notifications.common.enums.DeliveryStatus;
import org.wso2.dpdp.accelerator.event.notifications.common.util.HmacSigner;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDelivery;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDeliveryAudit;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Single-delivery unit of work. Pulled off the {@link DeliveryDAO} batch by
 * {@link WebhookDeliveryWorker} and executed on the shared
 * {@link java.util.concurrent.ScheduledExecutorService}.
 *
 * <p>For each invocation the task:</p>
 * <ol>
 *   <li>POSTs the payload to the subscriber's callback URL with an HMAC-SHA256 signature in
 *       the {@code event-signature} header.</li>
 *   <li>Writes exactly one {@code WEBHOOK_DELIVERY_AUDIT} row regardless of outcome.</li>
 *   <li>On a 2xx success, marks the delivery {@code delivered} with the current timestamp.</li>
 *   <li>On a non-2xx response or an exception, increments {@code ATTEMPT_COUNT}, computes the
 *       next retry timestamp with exponential backoff, and either releases the row back to
 *       {@code pending} (if retries remain) or marks it terminal {@code failed}.</li>
 * </ol>
 *
 * <p>Mirror of the shape used by
 * {@code SubscriptionServiceImpl.WebhookVerificationTask}, on purpose, so the two retry
 * subsystems stay easy to compare.</p>
 */
public class WebhookDeliveryTask implements Runnable {

    private static final String MALFORMED_PAYLOAD_RESPONSE_CODE = "MALFORMED_PAYLOAD";

    private static final Log LOG = LogFactory.getLog(WebhookDeliveryTask.class);

    // Constants kept here (not in EventNotificationCommonConstants) so they stay scoped to
    // outbound HTTP delivery and don't leak into the common module.
    private static final String EVENT_SIGNATURE_HEADER = "event-signature";
    private static final String DELIVERY_ID_HEADER = "Delivery-Id";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String RESPONSE_CODE_EXCEPTION = "EXCEPTION";
    private static final String RESPONSE_CODE_MISSING_SHARED_SECRET = "MISSING_SECRET";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(
            EventNotificationServiceConstants.WEBHOOK_HTTP_TIMEOUT_SECONDS);

    // ObjectMapper is thread-safe for serialization after construction (Jackson docs guarantee
    // this). Sharing a single static instance avoids the overhead of instantiating a new mapper
    // for every delivery task while keeping it scoped to this class.
    private static final com.fasterxml.jackson.databind.ObjectMapper ENVELOPE_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final WebhookDelivery delivery;
    private final String orgId;
    private final String groupId;
    private final String payload;
    private final String callbackUrl;
    private final String sharedSecret;
    private final String topic;
    private final DeliveryDAO deliveryDAO;
    private final HttpClient httpClient;
    private final DPDPConfigurationService configurationService;
    private final EventPayloadSigner payloadSigner;

    public WebhookDeliveryTask(WebhookDelivery delivery, String orgId, String groupId, String payload, String callbackUrl,
            String sharedSecret, String topic, DeliveryDAO deliveryDAO,
            HttpClient httpClient) {
        this(delivery, orgId, groupId, payload, callbackUrl, sharedSecret, topic, deliveryDAO,
                httpClient, new org.wso2.dpdp.accelerator.common.config.DPDPConfigurationServiceImpl(false),
                new IdentityServerPayloadSigner());
    }

    public WebhookDeliveryTask(WebhookDelivery delivery, String orgId, String groupId, String payload,
            String callbackUrl,
            String sharedSecret, String topic, DeliveryDAO deliveryDAO,
            HttpClient httpClient, DPDPConfigurationService configurationService) {
        this(delivery, orgId, groupId, payload, callbackUrl, sharedSecret, topic, deliveryDAO,
                httpClient, configurationService, new IdentityServerPayloadSigner());
    }

    WebhookDeliveryTask(WebhookDelivery delivery, String orgId, String groupId, String payload, String callbackUrl,
            String sharedSecret, String topic, DeliveryDAO deliveryDAO,
            HttpClient httpClient, DPDPConfigurationService configurationService,
            EventPayloadSigner payloadSigner) {
        this.delivery = delivery;
        this.orgId = orgId;
        this.groupId = groupId;
        this.payload = payload;
        this.callbackUrl = callbackUrl;
        this.sharedSecret = sharedSecret;
        this.topic = topic;
        this.deliveryDAO = deliveryDAO;
        this.httpClient = httpClient;
        this.configurationService = configurationService;
        this.payloadSigner = payloadSigner;
    }

    @Override
    public void run() {
        String responseCode;
        try {
            responseCode = dispatch();
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.debug("Webhook delivery attempt failed for delivery ["
                    + LogSanitizer.sanitize(delivery.getDeliveryId()) + "]: "
                    + LogSanitizer.sanitize(e.getMessage()));
            if (e instanceof MalformedPayloadException) {
                recordPermanentFailure(MALFORMED_PAYLOAD_RESPONSE_CODE);
            } else if (e instanceof MissingSharedSecretException) {
                recordPermanentFailure(RESPONSE_CODE_MISSING_SHARED_SECRET);
            } else {
                recordFailure(RESPONSE_CODE_EXCEPTION, null);
            }
            return;
        }

        int httpStatus = Integer.parseInt(responseCode);
        if (httpStatus >= 200 && httpStatus < 300) {
            recordSuccess(httpStatus);
        } else {
            recordFailure(responseCode, null);
        }
    }

    /**
     * Builds the event payload, signs it, and returns the HTTP status code as a string.
     * Interrupts and IO errors propagate to the caller.
     *
     * <p>When certificate signing is enabled, the body contains only {@code signedPayload}.
     * The compact JWS embeds the complete event envelope and its HMAC-SHA256 hash, avoiding
     * a second unsigned copy of the event data. The {@code event-signature} header remains
     * an HMAC over the exact HTTP body for subscription-level authentication.</p>
     */
    private String dispatch() throws Exception {
        if (sharedSecret == null || sharedSecret.trim().isEmpty()) {
            throw new MissingSharedSecretException();
        }
        String body = buildEnvelope();
        if (configurationService.isEventNotificationPayloadSigningEnabled()) {
            Map<String, Object> signedBody = new LinkedHashMap<>();
            signedBody.put("signedPayload", new SignedEventPayloadFactory(payloadSigner).sign(
                    orgId, groupId, delivery.getSubscriptionId(), delivery.getDeliveryId(),
                    delivery.getEventId(), topic, payload, sharedSecret,
                    configurationService.getEventNotificationPayloadSigningAudience()));
            body = ENVELOPE_MAPPER.writeValueAsString(signedBody);
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(callbackUrl))
                .timeout(HTTP_TIMEOUT)
                .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_JSON)
                .header(DELIVERY_ID_HEADER, delivery.getDeliveryId())
                .POST(HttpRequest.BodyPublishers.ofString(body));
        String signature = HmacSigner.sign(sharedSecret, body);
        builder.header(EVENT_SIGNATURE_HEADER, "sha256=" + signature);
        HttpResponse<Void> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.discarding());
        return String.valueOf(response.statusCode());
    }

    /**
     * Wraps the raw event payload plus accelerator-managed routing metadata in a single
     * JSON object. The original payload is parsed back into a {@code JsonNode} so it stays
     * an object/array/scalar under {@code "eventPayload"} — not a stringified blob — preserving
     * receivers' ability to inspect the original event without a second JSON parse.
     *
     * <p>LinkedHashMap preserves field order so the serialized envelope is stable across
     * runs (helpful for snapshot tests and for receivers diffing the body byte-for-byte).
     * If the raw payload is null or not parseable JSON, the delivery is marked permanently
     * failed.</p>
     */
    private String buildEnvelope() throws Exception {
        try {
            return SignedEventPayloadFactory.buildEnvelopeJson(orgId, groupId, delivery.getSubscriptionId(),
                    delivery.getDeliveryId(), delivery.getEventId(), topic, payload);
        } catch (Exception parseFailure) {
            LOG.debug("Event payload for delivery [" + LogSanitizer.sanitize(delivery.getDeliveryId())
                    + "] was not parseable JSON; marking delivery as permanently failed.");
            throw new MalformedPayloadException("Event payload is not valid JSON.", parseFailure);
        }
    }

    private void recordPermanentFailure(String responseCode) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        WebhookDelivery failed = new WebhookDelivery(
                delivery.getDeliveryId(), delivery.getSubscriptionId(), delivery.getEventId(),
                DeliveryStatus.FAILED.getValue(), delivery.getAttemptCount() + 1, null,
                delivery.getCreatedAt(), now, null);
        try {
            DatabaseUtils.<Void>executeInTransaction(conn -> {
                deliveryDAO.recordPermanentFailure(conn, newAudit(now, responseCode), failed);
                return null;
            });
        } catch (RuntimeException e) {
            LOG.error("Failed to mark malformed delivery [" + LogSanitizer.sanitize(delivery.getDeliveryId())
                    + "] as failed: " + LogSanitizer.sanitize(e.getMessage()), e);
        }
    }

    private static final class MalformedPayloadException extends Exception {

        private MalformedPayloadException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final class MissingSharedSecretException extends Exception {

        private MissingSharedSecretException() {
            super("Webhook shared secret is missing.");
        }
    }

    private void recordSuccess(int httpStatus) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        WebhookDeliveryAudit audit = newAudit(now, String.valueOf(httpStatus));
        WebhookDelivery updated = new WebhookDelivery(
                delivery.getDeliveryId(),
                delivery.getSubscriptionId(),
                delivery.getEventId(),
                DeliveryStatus.DELIVERED.getValue(),
                delivery.getAttemptCount() + 1,
                null,
                delivery.getCreatedAt(),
                now,
                now,
                delivery.isManualRetryUsed());
        try {
            boolean recorded = DatabaseUtils.<Boolean>executeInTransaction(conn ->
                    deliveryDAO.recordSuccessfulAttempt(conn, audit, updated));
            if (recorded) {
                LOG.info("Webhook delivered [delivery=" + LogSanitizer.sanitize(delivery.getDeliveryId()) + ", event="
                        + LogSanitizer.sanitize(delivery.getEventId()) + ", topic=" + LogSanitizer.sanitize(topic) + ", attempt="
                        + updated.getAttemptCount() + ", status=" + httpStatus + "].");
            } else {
                LOG.debug("recordSuccessfulAttempt returned false for delivery ["
                        + LogSanitizer.sanitize(delivery.getDeliveryId()) + "].");
            }
        } catch (RuntimeException e) {
            LOG.error("Failed to record successful attempt for delivery ["
                    + LogSanitizer.sanitize(delivery.getDeliveryId()) + "]: "
                    + LogSanitizer.sanitize(e.getMessage()), e);
        }
    }

    private void recordFailure(String responseCode, Throwable cause) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        WebhookDeliveryAudit audit = newAudit(now, responseCode);
        int newAttempt = delivery.getAttemptCount() + 1;
        int maxRetries = configurationService.getEventNotificationMaxRetries();
        if (newAttempt > maxRetries) {
            WebhookDelivery failed = new WebhookDelivery(
                    delivery.getDeliveryId(),
                    delivery.getSubscriptionId(),
                    delivery.getEventId(),
                    DeliveryStatus.FAILED.getValue(),
                    newAttempt,
                    null,
                    delivery.getCreatedAt(),
                    now,
                    null,
                    delivery.isManualRetryUsed());
            try {
                DatabaseUtils.<Void>executeInTransaction(conn -> {
                    deliveryDAO.recordPermanentFailure(conn, audit, failed);
                    return null;
                });
                LOG.debug("Webhook delivery [" + LogSanitizer.sanitize(delivery.getDeliveryId()) + "] exhausted "
                        + maxRetries + " retries after " + newAttempt
                        + " attempts; marked as failed (last response="
                        + LogSanitizer.sanitize(responseCode) + ").");
            } catch (RuntimeException e) {
                LOG.error("Failed to mark delivery [" + LogSanitizer.sanitize(delivery.getDeliveryId())
                        + "] as failed: " + LogSanitizer.sanitize(e.getMessage()), e);
            }
            return;
        }

        long baseBackoffSeconds = configurationService.getEventNotificationBaseBackoffSeconds();
        long delaySeconds = baseBackoffSeconds * (long) Math.pow(
                EventNotificationServiceConstants.RETRY_BACKOFF_MULTIPLIER, newAttempt - 1);
        Timestamp nextRetryAt = new Timestamp(now.getTime() + delaySeconds * 1000L);

        try {
            boolean released = DatabaseUtils.<Boolean>executeInTransaction(conn ->
                    deliveryDAO.recordRetryableFailure(conn, audit, delivery.getDeliveryId(), newAttempt, nextRetryAt));
            if (released) {
                LOG.debug("Webhook delivery [" + LogSanitizer.sanitize(delivery.getDeliveryId()) + "] attempt " + newAttempt
                        + " failed (response=" + LogSanitizer.sanitize(responseCode) + "); next retry at " + nextRetryAt
                        + " (~" + delaySeconds + "s).");
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Webhook delivery [" + LogSanitizer.sanitize(delivery.getDeliveryId())
                            + "] was no longer in_flight; release was a no-op.");
                }
            }
        } catch (RuntimeException e) {
            LOG.error("Failed to release webhook delivery [" + LogSanitizer.sanitize(delivery.getDeliveryId())
                    + "] for retry: " + LogSanitizer.sanitize(e.getMessage()), e);
        }
    }

    private WebhookDeliveryAudit newAudit(Timestamp now, String responseCode) {
        return new WebhookDeliveryAudit(
                UUID.randomUUID().toString(),
                delivery.getEventId(),
                delivery.getDeliveryId(),
                orgId,
                responseCode,
                now,
                now);
    }
}
