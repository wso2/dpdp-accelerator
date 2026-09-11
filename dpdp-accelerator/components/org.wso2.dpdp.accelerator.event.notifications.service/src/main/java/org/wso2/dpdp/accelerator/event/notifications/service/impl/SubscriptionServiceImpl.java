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

package org.wso2.dpdp.accelerator.event.notifications.service.impl;

import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.common.util.DatabaseUtils;
import org.wso2.dpdp.accelerator.common.util.LogSanitizer;
import org.wso2.dpdp.accelerator.event.notifications.common.constants.EventNotificationCommonConstants;
import org.wso2.dpdp.accelerator.common.util.HTTPClientUtils;
import org.wso2.dpdp.accelerator.event.notifications.common.util.EventNotificationUrlValidator;
import org.wso2.dpdp.accelerator.event.notifications.common.util.CallbackUrlCanonicalizer;
import org.wso2.dpdp.accelerator.event.notifications.common.util.PurposeOverlapUtils;
import org.wso2.dpdp.accelerator.event.notifications.common.util.WebhookVerificationUriBuilder;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryAckDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.PaginatedDAOResult;
import org.wso2.dpdp.accelerator.event.notifications.dao.SubscriptionDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.TopicDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Subscription;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.SubscriptionDeliverySummary;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Topic;
import org.wso2.dpdp.accelerator.event.notifications.service.SubscriptionService;
import org.wso2.dpdp.accelerator.event.notifications.service.constants.EventNotificationServiceConstants;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.DeliveryConfigDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.FilterDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDeliveryDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionEventHistoryDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dispatch.WebhookDeliveryWorker;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.DeliveryMode;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.PurposeFilterMode;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.SubscriptionStatus;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.TopicStatus;
import org.wso2.dpdp.accelerator.event.notifications.common.exception.EventNotificationDuplicateResourceException;
import org.wso2.dpdp.accelerator.event.notifications.common.exception.EventNotificationInvalidStateException;
import org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException;
import org.wso2.dpdp.accelerator.event.notifications.service.model.PaginatedResult;
import org.wso2.dpdp.accelerator.event.notifications.service.util.EventNotificationParameterUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.sql.Connection;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class SubscriptionServiceImpl implements SubscriptionService {

    private static final Log LOG = LogFactory.getLog(SubscriptionServiceImpl.class);

    private SubscriptionDAO subscriptionDAO;
    private TopicDAO topicDAO;
    private DeliveryDAO deliveryDAO;
    private DeliveryAckDAO deliveryAckDAO;
    private DPDPConfigurationService configurationService;

    private ScheduledExecutorService scheduler;
    private HttpClient httpClient;
    private ManualRetryDispatcher manualRetryDispatcher;

    @FunctionalInterface
    public interface ManualRetryDispatcher {
        WebhookDeliveryWorker.ManualRetrySubmissionResult submit(String orgId, String subscriptionId,
                String deliveryId);
    }

    public SubscriptionServiceImpl() {
    }

    public SubscriptionServiceImpl(SubscriptionDAO subscriptionDAO, TopicDAO topicDAO,
            DeliveryDAO deliveryDAO, DeliveryAckDAO deliveryAckDAO) {
        this(subscriptionDAO, topicDAO, deliveryDAO, deliveryAckDAO,
                new org.wso2.dpdp.accelerator.common.config.DPDPConfigurationServiceImpl(false));
    }

    public SubscriptionServiceImpl(SubscriptionDAO subscriptionDAO, TopicDAO topicDAO,
            DeliveryDAO deliveryDAO, DeliveryAckDAO deliveryAckDAO,
            DPDPConfigurationService configurationService) {
        this.subscriptionDAO = subscriptionDAO;
        this.topicDAO = topicDAO;
        this.deliveryDAO = deliveryDAO;
        this.deliveryAckDAO = deliveryAckDAO;
        this.configurationService = configurationService;
    }

    public void start() {
        int poolSize = getConfiguration().getEventNotificationThreadPoolSize();
        this.scheduler = Executors.newScheduledThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "webhook-verification-pool");
            t.setDaemon(true);
            return t;
        });

        this.httpClient = HTTPClientUtils.getHttpClient();
    }

    public void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
    }

    public void setManualRetryDispatcher(ManualRetryDispatcher manualRetryDispatcher) {
        this.manualRetryDispatcher = manualRetryDispatcher;
    }

    @Override
    public SubscriptionDTO createSubscription(String orgId, String groupId, String topicName,
            FilterDTO filter, DeliveryConfigDTO delivery) {
        if (orgId == null || orgId.trim().isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.ORG_ID_MISSING_ERROR_MSG, 400);
        }
        String effectiveGroupId = (groupId != null && !groupId.trim().isEmpty())
                ? groupId.trim()
                : orgId.trim();
        if (topicName == null || topicName.trim().isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.ORG_ID_OR_TOPIC_NAME_MISSING_ERROR_MSG, 400);
        }

        PurposeFilterMode filterType = (filter != null && filter.getType() != null) ? filter.getType()
                : PurposeFilterMode.ALL;
        List<String> purposes = (filter != null && filter.getPurposes() != null) ? filter.getPurposes()
                : Collections.emptyList();
        DeliveryMode deliveryMode = (delivery != null && delivery.getMode() != null) ? delivery.getMode()
                : DeliveryMode.WEBHOOK;
        String callbackUrl = (delivery != null) ? delivery.getCallbackUrl() : null;
        String sharedSecret = (delivery != null) ? delivery.getSharedSecret() : null;

        if (deliveryMode == DeliveryMode.WEBHOOK) {
            validateCallbackUrl(callbackUrl);
        }
        if (sharedSecret == null || sharedSecret.trim().isEmpty()) {
            throw new EventNotificationException(
                    EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_VALIDATION_FAILED,
                    EventNotificationServiceConstants.SHARED_SECRET_REQUIRED_ERROR_MSG, 400);
        }

        validatePurposeFilterMode(filterType, purposes);

        String initialStatus = (deliveryMode == DeliveryMode.WEBHOOK)
                ? SubscriptionStatus.PENDING.getValue()
                : SubscriptionStatus.ACTIVE.getValue();

        String subscriptionId = UUID.randomUUID().toString();
        Subscription[] sub = new Subscription[1];

        DatabaseUtils.<Void>executeInTransaction(conn -> {
            Optional<Topic> topicOpt = topicDAO.getTopicByOrgAndName(conn, orgId.trim(), topicName.trim());
            if (!topicOpt.isPresent() || TopicStatus.DEREGISTERED.getValue().equalsIgnoreCase(topicOpt.get().getStatus())) {
                throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_TOPIC_NOT_FOUND,
                        EventNotificationServiceConstants.ERROR_TITLE_TOPIC_NOT_FOUND,
                        "Topic '" + topicName + "' is not registered for this org.", 404);
            }

            Topic topic = topicOpt.get();
            List<Subscription> existingSubs = subscriptionDAO.getLiveSubscriptionsByOrgAndTopic(conn, orgId.trim(),
                    topic.getTopicId());
            validateDuplicateAndConflict(existingSubs, effectiveGroupId, filterType, purposes, deliveryMode, callbackUrl);

            sub[0] = new Subscription(subscriptionId, orgId.trim(), effectiveGroupId, topic.getTopicId(),
                    filterType.getValue(), purposes, deliveryMode.getValue(),
                    callbackUrl != null ? callbackUrl.trim() : null,
                    sharedSecret != null ? sharedSecret.trim() : null,
                    initialStatus, null, null);

            try {
                subscriptionDAO.addSubscription(conn, sub[0]);
            } catch (EventNotificationInvalidStateException e) {
                // The DAO detected a deregistered/inactive topic under the row lock — a
                // concurrent TopicService.deleteTopic committed between our service-layer
                // pre-check and the FOR UPDATE acquisition in the DAO. Map to 409.
                throw new EventNotificationException(
                        EventNotificationServiceConstants.ERROR_CODE_INVALID_STATE,
                        EventNotificationServiceConstants.ERROR_TITLE_INVALID_STATE,
                        String.format(EventNotificationServiceConstants.TOPIC_NOT_ACTIVE_ERROR_MSG, topicName.trim()),
                        409);
            } catch (EventNotificationDuplicateResourceException e) {
                String conflictMessage = EventNotificationCommonConstants.ERROR_SUBSCRIPTION_MIXED_DELIVERY_MODE
                        .equals(e.getMessage())
                                ? EventNotificationServiceConstants.MIXED_DELIVERY_MODE_SUBSCRIPTION_ERROR_MSG
                                : EventNotificationServiceConstants.DUPLICATE_SUBSCRIPTION_ERROR_MSG;
                throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_RESOURCE_EXISTS,
                        EventNotificationServiceConstants.ERROR_TITLE_DUPLICATE_SUBSCRIPTION,
                        conflictMessage, 409);
            }
            return null;
        });

        if (deliveryMode == DeliveryMode.WEBHOOK) {
            scheduleWebhookVerificationTask(subscriptionId, orgId.trim(), callbackUrl.trim(), topicName.trim(), 0);
        }

        return mapToDTO(sub[0], topicName.trim());
    }

    private void validatePurposeFilterMode(PurposeFilterMode filterType, List<String> purposes) {
        Set<String> purposesSet = PurposeOverlapUtils.canonicalize(purposes);
        if (filterType == PurposeFilterMode.SPECIFIC && purposesSet.isEmpty()) {
            throw new EventNotificationException(
                    EventNotificationServiceConstants.ERROR_CODE_MISSING_REQUIRED_PARAM,
                    EventNotificationServiceConstants.ERROR_TITLE_VALIDATION_FAILED,
                    EventNotificationServiceConstants.FILTER_PURPOSES_REQUIRED_FOR_SPECIFIC_ERROR_MSG, 422);
        }
        if (filterType == PurposeFilterMode.EXCEPT && purposesSet.isEmpty()) {
            throw new EventNotificationException(
                    EventNotificationServiceConstants.ERROR_CODE_MISSING_REQUIRED_PARAM,
                    EventNotificationServiceConstants.ERROR_TITLE_VALIDATION_FAILED,
                    EventNotificationServiceConstants.FILTER_PURPOSES_REQUIRED_FOR_EXCEPT_ERROR_MSG, 422);
        }
    }

    private void validateDuplicateAndConflict(List<Subscription> existingSubs, String groupId,
            PurposeFilterMode filterType, List<String> purposes,
            DeliveryMode deliveryMode, String callbackUrl) {
        Set<String> newPurposesSet = PurposeOverlapUtils.canonicalize(purposes);
        String normalizedCallbackUrl = CallbackUrlCanonicalizer.canonicalize(callbackUrl);

        for (Subscription existing : existingSubs) {
            String existingGroupId = existing.getGroupId() != null ? existing.getGroupId().trim() : "";
            if (!groupId.trim().equalsIgnoreCase(existingGroupId)) {
                continue;
            }

            String existingStatus = existing.getStatus() != null
                    ? existing.getStatus().toLowerCase(java.util.Locale.ROOT) : "";
            if (!SubscriptionStatus.ACTIVE.getValue().toLowerCase(java.util.Locale.ROOT).equals(existingStatus)
                    && !SubscriptionStatus.PENDING.getValue().toLowerCase(java.util.Locale.ROOT).equals(existingStatus)
                    && !SubscriptionStatus.STALE.getValue().toLowerCase(java.util.Locale.ROOT).equals(existingStatus)) {
                continue;
            }

            DeliveryMode existingDeliveryMode = DeliveryMode.fromValueOrDefault(existing.getDeliveryMode(),
                    DeliveryMode.WEBHOOK);

            if (existingDeliveryMode != deliveryMode) {
                throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_RESOURCE_EXISTS,
                        EventNotificationServiceConstants.ERROR_TITLE_DUPLICATE_SUBSCRIPTION,
                        EventNotificationServiceConstants.MIXED_DELIVERY_MODE_SUBSCRIPTION_ERROR_MSG, 409);
            }

            if (deliveryMode == DeliveryMode.WEBHOOK) {
                String existingCallbackUrl = CallbackUrlCanonicalizer.canonicalize(existing.getCallbackUrl());
                if (!normalizedCallbackUrl.equals(existingCallbackUrl)) {
                    continue;
                }
            }

            PurposeFilterMode existingFilterMode = PurposeFilterMode.fromValueOrDefault(existing.getPurposeFilterMode(),
                    PurposeFilterMode.ALL);
            Set<String> existingPurposesSet = PurposeOverlapUtils.canonicalize(existing.getPurposes());

            if (PurposeOverlapUtils.overlaps(filterType, newPurposesSet, existingFilterMode, existingPurposesSet)) {
                throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_RESOURCE_EXISTS,
                        EventNotificationServiceConstants.ERROR_TITLE_DUPLICATE_SUBSCRIPTION,
                        EventNotificationServiceConstants.DUPLICATE_SUBSCRIPTION_ERROR_MSG, 409);
            }
        }
    }

    private void scheduleWebhookVerificationTask(String subscriptionId, String orgId, String callbackUrl,
            String topicName,
            int attempt) {
        if (scheduler == null || scheduler.isShutdown()) {
            return;
        }
        long delaySeconds = 0;
        if (attempt > 0) {
            delaySeconds = getConfiguration().getEventNotificationBaseBackoffSeconds()
                    * (long) Math.pow(EventNotificationServiceConstants.RETRY_BACKOFF_MULTIPLIER, attempt - 1);
        }
        scheduler.schedule(new WebhookVerificationTask(subscriptionId, orgId, callbackUrl, topicName, attempt),
                delaySeconds,
                TimeUnit.SECONDS);
    }

    private class WebhookVerificationTask implements Runnable {
        private final String subscriptionId;
        private final String orgId;
        private final String callbackUrl;
        private final String topicName;
        private final int attempt;

        WebhookVerificationTask(String subscriptionId, String orgId, String callbackUrl, String topicName,
                int attempt) {
            this.subscriptionId = subscriptionId;
            this.orgId = orgId;
            this.callbackUrl = callbackUrl;
            this.topicName = topicName;
            this.attempt = attempt;
        }

        @Override
        public void run() {
            int maxRetries = getConfiguration().getEventNotificationMaxRetries();
            VerificationAttemptResult result = executeVerificationAttempt(subscriptionId, orgId,
                    SubscriptionStatus.PENDING.getValue(), callbackUrl, topicName,
                    attempt >= maxRetries);
            if (!result.claimed) {
                LOG.debug("Webhook verification skipped for subscription ["
                        + LogSanitizer.sanitize(subscriptionId) + "] because it is no longer PENDING.");
                return;
            }
            if (result.success) {
                LOG.info("Webhook verification succeeded for subscription ["
                        + LogSanitizer.sanitize(subscriptionId) + "] on attempt " + (attempt + 1) + ".");
                return;
            }
            int nextAttempt = attempt + 1;
            if (nextAttempt <= maxRetries) {
                LOG.debug("Webhook verification attempt " + (attempt + 1)
                        + " failed for subscription [" + LogSanitizer.sanitize(subscriptionId)
                        + "]. Retrying. Reason: " + LogSanitizer.sanitize(result.failure.getMessage()));
                scheduleWebhookVerificationTask(subscriptionId, orgId, callbackUrl, topicName, nextAttempt);
            } else {
                LOG.debug("Exhausted all retries for subscription [" + LogSanitizer.sanitize(subscriptionId)
                        + "]. Marked as STALE.");
            }
        }
    }

    /**
     * Keeps the subscription row locked for the complete bounded verification call.
     * This intentionally spans the HTTP request: without a dedicated lease column it
     * is the only portable way to prevent another IS node from issuing the same
     * verification concurrently. The request timeout bounds the lock duration.
     */
    private VerificationAttemptResult executeVerificationAttempt(String subscriptionId, String orgId,
            String expectedStatus, String callbackUrl, String topicName, boolean markStaleOnFailure) {

        return DatabaseUtils.executeInTransaction(connection -> {
            VerificationAttemptResult result;
            Optional<Subscription> locked = subscriptionDAO.lockSubscriptionForVerification(connection,
                    subscriptionId, orgId, expectedStatus);
            if (!locked.isPresent()) {
                result = VerificationAttemptResult.notClaimed();
            } else {
                try {
                    verifyWebhookCallback(callbackUrl, topicName);
                    boolean updated = subscriptionDAO.updateSubscriptionStatus(connection, subscriptionId, orgId,
                            expectedStatus, SubscriptionStatus.ACTIVE.getValue());
                    result = updated ? VerificationAttemptResult.success() : VerificationAttemptResult.notClaimed();
                } catch (Exception e) {
                    if (markStaleOnFailure) {
                        subscriptionDAO.updateSubscriptionStatus(connection, subscriptionId, orgId,
                                expectedStatus, SubscriptionStatus.STALE.getValue());
                    }
                    result = VerificationAttemptResult.failure(e);
                }
            }
            return result;
        });
    }

    private static final class VerificationAttemptResult {
        private final boolean claimed;
        private final boolean success;
        private final Exception failure;

        private VerificationAttemptResult(boolean claimed, boolean success, Exception failure) {
            this.claimed = claimed;
            this.success = success;
            this.failure = failure;
        }

        private static VerificationAttemptResult notClaimed() {
            return new VerificationAttemptResult(false, false, null);
        }

        private static VerificationAttemptResult success() {
            return new VerificationAttemptResult(true, true, null);
        }

        private static VerificationAttemptResult failure(Exception failure) {
            return new VerificationAttemptResult(true, false, failure);
        }
    }

    private void validateCallbackUrl(String callbackUrl) {
        if (callbackUrl == null || callbackUrl.trim().isEmpty()) {
            throw new EventNotificationException(
                    EventNotificationServiceConstants.ERROR_CODE_MISSING_REQUIRED_PARAM,
                    EventNotificationServiceConstants.ERROR_TITLE_VALIDATION_FAILED,
                    EventNotificationServiceConstants.CALLBACK_URL_REQUIRED_ERROR_MSG, 422);
        }
        try {
            EventNotificationUrlValidator.validate(callbackUrl,
                    getConfiguration().getEventNotificationAllowedCallbackPorts(),
                    getConfiguration().isEventNotificationPrivateNetworkCallbackTargetsAllowed());
            URI uri = URI.create(callbackUrl.trim());
            String scheme = uri.getScheme();
            if ("http".equalsIgnoreCase(scheme)
                    && !getConfiguration().isEventNotificationHttpCallbackUrlAllowed()) {
                throw new EventNotificationException(
                        EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                        EventNotificationServiceConstants.ERROR_TITLE_VALIDATION_FAILED,
                        EventNotificationServiceConstants.CALLBACK_URL_HTTPS_REQUIRED_ERROR_MSG, 400);
            }
        } catch (EventNotificationException e) {
            throw e;
        } catch (Exception e) {
            throw new EventNotificationException(
                    EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_VALIDATION_FAILED,
                    "Invalid callback URL.", 400);
        }
    }

    private void verifyWebhookCallback(String callbackUrl, String topicName) {
        validateCallbackUrl(callbackUrl);
        String challenge = UUID.randomUUID().toString();
        try {
            URI verificationUri = WebhookVerificationUriBuilder.build(URI.create(callbackUrl.trim()), topicName,
                    challenge);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(verificationUri)
                    .timeout(Duration.ofSeconds(EventNotificationServiceConstants.WEBHOOK_HTTP_TIMEOUT_SECONDS))
                    .GET()
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int maxBodyBytes = getConfiguration().getEventNotificationMaxVerificationResponseBodyBytes();
            InputStream responseBody = response.body() != null ? response.body() : InputStream.nullInputStream();
            byte[] bodyBytes;
            try (InputStream inputStream = responseBody) {
                if (response.statusCode() != 200) {
                    throw new EventNotificationException(
                            EventNotificationServiceConstants.ERROR_CODE_WEBHOOK_VERIFICATION_FAILED,
                            EventNotificationServiceConstants.ERROR_TITLE_WEBHOOK_VERIFICATION_FAILED,
                            "Callback URL responded with HTTP " + response.statusCode(),
                            422);
                }
                bodyBytes = readVerificationResponse(inputStream, maxBodyBytes);
            }
            String body = new String(bodyBytes, StandardCharsets.UTF_8).trim();
            // Spec-compliant verification: the response body must be the challenge value
            // exactly
            // (modulo surrounding whitespace). Using contains() would let a receiver pass
            // the
            // check by appending the challenge to arbitrary content.
            if (!challenge.equals(body)) {
                throw new EventNotificationException(
                        EventNotificationServiceConstants.ERROR_CODE_WEBHOOK_VERIFICATION_FAILED,
                        EventNotificationServiceConstants.ERROR_TITLE_WEBHOOK_VERIFICATION_FAILED,
                        EventNotificationServiceConstants.WEBHOOK_CHALLENGE_MISMATCH_ERROR_MSG, 422);
            }
        } catch (EventNotificationException e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("Webhook verification request failed: " + LogSanitizer.sanitize(e.getMessage()), e);
            }
            throw new EventNotificationException(
                    EventNotificationServiceConstants.ERROR_CODE_WEBHOOK_VERIFICATION_FAILED,
                    EventNotificationServiceConstants.ERROR_TITLE_WEBHOOK_VERIFICATION_FAILED,
                    EventNotificationServiceConstants.WEBHOOK_VERIFICATION_FAILED_ERROR_MSG, 422);
        }
    }

    private byte[] readVerificationResponse(InputStream inputStream, int maxBodyBytes) throws IOException {

        ByteArrayOutputStream body = new ByteArrayOutputStream(Math.min(maxBodyBytes, 8192));
        byte[] buffer = new byte[8192];
        int remaining = maxBodyBytes;
        while (true) {
            int readLength = remaining >= buffer.length ? buffer.length : remaining + 1;
            int count = inputStream.read(buffer, 0, readLength);
            if (count < 0) {
                return body.toByteArray();
            }
            if (count == 0) {
                continue;
            }
            if (count > remaining) {
                throw new EventNotificationException(
                        EventNotificationServiceConstants.ERROR_CODE_WEBHOOK_VERIFICATION_FAILED,
                        EventNotificationServiceConstants.ERROR_TITLE_WEBHOOK_VERIFICATION_FAILED,
                        "Verification response body exceeded " + maxBodyBytes + " bytes", 422);
            }
            body.write(buffer, 0, count);
            remaining -= count;
        }
    }

    @Override
    public PaginatedResult<SubscriptionDTO> listSubscriptions(String orgId, String status, String purposes,
            String search, int limit, int offset, String sort) {
        if (orgId == null || orgId.trim().isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.ORG_ID_MISSING_ERROR_MSG, 400);
        }
        int lim = EventNotificationParameterUtils.normalizeLimit(limit);
        int off = EventNotificationParameterUtils.normalizeOffset(offset);
        String normalizedStatus = EventNotificationParameterUtils.normalizeStatusFilter(status);
        return DatabaseUtils.executeInTransaction(conn -> {
            PaginatedDAOResult<Subscription> daoResult = subscriptionDAO.listSubscriptions(
                    conn, orgId.trim(), normalizedStatus, purposes, search, lim, off, sort);
            List<SubscriptionDTO> dtoList = new ArrayList<>();
            for (Subscription sub : daoResult.getItems()) {
                String topicName = topicDAO.getTopicById(conn, sub.getTopicId(), sub.getOrgId()).map(Topic::getName)
                        .orElse("unknown");
                dtoList.add(mapToDTO(sub, topicName));
            }
            return new PaginatedResult<>(dtoList, daoResult.getTotal());
        });
    }

    @Override
    public SubscriptionDTO getSubscription(String orgId, String subscriptionIdStr) {
        if (orgId == null || orgId.trim().isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.ORG_ID_MISSING_ERROR_MSG, 400);
        }
        if (subscriptionIdStr == null || subscriptionIdStr.trim().isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.SUBSCRIPTION_ID_MISSING_ERROR_MSG, 400);
        }
        return DatabaseUtils.executeInTransaction(conn -> {
            Optional<Subscription> subOpt = subscriptionDAO.getSubscriptionById(conn, subscriptionIdStr.trim(), orgId.trim());
            if (!subOpt.isPresent()) {
                throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_RESOURCE_NOT_FOUND,
                        EventNotificationServiceConstants.ERROR_TITLE_RESOURCE_NOT_FOUND,
                        EventNotificationServiceConstants.SUBSCRIPTION_NOT_FOUND_ERROR_MSG, 404);
            }
            Subscription sub = subOpt.get();
            String topicName = topicDAO.getTopicById(conn, sub.getTopicId(), sub.getOrgId()).map(Topic::getName)
                    .orElse("unknown");
            return mapToDTO(sub, topicName);
        });
    }

    @Override
    public SubscriptionDTO deleteSubscription(String orgId, String subscriptionIdStr) {
        if (orgId == null || orgId.trim().isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.ORG_ID_MISSING_ERROR_MSG, 400);
        }
        if (subscriptionIdStr == null || subscriptionIdStr.trim().isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.SUBSCRIPTION_ID_MISSING_ERROR_MSG, 400);
        }

        return DatabaseUtils.executeInTransaction(conn -> {
            Optional<Subscription> subOpt = subscriptionDAO.getSubscriptionById(conn, subscriptionIdStr.trim(), orgId.trim());
            if (!subOpt.isPresent() || SubscriptionStatus.DELETED.getValue().equalsIgnoreCase(subOpt.get().getStatus())) {
                throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_RESOURCE_NOT_FOUND,
                        EventNotificationServiceConstants.ERROR_TITLE_RESOURCE_NOT_FOUND,
                        EventNotificationServiceConstants.SUBSCRIPTION_NOT_FOUND_ERROR_MSG, 404);
            }

            Subscription sub = subOpt.get();
            boolean deleted = subscriptionDAO.deleteSubscriptionAtomic(conn, sub.getSubscriptionId(), orgId.trim(),
                    sub.getStatus());
            if (!deleted) {
                Optional<Subscription> latest = subscriptionDAO.getSubscriptionById(conn, sub.getSubscriptionId(), orgId.trim());
                if (!latest.isPresent() || SubscriptionStatus.DELETED.getValue().equalsIgnoreCase(latest.get().getStatus())) {
                    throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_RESOURCE_NOT_FOUND,
                            EventNotificationServiceConstants.ERROR_TITLE_RESOURCE_NOT_FOUND,
                            EventNotificationServiceConstants.SUBSCRIPTION_NOT_FOUND_ERROR_MSG, 404);
                }
                if (!sub.getStatus().equalsIgnoreCase(latest.get().getStatus())) {
                    throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_RESOURCE_EXISTS,
                            EventNotificationServiceConstants.ERROR_TITLE_CONCURRENT_MUTATION,
                            EventNotificationServiceConstants.SUBSCRIPTION_CONCURRENT_MODIFICATION_ERROR_MSG, 409);
                }
                if (subscriptionDAO.hasPendingOrInFlightDeliveries(conn, sub.getSubscriptionId(), orgId.trim())) {
                    throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_RESOURCE_EXISTS,
                            EventNotificationServiceConstants.ERROR_TITLE_IN_FLIGHT_DELIVERIES,
                            EventNotificationServiceConstants.SUBSCRIPTION_IN_FLIGHT_DELIVERIES_ERROR_MSG, 409);
                }
                throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INTERNAL_ERROR,
                        EventNotificationServiceConstants.ERROR_TITLE_INTERNAL_ERROR,
                        EventNotificationServiceConstants.FAILED_TO_DELETE_SUBSCRIPTION_ERROR_MSG, 500);
            }

            String topicName = topicDAO.getTopicById(conn, sub.getTopicId(), sub.getOrgId()).map(Topic::getName)
                    .orElse("unknown");
            Subscription deletedSub = new Subscription(
                    sub.getSubscriptionId(),
                    sub.getOrgId(),
                    sub.getGroupId(),
                    sub.getTopicId(),
                    sub.getPurposeFilterMode(),
                    sub.getPurposes(),
                    sub.getDeliveryMode(),
                    sub.getCallbackUrl(),
                    sub.getSharedSecret(),
                    SubscriptionStatus.DELETED.getValue(),
                    sub.getCreatedAt(),
                    new java.sql.Timestamp(System.currentTimeMillis()));
            return mapToDTO(deletedSub, topicName);
        });
    }

    @Override
    public SubscriptionDTO retryVerification(String orgId, String subscriptionId) {
        if (orgId == null || orgId.trim().isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.ORG_ID_MISSING_ERROR_MSG, 400);
        }
        if (subscriptionId == null || subscriptionId.trim().isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.SUBSCRIPTION_ID_MISSING_ERROR_MSG, 400);
        }

        Subscription[] subHolder = new Subscription[1];
        String[] topicNameHolder = new String[1];
        DatabaseUtils.<Void>executeInTransaction(conn -> {
            Optional<Subscription> subOpt = subscriptionDAO.getSubscriptionById(conn, subscriptionId.trim(), orgId.trim());
            if (!subOpt.isPresent() || SubscriptionStatus.DELETED.getValue().equalsIgnoreCase(subOpt.get().getStatus())) {
                throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_RESOURCE_NOT_FOUND,
                        EventNotificationServiceConstants.ERROR_TITLE_RESOURCE_NOT_FOUND,
                        EventNotificationServiceConstants.SUBSCRIPTION_NOT_FOUND_ERROR_MSG, 404);
            }

            subHolder[0] = subOpt.get();
            if (!SubscriptionStatus.STALE.getValue().equalsIgnoreCase(subHolder[0].getStatus())
                    && !SubscriptionStatus.PENDING.getValue().equalsIgnoreCase(subHolder[0].getStatus())) {
                throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_STATE,
                        EventNotificationServiceConstants.ERROR_TITLE_INVALID_STATE,
                        EventNotificationServiceConstants.ONLY_STALE_SUBSCRIPTIONS_VERIFIABLE_ERROR_MSG,
                        409);
            }

            if (subHolder[0].getCallbackUrl() == null || subHolder[0].getCallbackUrl().trim().isEmpty()) {
                throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_STATE,
                        EventNotificationServiceConstants.ERROR_TITLE_INVALID_STATE,
                        EventNotificationServiceConstants.NO_CALLBACK_URL_ERROR_MSG, 409);
            }
            if (subHolder[0].getSharedSecret() == null || subHolder[0].getSharedSecret().trim().isEmpty()) {
                throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_STATE,
                        EventNotificationServiceConstants.ERROR_TITLE_INVALID_STATE,
                        EventNotificationServiceConstants.SHARED_SECRET_REQUIRED_ERROR_MSG, 409);
            }

            topicNameHolder[0] = topicDAO.getTopicById(conn, subHolder[0].getTopicId(), subHolder[0].getOrgId())
                    .map(Topic::getName).orElse("unknown");
            return null;
        });

        String expectedStatus = subHolder[0].getStatus().trim().toLowerCase(java.util.Locale.ROOT);
        VerificationAttemptResult result = executeVerificationAttempt(subscriptionId.trim(), orgId.trim(),
                expectedStatus, subHolder[0].getCallbackUrl().trim(), topicNameHolder[0], false);
        if (result.claimed && !result.success) {
            String description = result.failure instanceof EventNotificationException
                    ? ((EventNotificationException) result.failure).getDescription()
                    : result.failure.getMessage();
            throw new EventNotificationException(
                    EventNotificationServiceConstants.ERROR_CODE_WEBHOOK_VERIFICATION_FAILED,
                    EventNotificationServiceConstants.ERROR_TITLE_WEBHOOK_VERIFICATION_FAILED, description, 422);
        }
        if (!result.claimed) {
            DatabaseUtils.<Void>executeInTransaction(conn2 -> {
                Optional<Subscription> current = subscriptionDAO.getSubscriptionById(conn2, subscriptionId.trim(), orgId.trim());
                if (!current.isPresent()
                        || SubscriptionStatus.DELETED.getValue().equalsIgnoreCase(current.get().getStatus())) {
                    throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_RESOURCE_NOT_FOUND,
                            EventNotificationServiceConstants.ERROR_TITLE_RESOURCE_NOT_FOUND,
                            EventNotificationServiceConstants.SUBSCRIPTION_NOT_FOUND_ERROR_MSG, 404);
                }
                return null;
            });
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_STATE,
                    EventNotificationServiceConstants.ERROR_TITLE_CONCURRENT_MUTATION,
                    EventNotificationServiceConstants.SUBSCRIPTION_CONCURRENT_MODIFICATION_ERROR_MSG, 409);
        }
        subHolder[0].setStatus(SubscriptionStatus.ACTIVE.getValue());

        return mapToDTO(subHolder[0], topicNameHolder[0]);
    }

    @Override
    public PaginatedResult<SubscriptionDeliveryDTO> listSubscriptionEvents(String orgId, String subscriptionId,
            int limit, int offset) {
        if (orgId == null || orgId.trim().isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.ORG_ID_MISSING_ERROR_MSG, 400);
        }
        if (subscriptionId == null || subscriptionId.trim().isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.SUBSCRIPTION_ID_MISSING_ERROR_MSG, 400);
        }

        return DatabaseUtils.<PaginatedResult<SubscriptionDeliveryDTO>>executeInTransaction(conn -> {
            Optional<Subscription> subOpt = subscriptionDAO.getSubscriptionById(conn, subscriptionId.trim(), orgId.trim());
            if (!subOpt.isPresent()) {
                throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_RESOURCE_NOT_FOUND,
                        EventNotificationServiceConstants.ERROR_TITLE_RESOURCE_NOT_FOUND,
                        EventNotificationServiceConstants.SUBSCRIPTION_NOT_FOUND_ERROR_MSG, 404);
            }

            int lim = EventNotificationParameterUtils.normalizeLimit(limit);
            int off = EventNotificationParameterUtils.normalizeOffset(offset);
            int[] totalOut = new int[1];

            List<SubscriptionDeliverySummary> summaries = deliveryDAO.listSubscriptionDeliveries(conn, orgId.trim(),
                    subscriptionId.trim(), lim, off, totalOut);
            List<SubscriptionDeliveryDTO> dtoList = new ArrayList<>();
            for (SubscriptionDeliverySummary summary : summaries) {
                dtoList.add(new SubscriptionDeliveryDTO(
                        summary.getDeliveryId(),
                        summary.getEventId(),
                        summary.getTopicName(),
                        summary.getCurrentStatus() != null ? summary.getCurrentStatus()
                                : SubscriptionStatus.PENDING.getValue(),
                        summary.getDeliveryMode() != null ? summary.getDeliveryMode()
                                : DeliveryMode.WEBHOOK.getValue(),
                        summary.getOccurredAt() != null ? summary.getOccurredAt().getTime()
                                : (summary.getCreatedAt() != null ? summary.getCreatedAt().getTime()
                                        : System.currentTimeMillis())));
            }
            return new PaginatedResult<>(dtoList, totalOut[0]);
        });
    }

    @Override
    public SubscriptionEventHistoryDTO getSubscriptionEventHistory(String orgId, String subscriptionId,
            String deliveryId) {
        if (orgId == null || orgId.trim().isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.ORG_ID_MISSING_ERROR_MSG, 400);
        }
        if (subscriptionId == null || subscriptionId.trim().isEmpty() || deliveryId == null
                || deliveryId.trim().isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.SUBSCRIPTION_ID_MISSING_ERROR_MSG, 400);
        }

        return DatabaseUtils.<SubscriptionEventHistoryDTO>executeInTransaction(conn -> {
            Optional<Subscription> subOpt = subscriptionDAO.getSubscriptionById(conn, subscriptionId.trim(), orgId.trim());
            if (!subOpt.isPresent()) {
                throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_RESOURCE_NOT_FOUND,
                        EventNotificationServiceConstants.ERROR_TITLE_RESOURCE_NOT_FOUND,
                        EventNotificationServiceConstants.SUBSCRIPTION_NOT_FOUND_ERROR_MSG, 404);
            }

            Optional<SubscriptionDeliverySummary> summaryOpt = deliveryDAO
                    .getSubscriptionDeliveryById(conn, orgId.trim(), subscriptionId.trim(), deliveryId.trim());
            if (!summaryOpt.isPresent()) {
                throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_DELIVERY_NOT_FOUND,
                        EventNotificationServiceConstants.ERROR_TITLE_DELIVERY_NOT_FOUND,
                        EventNotificationServiceConstants.DELIVERY_NOT_FOUND_ERROR_MSG, 404);
            }

            return DeliveryHistoryMapper.map(conn, orgId.trim(), deliveryId.trim(),
                    summaryOpt.get(), deliveryDAO, deliveryAckDAO,
                    getConfiguration().getEventNotificationMaxRetries());
        });
    }

    @Override
    public SubscriptionEventHistoryDTO retryDelivery(String orgId, String subscriptionId, String deliveryId) {
        if (orgId == null || orgId.trim().isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.ORG_ID_MISSING_ERROR_MSG, 400);
        }
        if (subscriptionId == null || subscriptionId.trim().isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.SUBSCRIPTION_ID_MISSING_ERROR_MSG, 400);
        }
        if (deliveryId == null || deliveryId.trim().isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.DELIVERY_ID_MISSING_ERROR_MSG, 400);
        }
        if (manualRetryDispatcher == null) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INTERNAL_ERROR,
                    EventNotificationServiceConstants.ERROR_TITLE_INTERNAL_ERROR,
                    EventNotificationServiceConstants.DELIVERY_MANUAL_RETRY_UNAVAILABLE_ERROR_MSG, 500);
        }

        WebhookDeliveryWorker.ManualRetrySubmissionResult result = manualRetryDispatcher.submit(
                orgId.trim(), subscriptionId.trim(), deliveryId.trim());
        if (result == WebhookDeliveryWorker.ManualRetrySubmissionResult.NOT_FOUND) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_DELIVERY_NOT_FOUND,
                    EventNotificationServiceConstants.ERROR_TITLE_DELIVERY_NOT_FOUND,
                    EventNotificationServiceConstants.DELIVERY_NOT_FOUND_ERROR_MSG, 404);
        }
        if (result == WebhookDeliveryWorker.ManualRetrySubmissionResult.NOT_ELIGIBLE) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_STATE,
                    EventNotificationServiceConstants.ERROR_TITLE_INVALID_STATE,
                    EventNotificationServiceConstants.DELIVERY_MANUAL_RETRY_NOT_ELIGIBLE_ERROR_MSG, 409);
        }
        return getSubscriptionEventHistory(orgId, subscriptionId, deliveryId);
    }

    private SubscriptionDTO mapToDTO(Subscription sub, String topicName) {
        if (sub == null) {
            return null;
        }

        SubscriptionStatus status = SubscriptionStatus.fromValueOrDefault(sub.getStatus(), SubscriptionStatus.ACTIVE);
        Long createdAt = sub.getCreatedAt() != null ? sub.getCreatedAt().getTime() : null;
        Long updatedAt = sub.getUpdatedAt() != null ? sub.getUpdatedAt().getTime() : null;

        PurposeFilterMode filterType = PurposeFilterMode.fromValueOrDefault(sub.getPurposeFilterMode(),
                PurposeFilterMode.ALL);
        FilterDTO filter = new FilterDTO(filterType, sub.getPurposes());

        DeliveryMode deliveryMode = DeliveryMode.fromValueOrDefault(sub.getDeliveryMode(), DeliveryMode.WEBHOOK);
        DeliveryConfigDTO delivery = new DeliveryConfigDTO(deliveryMode, sub.getCallbackUrl(), null);

        return new SubscriptionDTO(
                sub.getSubscriptionId(),
                sub.getOrgId(),
                sub.getGroupId(),
                topicName,
                filter,
                delivery,
                status,
                createdAt,
                updatedAt,
                false,
                null);
    }

    private DPDPConfigurationService getConfiguration() {
        if (configurationService == null) {
            return new org.wso2.dpdp.accelerator.common.config.DPDPConfigurationServiceImpl(false);
        }
        return configurationService;
    }
}
