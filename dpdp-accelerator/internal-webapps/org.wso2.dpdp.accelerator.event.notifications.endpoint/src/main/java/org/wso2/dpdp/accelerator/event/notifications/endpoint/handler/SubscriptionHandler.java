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

package org.wso2.dpdp.accelerator.event.notifications.endpoint.handler;

import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.dpdp.accelerator.event.notifications.service.SubscriptionService;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.DeliveryConfigDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.FilterDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDeliveryDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionEventHistoryDTO;

import org.wso2.dpdp.accelerator.event.notifications.service.model.PaginatedResult;

public class SubscriptionHandler {

    private final SubscriptionService subscriptionService;

    public SubscriptionHandler() {
        SubscriptionService svc = (SubscriptionService) PrivilegedCarbonContext
                .getThreadLocalCarbonContext()
                .getOSGiService(SubscriptionService.class, null);
        if (svc == null) {
            throw new IllegalStateException("SubscriptionService OSGi service not available");
        }
        this.subscriptionService = svc;
    }

    public SubscriptionHandler(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    public SubscriptionDTO createSubscription(String orgId, SubscriptionDTO request) {
        String groupId = orgId != null ? orgId.trim() : null;
        String topic = request != null ? request.getTopic() : null;
        FilterDTO filterDTO = request != null ? request.getFilter() : null;
        DeliveryConfigDTO deliveryDTO = request != null ? request.getDelivery() : null;
        return subscriptionService.createSubscription(orgId, groupId, topic, filterDTO, deliveryDTO);
    }

    public PaginatedResult<SubscriptionDTO> listSubscriptions(String orgId, String status, String purposes,
            String search, Integer limit, Integer offset, String sort) {
        int lim = limit == null ? 0 : limit;
        int off = offset == null ? -1 : offset;
        return subscriptionService.listSubscriptions(orgId, status, purposes, search, lim, off, sort);
    }

    public SubscriptionDTO getSubscription(String orgId, String subscriptionId) {
        return subscriptionService.getSubscription(orgId, subscriptionId);
    }

    public SubscriptionDTO deleteSubscription(String orgId, String subscriptionId) {
        return subscriptionService.deleteSubscription(orgId, subscriptionId);
    }

    public SubscriptionDTO retryVerification(String orgId, String subscriptionId) {
        return subscriptionService.retryVerification(orgId, subscriptionId);
    }

    public PaginatedResult<SubscriptionDeliveryDTO> listSubscriptionEvents(String orgId, String subscriptionId,
            Integer limit, Integer offset) {
        int lim = limit == null ? 0 : limit;
        int off = offset == null ? -1 : offset;
        return subscriptionService.listSubscriptionEvents(orgId, subscriptionId, lim, off);
    }

    public SubscriptionEventHistoryDTO getSubscriptionEventHistory(String orgId, String subscriptionId,
            String deliveryId) {
        return subscriptionService.getSubscriptionEventHistory(orgId, subscriptionId, deliveryId);
    }

    public SubscriptionEventHistoryDTO retryDelivery(String orgId, String subscriptionId, String deliveryId) {
        return subscriptionService.retryDelivery(orgId, subscriptionId, deliveryId);
    }
}
