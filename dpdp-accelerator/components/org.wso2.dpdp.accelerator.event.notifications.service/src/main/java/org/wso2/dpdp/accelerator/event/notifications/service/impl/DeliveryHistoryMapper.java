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

import org.wso2.dpdp.accelerator.event.notifications.common.enums.DeliveryMode;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.DeliveryStatus;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.PollStatus;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryAckDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.PollDelivery;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.SubscriptionDeliverySummary;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDelivery;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDeliveryAck;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDeliveryAudit;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDeliveryAttemptDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionEventHistoryDTO;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Maps persisted delivery state to the history response shared by event and subscription APIs.
 */
final class DeliveryHistoryMapper {

    private DeliveryHistoryMapper() {
    }

    /**
     * Maps persisted delivery state to the history response shared by event and subscription APIs.
     * <p>
     * Note: The caller owns the lifecycle of {@code conn}; this method does not commit or close it.
     */
    static SubscriptionEventHistoryDTO map(Connection conn, String orgId, String deliveryId,
            SubscriptionDeliverySummary summary, DeliveryDAO deliveryDAO, DeliveryAckDAO deliveryAckDAO,
            int maxRetries) {
        String mode = summary.getDeliveryMode() != null ? summary.getDeliveryMode()
                : DeliveryMode.WEBHOOK.getValue();

        SubscriptionEventHistoryDTO dto = new SubscriptionEventHistoryDTO();
        dto.setDeliveryId(summary.getDeliveryId());
        dto.setEventId(summary.getEventId());
        dto.setTopic(summary.getTopicName());
        dto.setDeliveryMode(mode);
        dto.setCurrentStatus(summary.getCurrentStatus() != null ? summary.getCurrentStatus()
                : defaultStatus(mode));
        dto.setOccurredAt(summary.getOccurredAt() != null ? summary.getOccurredAt().getTime()
                : (summary.getCreatedAt() != null ? summary.getCreatedAt().getTime() : System.currentTimeMillis()));

        if (DeliveryMode.WEBHOOK.getValue().equals(mode)) {
            mapWebhookHistory(conn, orgId, deliveryId, summary, deliveryDAO, deliveryAckDAO, dto, maxRetries);
        } else {
            mapPollHistory(conn, orgId, deliveryId, summary, deliveryDAO, dto);
        }
        return dto;
    }

    private static void mapWebhookHistory(Connection conn, String orgId, String deliveryId, SubscriptionDeliverySummary summary,
            DeliveryDAO deliveryDAO, DeliveryAckDAO deliveryAckDAO, SubscriptionEventHistoryDTO dto,
            int maxRetries) {
        Optional<WebhookDelivery> webhookDelivery = deliveryDAO.getWebhookDeliveryById(conn, deliveryId, orgId);
        if (webhookDelivery.isPresent()) {
            WebhookDelivery delivery = webhookDelivery.get();
            if (delivery.getNextRetryAt() != null) {
                dto.setNextRetryAt(delivery.getNextRetryAt().getTime());
            }
            dto.setManualRetryUsed(delivery.isManualRetryUsed());
            dto.setManualRetryAvailable(DeliveryStatus.FAILED.getValue().equalsIgnoreCase(delivery.getStatus())
                    && delivery.getAttemptCount() > maxRetries && !delivery.isManualRetryUsed());
        }

        Optional<WebhookDeliveryAck> deliveryAck = deliveryAckDAO.getDeliveryAckByDeliveryId(conn, deliveryId);
        if (deliveryAck.isPresent()) {
            WebhookDeliveryAck ack = deliveryAck.get();
            dto.setCompletionStatus(ack.getCompletionStatus() != null ? ack.getCompletionStatus()
                    : DeliveryStatus.COMPLETED.getValue());
            dto.setCompletionEvidence(ack.getCompletionEvidence());
        }

        List<SubscriptionDeliveryAttemptDTO> attempts = new ArrayList<>();
        int attemptNumber = 1;
        for (WebhookDeliveryAudit audit : deliveryDAO.getWebhookDeliveryAudits(conn, deliveryId, orgId)) {
            attempts.add(mapAttempt(attemptNumber++, audit));
        }
        if (attempts.isEmpty()) {
            attempts.add(new SubscriptionDeliveryAttemptDTO(1,
                    summary.getCurrentStatus() != null ? summary.getCurrentStatus()
                            : DeliveryStatus.PENDING.getValue(),
                    summary.getCreatedAt() != null ? summary.getCreatedAt().getTime() : System.currentTimeMillis(),
                    null, null));
        }
        dto.setHistory(attempts);
    }

    private static SubscriptionDeliveryAttemptDTO mapAttempt(int attemptNumber, WebhookDeliveryAudit audit) {
        Integer httpStatus = null;
        String error = null;
        String status = DeliveryStatus.FAILED.getValue();
        if (audit.getResponseCode() != null) {
            try {
                httpStatus = Integer.parseInt(audit.getResponseCode().trim());
                if (httpStatus >= 200 && httpStatus < 300) {
                    status = DeliveryStatus.DELIVERED.getValue();
                } else {
                    error = "HTTP " + httpStatus;
                }
            } catch (NumberFormatException e) {
                error = audit.getResponseCode();
            }
        }
        long timestamp = audit.getAttemptAt() != null ? audit.getAttemptAt().getTime()
                : (audit.getCreatedAt() != null ? audit.getCreatedAt().getTime() : System.currentTimeMillis());
        return new SubscriptionDeliveryAttemptDTO(attemptNumber, status, timestamp, httpStatus, error);
    }

    private static void mapPollHistory(Connection conn, String orgId, String deliveryId, SubscriptionDeliverySummary summary,
            DeliveryDAO deliveryDAO, SubscriptionEventHistoryDTO dto) {
        Optional<PollDelivery> pollDelivery = deliveryDAO.getPollDeliveryById(conn, deliveryId, orgId);
        String pollStatus = summary.getCurrentStatus() != null ? summary.getCurrentStatus()
                : PollStatus.PENDING.getValue();
        long timestamp = summary.getOccurredAt() != null ? summary.getOccurredAt().getTime()
                : System.currentTimeMillis();
        if (pollDelivery.isPresent() && pollDelivery.get().getCompletedAt() != null) {
            timestamp = pollDelivery.get().getCompletedAt().getTime();
        }

        dto.setCompletionStatus(pollStatus);
        List<SubscriptionDeliveryAttemptDTO> attempts = new ArrayList<>();
        attempts.add(new SubscriptionDeliveryAttemptDTO(1, pollStatus, timestamp, null, null));
        dto.setHistory(attempts);
    }

    static String defaultStatus(String deliveryMode) {
        return DeliveryMode.POLL.getValue().equals(deliveryMode)
                ? PollStatus.PENDING.getValue()
                : DeliveryStatus.PENDING.getValue();
    }
}
