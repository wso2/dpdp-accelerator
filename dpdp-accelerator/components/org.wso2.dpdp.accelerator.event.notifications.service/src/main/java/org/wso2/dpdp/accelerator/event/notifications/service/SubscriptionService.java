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

package org.wso2.dpdp.accelerator.event.notifications.service;

import org.wso2.dpdp.accelerator.event.notifications.service.model.PaginatedResult;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.FilterDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.DeliveryConfigDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDeliveryDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionEventHistoryDTO;

public interface SubscriptionService {

    default SubscriptionDTO createSubscription(String orgId, String topicName, FilterDTO filter,
            DeliveryConfigDTO delivery) {
        return createSubscription(orgId, orgId, topicName, filter, delivery);
    }

    SubscriptionDTO createSubscription(String orgId, String groupId, String topicName, FilterDTO filter,
            DeliveryConfigDTO delivery);

    PaginatedResult<SubscriptionDTO> listSubscriptions(String orgId, String status, String purposes, String search,
            int limit, int offset, String sort);

    SubscriptionDTO getSubscription(String orgId, String subscriptionId);

    SubscriptionDTO deleteSubscription(String orgId, String subscriptionId);

    SubscriptionDTO retryVerification(String orgId, String subscriptionId);

    PaginatedResult<SubscriptionDeliveryDTO> listSubscriptionEvents(String orgId, String subscriptionId, int limit,
            int offset);

    SubscriptionEventHistoryDTO getSubscriptionEventHistory(String orgId, String subscriptionId, String deliveryId);

    SubscriptionEventHistoryDTO retryDelivery(String orgId, String subscriptionId, String deliveryId);

}
