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

package org.wso2.dpdp.accelerator.event.notifications.endpoint.util;

import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.Delivery;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.DeliveryAttempt;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.DeliveryConfig;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.DeliveryConfigRequest;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.DeliveryHistory;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.DeliveryMode;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.DeliveryPage;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.Event;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.EventCreateRequest;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.EventPage;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.Filter;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.PollResponse;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.PurposeFilterMode;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.Subscription;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.SubscriptionCreateRequest;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.SubscriptionPage;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.SubscriptionStatus;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.Topic;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.TopicCreateRequest;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.TopicPage;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.TopicStatus;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.DeliveryConfigDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.EventCreateDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.EventDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.EventPollingResponseDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.FilterDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDeliveryAttemptDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDeliveryDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionEventHistoryDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.TopicDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.model.PaginatedResult;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Explicit conversion between the REST contract and service contracts. */
public final class EventNotificationDtoMapper {
    private EventNotificationDtoMapper() {
    }

    public static TopicDTO toService(TopicCreateRequest source) {
        if (source == null) {
            return null;
        }
        TopicDTO target = new TopicDTO();
        target.setTopicId(source.getTopicId());
        target.setStatus(source.getStatus());
        target.setInitiatedBy(source.getInitiatedBy());
        target.setName(source.getName());
        target.setDescription(source.getDescription());
        return target;
    }

    public static EventCreateDTO toService(EventCreateRequest source) {
        return source == null ? null : new EventCreateDTO(source.getTopic(),
                copy(source.getPurposes()), source.getPayload());
    }

    public static SubscriptionDTO toService(SubscriptionCreateRequest source) {
        if (source == null) {
            return null;
        }
        SubscriptionDTO target = new SubscriptionDTO();
        target.setSubscriptionId(source.getSubscriptionId());
        target.setOrgId(source.getOrgId());
        target.setGroupId(source.getGroupId());
        target.setStatus(source.getStatus() == null ? null :
                org.wso2.dpdp.accelerator.event.notifications.common.enums.SubscriptionStatus
                        .fromValue(source.getStatus().toString()));
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        target.setAlreadyExists(source.getAlreadyExists());
        target.setMessage(source.getMessage());
        target.setTopic(source.getTopic());
        if (source.getFilter() != null) {
            Filter filter = source.getFilter();
            target.setFilter(new FilterDTO(filter.getType() == null ? null :
                    org.wso2.dpdp.accelerator.event.notifications.common.enums.PurposeFilterMode
                            .fromValue(filter.getType().toString()), copy(filter.getPurposes())));
        }
        if (source.getDelivery() != null) {
            DeliveryConfigRequest delivery = source.getDelivery();
            target.setDelivery(new DeliveryConfigDTO(delivery.getMode() == null ? null :
                    org.wso2.dpdp.accelerator.event.notifications.common.enums.DeliveryMode
                            .fromValue(delivery.getMode().toString()),
                    delivery.getCallbackUrl(), delivery.getSharedSecret()));
        }
        return target;
    }

    public static Topic toApi(TopicDTO source) {
        if (source == null) {
            return null;
        }
        Topic target = new Topic();
        target.setTopicId(source.getTopicId());
        target.setName(source.getName());
        target.setDescription(source.getDescription());
        target.setStatus(source.getStatus() == null ? null : TopicStatus.fromValue(source.getStatus()));
        target.setInitiatedBy(source.getInitiatedBy() == null ? null :
                Topic.InitiatedByEnum.fromValue(source.getInitiatedBy()));
        return target;
    }

    public static Subscription toApi(SubscriptionDTO source) {
        if (source == null) {
            return null;
        }
        Subscription target = new Subscription();
        target.setSubscriptionId(source.getSubscriptionId());
        target.setOrgId(source.getOrgId());
        target.setGroupId(source.getGroupId());
        target.setTopic(source.getTopic());
        target.setStatus(source.getStatus() == null ? null :
                SubscriptionStatus.fromValue(source.getStatus().getValue()));
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        target.setAlreadyExists(source.getAlreadyExists());
        target.setMessage(source.getMessage());
        if (source.getFilter() != null) {
            Filter filter = new Filter();
            filter.setType(source.getFilter().getType() == null ? null :
                    PurposeFilterMode.fromValue(source.getFilter().getType().getValue()));
            filter.setPurposes(copy(source.getFilter().getPurposes()));
            target.setFilter(filter);
        }
        if (source.getDelivery() != null) {
            DeliveryConfig delivery = new DeliveryConfig();
            delivery.setMode(source.getDelivery().getMode() == null ? null :
                    DeliveryMode.fromValue(source.getDelivery().getMode().getValue()));
            delivery.setCallbackUrl(source.getDelivery().getCallbackUrl());
            // The public response never exposes the subscription shared secret.
            target.setDelivery(delivery);
        }
        return target;
    }

    public static Event toApi(EventDTO source) {
        if (source == null) {
            return null;
        }
        Event target = new Event();
        target.setEventId(source.getEventId());
        target.setOrgId(source.getOrgId());
        target.setGroupId(source.getGroupId());
        target.setTopicId(source.getTopicId());
        target.setTopic(source.getTopic());
        target.setPayload(source.getPayload());
        target.setDeliveriesCount(source.getDeliveriesCount());
        target.setPurposes(copy(source.getPurposes()));
        target.setOccurredAt(epoch(source.getOccurredAt()));
        target.setCreatedAt(epoch(source.getCreatedAt()));
        return target;
    }

    public static Delivery toApi(SubscriptionDeliveryDTO source) {
        if (source == null) {
            return null;
        }
        Delivery target = new Delivery();
        target.setDeliveryId(source.getDeliveryId());
        target.setEventId(source.getEventId());
        target.setSubscriptionId(source.getSubscriptionId());
        target.setGroupId(source.getGroupId());
        target.setTopic(source.getTopic());
        target.setCurrentStatus(source.getCurrentStatus());
        target.setOccurredAt(source.getOccurredAt());
        target.setDeliveryMode(source.getDeliveryMode() == null ? null : DeliveryMode.fromValue(source.getDeliveryMode()));
        return target;
    }

    public static DeliveryAttempt toApi(SubscriptionDeliveryAttemptDTO source) {
        if (source == null) {
            return null;
        }
        DeliveryAttempt target = new DeliveryAttempt();
        target.setAttempt(source.getAttempt());
        target.setStatus(source.getStatus());
        target.setTimestamp(source.getTimestamp());
        target.setHttpStatus(source.getHttpStatus());
        target.setError(source.getError());
        return target;
    }

    public static DeliveryHistory toApi(SubscriptionEventHistoryDTO source) {
        if (source == null) {
            return null;
        }
        DeliveryHistory target = new DeliveryHistory();
        target.setDeliveryId(source.getDeliveryId());
        target.setEventId(source.getEventId());
        target.setTopic(source.getTopic());
        target.setCurrentStatus(source.getCurrentStatus());
        target.setOccurredAt(source.getOccurredAt());
        target.setNextRetryAt(source.getNextRetryAt());
        target.setCompletionStatus(source.getCompletionStatus());
        target.setCompletionEvidence(source.getCompletionEvidence());
        target.setDeliveryMode(source.getDeliveryMode() == null ? null : DeliveryMode.fromValue(source.getDeliveryMode()));
        target.setHistory(map(source.getHistory(), EventNotificationDtoMapper::toApi));
        return target;
    }

    public static PollResponse toApi(EventPollingResponseDTO source) {
        if (source == null) {
            return null;
        }
        PollResponse target = new PollResponse();
        target.setMoreAvailable(source.isMoreAvailable());
        target.setSets(source.getSets());
        return target;
    }

    public static TopicPage topics(PaginatedResult<TopicDTO> source) {
        if (source == null) {
            return null;
        }
        TopicPage target = new TopicPage();
        target.setItems(map(source.getItems(), EventNotificationDtoMapper::toApi));
        target.setTotal(source.getTotal());
        return target;
    }

    public static SubscriptionPage subscriptions(PaginatedResult<SubscriptionDTO> source) {
        if (source == null) {
            return null;
        }
        SubscriptionPage target = new SubscriptionPage();
        target.setItems(map(source.getItems(), EventNotificationDtoMapper::toApi));
        target.setTotal(source.getTotal());
        return target;
    }

    public static EventPage events(PaginatedResult<EventDTO> source) {
        if (source == null) {
            return null;
        }
        EventPage target = new EventPage();
        target.setItems(map(source.getItems(), EventNotificationDtoMapper::toApi));
        target.setTotal(source.getTotal());
        return target;
    }

    public static DeliveryPage deliveries(PaginatedResult<SubscriptionDeliveryDTO> source) {
        if (source == null) {
            return null;
        }
        DeliveryPage target = new DeliveryPage();
        target.setItems(map(source.getItems(), EventNotificationDtoMapper::toApi));
        target.setTotal(source.getTotal());
        return target;
    }

    private static Long epoch(Timestamp value) {
        return value == null ? null : value.getTime();
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? null : new ArrayList<>(values);
    }

    private static <S, T> List<T> map(List<S> source, Function<S, T> mapper) {
        if (source == null) {
            return null;
        }
        List<T> target = new ArrayList<>(source.size());
        for (S item : source) {
            target.add(mapper.apply(item));
        }
        return target;
    }
}
