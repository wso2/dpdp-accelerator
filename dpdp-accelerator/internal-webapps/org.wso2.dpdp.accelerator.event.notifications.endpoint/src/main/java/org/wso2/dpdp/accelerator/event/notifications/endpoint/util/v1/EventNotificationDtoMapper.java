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

package org.wso2.dpdp.accelerator.event.notifications.endpoint.util.v1;

import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1.dto.Delivery;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1.dto.DeliveryAttempt;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1.dto.DeliveryConfig;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1.dto.DeliveryConfigRequest;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1.dto.DeliveryHistory;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1.dto.DeliveryMode;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1.dto.DeliveryPage;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1.dto.Event;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1.dto.EventCreateRequest;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1.dto.EventPage;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1.dto.Filter;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1.dto.PollResponse;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1.dto.PurposeFilterMode;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1.dto.Subscription;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1.dto.SubscriptionCreateRequest;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1.dto.SubscriptionPage;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1.dto.SubscriptionStatus;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1.dto.Topic;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1.dto.TopicCreateRequest;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1.dto.TopicPage;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1.dto.TopicStatus;
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

/** Explicit conversion between the V1 REST contract and service contracts. */
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
        if (source.getTopic() != null && source.getTopics() != null) {
            throw new IllegalArgumentException("Supply topic or topics, not both.");
        }
        SubscriptionDTO target = new SubscriptionDTO();
        target.setName(source.getName());
        target.setSubscriptionId(source.getSubscriptionId());
        target.setOrgId(source.getOrgId());
        target.setGroupId(source.getGroupId());
        target.setStatus(source.getStatus() == null ? null :
                org.wso2.dpdp.accelerator.event.notifications.common.enums.SubscriptionStatus
                        .fromValueOrDefault(source.getStatus().toString(), null));
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        target.setAlreadyExists(source.getAlreadyExists());
        target.setMessage(source.getMessage());
        target.setTopic(source.getTopic());
        target.setTopics(source.getTopics());
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
        return toApi(source, true);
    }

    public static Subscription toSummaryApi(SubscriptionDTO source) {
        return toApi(source, false);
    }

    private static Subscription toApi(SubscriptionDTO source, boolean includeSecret) {
        if (source == null) {
            return null;
        }
        Subscription target = new Subscription();
        target.setName(source.getName());
        target.setSubscriptionId(source.getSubscriptionId());
        target.setOrgId(source.getOrgId());
        target.setGroupId(source.getGroupId());
        target.setTopic(source.getTopic());
        target.setTopics(source.getTopics());
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
            if (includeSecret) {
                delivery.setSharedSecret(source.getDelivery().getSharedSecret());
            }
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
        target.setDeliveryMode(source.getDeliveryMode() == null ? null :
                DeliveryMode.fromValue(source.getDeliveryMode()));
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
        target.setDeliveryMode(source.getDeliveryMode() == null ? null :
                DeliveryMode.fromValue(source.getDeliveryMode()));
        target.setOccurredAt(source.getOccurredAt());
        target.setNextRetryAt(source.getNextRetryAt());
        target.setCompletionStatus(source.getCompletionStatus());
        target.setCompletionEvidence(source.getCompletionEvidence());
        target.setManualRetryAvailable(source.isManualRetryAvailable());
        if (source.getHistory() != null) {
            List<DeliveryAttempt> attempts = new ArrayList<>();
            for (SubscriptionDeliveryAttemptDTO attemptDTO : source.getHistory()) {
                attempts.add(toApi(attemptDTO));
            }
            target.setHistory(attempts);
        }
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
        return page(source, EventNotificationDtoMapper::toApi, (items, total) -> {
            TopicPage page = new TopicPage();
            page.setItems(items);
            page.setTotal(total);
            return page;
        });
    }

    public static SubscriptionPage subscriptions(PaginatedResult<SubscriptionDTO> source) {
        return page(source, EventNotificationDtoMapper::toSummaryApi, (items, total) -> {
            SubscriptionPage page = new SubscriptionPage();
            page.setItems(items);
            page.setTotal(total);
            return page;
        });
    }

    public static EventPage events(PaginatedResult<EventDTO> source) {
        return page(source, EventNotificationDtoMapper::toApi, (items, total) -> {
            EventPage page = new EventPage();
            page.setItems(items);
            page.setTotal(total);
            return page;
        });
    }

    public static DeliveryPage deliveries(PaginatedResult<SubscriptionDeliveryDTO> source) {
        return page(source, EventNotificationDtoMapper::toApi, (items, total) -> {
            DeliveryPage page = new DeliveryPage();
            page.setItems(items);
            page.setTotal(total);
            return page;
        });
    }

    private static <S, T, P> P page(PaginatedResult<S> source, Function<S, T> mapper,
            java.util.function.BiFunction<List<T>, Integer, P> factory) {
        if (source == null) {
            return factory.apply(new ArrayList<>(), 0);
        }
        List<T> mapped = new ArrayList<>();
        if (source.getItems() != null) {
            for (S item : source.getItems()) {
                mapped.add(mapper.apply(item));
            }
        }
        return factory.apply(mapped, source.getTotal());
    }

    private static <T> List<T> copy(List<T> source) {
        return source == null ? null : new ArrayList<>(source);
    }

    private static Long epoch(Timestamp ts) {
        return ts == null ? null : ts.getTime();
    }
}
