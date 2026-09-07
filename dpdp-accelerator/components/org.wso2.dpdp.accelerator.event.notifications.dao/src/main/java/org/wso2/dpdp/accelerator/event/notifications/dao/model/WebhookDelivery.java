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

package org.wso2.dpdp.accelerator.event.notifications.dao.model;

import java.sql.Timestamp;

public class WebhookDelivery {

    private String deliveryId;
    private String subscriptionId;
    private String eventId;
    private String status;
    private int attemptCount;
    private Timestamp nextRetryAt;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Timestamp deliveredAt;
    private boolean manualRetryUsed;

    public WebhookDelivery() {
    }

    public WebhookDelivery(String deliveryId, String subscriptionId, String eventId, String status, int attemptCount, Timestamp nextRetryAt, Timestamp createdAt, Timestamp updatedAt, Timestamp deliveredAt) {

        this(deliveryId, subscriptionId, eventId, status, attemptCount, nextRetryAt, createdAt, updatedAt,
                deliveredAt, false);
    }

    public WebhookDelivery(String deliveryId, String subscriptionId, String eventId, String status, int attemptCount,
            Timestamp nextRetryAt, Timestamp createdAt, Timestamp updatedAt, Timestamp deliveredAt,
            boolean manualRetryUsed) {
        this.deliveryId = deliveryId;
        this.subscriptionId = subscriptionId;
        this.eventId = eventId;
        this.status = status;
        this.attemptCount = attemptCount;
        this.nextRetryAt = nextRetryAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deliveredAt = deliveredAt;
        this.manualRetryUsed = manualRetryUsed;
    }

    public String getDeliveryId() {
        return deliveryId;
    }

    public void setDeliveryId(String deliveryId) {
        this.deliveryId = deliveryId;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public Timestamp getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(Timestamp nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Timestamp getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(Timestamp deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public boolean isManualRetryUsed() {
        return manualRetryUsed;
    }

    public void setManualRetryUsed(boolean manualRetryUsed) {
        this.manualRetryUsed = manualRetryUsed;
    }
}
