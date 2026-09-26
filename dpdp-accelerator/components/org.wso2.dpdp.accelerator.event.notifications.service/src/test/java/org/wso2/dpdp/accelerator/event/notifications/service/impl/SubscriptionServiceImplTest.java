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

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryAckDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.PaginatedDAOResult;
import org.wso2.dpdp.accelerator.event.notifications.dao.SubscriptionDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.TopicDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Subscription;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Topic;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.DeliveryConfigDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.FilterDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.constants.EventNotificationServiceConstants;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.DeliveryMode;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.PurposeFilterMode;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.SubscriptionStatus;
import org.wso2.dpdp.accelerator.event.notifications.common.exception.service.EventNotificationServiceException;
import org.wso2.dpdp.accelerator.event.notifications.common.exception.dao.EventNotificationInvalidStateException;
import org.wso2.dpdp.accelerator.event.notifications.service.model.PaginatedResult;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.common.constant.DPDPCommonConstants;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.expectThrows;

public class SubscriptionServiceImplTest {

        @Test
        public void createsOneSubscriptionForTwoCanonicalTopics() {
                Map<String, Topic> resolved = new HashMap<>();
                resolved.put("accounts", new Topic("a", "org1", "accounts", "", "active"));
                resolved.put("billing", new Topic("b", "org1", "billing", "", "active"));
                when(topicDAO.getTopicsByOrgAndNames(any(Connection.class), eq("org1"), any())).thenReturn(resolved);

                SubscriptionDTO result = subscriptionService.createMultiTopicSubscription("org1", "group1",
                                "sub-name",
                                java.util.Arrays.asList("billing", "accounts"),
                                new FilterDTO(PurposeFilterMode.ALL, Collections.emptyList()),
                                new DeliveryConfigDTO(DeliveryMode.POLL, null, "secret"));
                assertEquals(result.getTopics(), java.util.Arrays.asList("accounts", "billing"));
                assertEquals(result.getName(), "sub-name");
                org.testng.Assert.assertNull(result.getTopic());
                org.mockito.ArgumentCaptor<Subscription> created = org.mockito.ArgumentCaptor
                                .forClass(Subscription.class);
                verify(subscriptionDAO).addSubscription(any(Connection.class), created.capture());
                assertEquals(created.getValue().getTopicIds(), java.util.Arrays.asList("a", "b"));
                assertEquals(created.getValue().getName(), "sub-name");
        }

        @Test
        public void rejectsNormalizedDuplicateTopicsBeforeWriting() {
                org.testng.Assert.expectThrows(EventNotificationServiceException.class,
                                () -> subscriptionService.createMultiTopicSubscription("org1", "group1",
                                                "sub-name",
                                                java.util.Arrays.asList("accounts", " ACCOUNTS "),
                                                new FilterDTO(PurposeFilterMode.ALL, Collections.emptyList()),
                                                new DeliveryConfigDTO(DeliveryMode.POLL, null, "secret")));
                verify(subscriptionDAO, never()).addSubscription(any(Connection.class), any(Subscription.class));
        }

        @Mock
        private SubscriptionDAO subscriptionDAO;

        @Mock
        private TopicDAO topicDAO;

        @Mock
        private DeliveryDAO deliveryDAO;

        @Mock
        private DeliveryAckDAO deliveryAckDAO;

        @Mock
        private DPDPConfigurationService configurationService;

        @Mock
        private Connection connection;

        private SubscriptionServiceImpl subscriptionService;

        @BeforeMethod
        public void setUp() throws Exception {
                MockitoAnnotations.openMocks(this);
                DataSource dataSource = mock(DataSource.class);
                when(dataSource.getConnection()).thenReturn(connection);
                setStaticInstance(null);
                setStaticDataSource(dataSource);
                when(configurationService.getEventNotificationThreadPoolSize()).thenReturn(4);
                when(configurationService.getEventNotificationBaseBackoffSeconds()).thenReturn(5L);
                when(configurationService.getEventNotificationMaxRetries()).thenReturn(5);
                when(configurationService.isEventNotificationHttpCallbackUrlAllowed()).thenReturn(true);
                when(configurationService.getEventNotificationAllowedCallbackPorts())
                                .thenReturn(DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_ALLOWED_CALLBACK_PORTS);
                when(configurationService.isEventNotificationPrivateNetworkCallbackTargetsAllowed()).thenReturn(false);
                when(configurationService.getEventNotificationMaxVerificationResponseBodyBytes()).thenReturn(4096);
                when(configurationService.getEventNotificationMaxSubscriptionTopics())
                                .thenReturn(DPDPCommonConstants.DEFAULT_EVENT_NOTIFICATIONS_MAX_SUBSCRIPTION_TOPICS);
                subscriptionService = new SubscriptionServiceImpl(subscriptionDAO, topicDAO, deliveryDAO,
                                deliveryAckDAO,
                                configurationService);
        }

        @org.testng.annotations.AfterMethod
        public void tearDown() throws Exception {
                setStaticDataSource(null);
                setStaticInstance(null);
        }

        private static void setStaticDataSource(DataSource dataSource) throws Exception {
                Field field = org.wso2.dpdp.accelerator.common.persistence.JDBCPersistenceManager.class
                                .getDeclaredField("dataSource");
                field.setAccessible(true);
                field.set(null, dataSource);
        }

        private static void setStaticInstance(
                        org.wso2.dpdp.accelerator.common.persistence.JDBCPersistenceManager instance) throws Exception {
                Field field = org.wso2.dpdp.accelerator.common.persistence.JDBCPersistenceManager.class
                                .getDeclaredField("instance");
                field.setAccessible(true);
                field.set(null, instance);
        }

        @Test
        public void testCreatePollSubscriptionSuccess() {
                Topic topic = new Topic("t1", "org1", "user-consent", "desc", "active");
                Map<String, Topic> resolved = new HashMap<>();
                resolved.put("user-consent", topic);
                when(topicDAO.getTopicsByOrgAndNames(any(Connection.class), eq("org1"), any())).thenReturn(resolved);
                doNothing().when(subscriptionDAO).addSubscription(any(Connection.class), any(Subscription.class));

                FilterDTO filter = new FilterDTO(PurposeFilterMode.ALL, Collections.emptyList());
                DeliveryConfigDTO delivery = new DeliveryConfigDTO(DeliveryMode.POLL, null, "secret123");

                SubscriptionDTO result = subscriptionService.createMultiTopicSubscription("org1", "group1",
                                "sub-name", Collections.singletonList("user-consent"), filter, delivery);
                assertNotNull(result);
                assertEquals(result.getStatus(), SubscriptionStatus.ACTIVE);
        }

        @Test(expectedExceptions = EventNotificationServiceException.class)
        public void testCreateSubscriptionMissingTopic() {
                FilterDTO filter = new FilterDTO(PurposeFilterMode.ALL, Collections.emptyList());
                DeliveryConfigDTO delivery = new DeliveryConfigDTO(DeliveryMode.POLL, null, "secret123");
                subscriptionService.createMultiTopicSubscription("org1", "group1", "sub-name", null, filter, delivery);
        }

        @Test
        public void testCreateSubscriptionNullGroupIdDefaultsToOrgId() {
                Topic topic = new Topic("t1", "org1", "topic1", "desc", "active");
                Map<String, Topic> resolved = new HashMap<>();
                resolved.put("topic1", topic);
                when(topicDAO.getTopicsByOrgAndNames(any(Connection.class), eq("org1"), any())).thenReturn(resolved);
                doNothing().when(subscriptionDAO).addSubscription(any(Connection.class), any(Subscription.class));

                FilterDTO filter = new FilterDTO(PurposeFilterMode.ALL, Collections.emptyList());
                DeliveryConfigDTO delivery = new DeliveryConfigDTO(DeliveryMode.POLL, null, "secret123");
                SubscriptionDTO result = subscriptionService.createMultiTopicSubscription("org1", null,
                                "sub-name", Collections.singletonList("topic1"), filter, delivery);
                assertNotNull(result);
                assertEquals(result.getGroupId(), "org1");
        }

        @Test
        public void testCreateSubscriptionBlankGroupIdDefaultsToOrgId() {
                Topic topic = new Topic("t1", "org1", "topic1", "desc", "active");
                Map<String, Topic> resolved = new HashMap<>();
                resolved.put("topic1", topic);
                when(topicDAO.getTopicsByOrgAndNames(any(Connection.class), eq("org1"), any())).thenReturn(resolved);
                doNothing().when(subscriptionDAO).addSubscription(any(Connection.class), any(Subscription.class));

                FilterDTO filter = new FilterDTO(PurposeFilterMode.ALL, Collections.emptyList());
                DeliveryConfigDTO delivery = new DeliveryConfigDTO(DeliveryMode.POLL, null, "secret123");
                SubscriptionDTO result = subscriptionService.createMultiTopicSubscription("org1", "   ",
                                "sub-name", Collections.singletonList("topic1"), filter, delivery);
                assertNotNull(result);
                assertEquals(result.getGroupId(), "org1");
        }

        @Test(expectedExceptions = EventNotificationServiceException.class)
        public void testCreateSubscriptionNullOrgId() {
                FilterDTO filter = new FilterDTO(PurposeFilterMode.ALL, Collections.emptyList());
                DeliveryConfigDTO delivery = new DeliveryConfigDTO(DeliveryMode.POLL, null, "secret123");
                subscriptionService.createMultiTopicSubscription(null, "group1",
                                "sub-name", Collections.singletonList("topic1"), filter, delivery);
        }

        @Test(expectedExceptions = EventNotificationServiceException.class)
        public void testCreateSpecificSubscriptionMissingPurposes() {
                Topic topic = new Topic("t1", "org1", "topic1", "desc", "active");
                Map<String, Topic> resolved = new HashMap<>();
                resolved.put("topic1", topic);
                when(topicDAO.getTopicsByOrgAndNames(any(Connection.class), eq("org1"), any())).thenReturn(resolved);

                FilterDTO filter = new FilterDTO(PurposeFilterMode.SPECIFIC, Collections.emptyList());
                DeliveryConfigDTO delivery = new DeliveryConfigDTO(DeliveryMode.POLL, null, "secret123");
                subscriptionService.createMultiTopicSubscription("org1", "group1",
                                "sub-name", Collections.singletonList("topic1"), filter, delivery);
        }

        @Test(expectedExceptions = EventNotificationServiceException.class)
        public void testCreateExceptSubscriptionMissingPurposes() {
                Topic topic = new Topic("t1", "org1", "topic1", "desc", "active");
                Map<String, Topic> resolved = new HashMap<>();
                resolved.put("topic1", topic);
                when(topicDAO.getTopicsByOrgAndNames(any(Connection.class), eq("org1"), any())).thenReturn(resolved);

                FilterDTO filter = new FilterDTO(PurposeFilterMode.EXCEPT, Collections.emptyList());
                DeliveryConfigDTO delivery = new DeliveryConfigDTO(DeliveryMode.POLL, null, "secret123");
                subscriptionService.createMultiTopicSubscription("org1", "group1",
                                "sub-name", Collections.singletonList("topic1"), filter, delivery);
        }

        @Test
        public void testCreateExceptSubscriptionWithPurposesSucceeds() {
                Topic topic = new Topic("t1", "org1", "topic1", "desc", "active");
                Map<String, Topic> resolved = new HashMap<>();
                resolved.put("topic1", topic);
                when(topicDAO.getTopicsByOrgAndNames(any(Connection.class), eq("org1"), any())).thenReturn(resolved);
                doNothing().when(subscriptionDAO).addSubscription(any(Connection.class), any(Subscription.class));

                FilterDTO filter = new FilterDTO(PurposeFilterMode.EXCEPT,
                                java.util.Arrays.asList("marketing", "billing"));
                DeliveryConfigDTO delivery = new DeliveryConfigDTO(DeliveryMode.POLL, null, "secret123");

                SubscriptionDTO result = subscriptionService.createMultiTopicSubscription("org1", "group1",
                                "sub-name", Collections.singletonList("topic1"), filter, delivery);
                assertNotNull(result);
                assertEquals(result.getStatus(), SubscriptionStatus.ACTIVE);
        }

        @Test
        public void testCreateSingleTopicLifecycleSubscriptionWithSpecificFilterRejectsWith422() {
                FilterDTO filter = new FilterDTO(PurposeFilterMode.SPECIFIC, Collections.singletonList("marketing"));
                DeliveryConfigDTO delivery = new DeliveryConfigDTO(DeliveryMode.POLL, null, "secret123");

                EventNotificationServiceException ex = org.testng.Assert.expectThrows(
                                EventNotificationServiceException.class,
                                () -> subscriptionService.createMultiTopicSubscription("org1", "group1",
                                                "sub-name", Collections.singletonList("user.data.change"), filter, delivery));
                assertEquals(ex.getStatusCode(), 422);
                assertEquals(ex.getDescription(), "Subscriptions containing user lifecycle topics require the all purpose filter.");
        }

        @Test
        public void testCreateSingleTopicUserAccountDeleteWithExceptFilterRejectsWith422() {
                FilterDTO filter = new FilterDTO(PurposeFilterMode.EXCEPT, Collections.singletonList("marketing"));
                DeliveryConfigDTO delivery = new DeliveryConfigDTO(DeliveryMode.POLL, null, "secret123");

                EventNotificationServiceException ex = org.testng.Assert.expectThrows(
                                EventNotificationServiceException.class,
                                () -> subscriptionService.createMultiTopicSubscription("org1", "group1",
                                                "sub-name", Collections.singletonList("user.account.delete"), filter, delivery));
                assertEquals(ex.getStatusCode(), 422);
                assertEquals(ex.getDescription(), "Subscriptions containing user lifecycle topics require the all purpose filter.");
        }

        @Test
        public void testCreateMultiTopicLifecycleSubscriptionWithSpecificFilterRejectsWith422() {
                FilterDTO filter = new FilterDTO(PurposeFilterMode.SPECIFIC, Collections.singletonList("marketing"));
                DeliveryConfigDTO delivery = new DeliveryConfigDTO(DeliveryMode.POLL, null, "secret123");

                EventNotificationServiceException ex = org.testng.Assert.expectThrows(
                                EventNotificationServiceException.class,
                                () -> subscriptionService.createMultiTopicSubscription("org1", "group1",
                                                "sub-name", java.util.Arrays.asList("consent.update", "user.data.change"), filter, delivery));
                assertEquals(ex.getStatusCode(), 422);
                assertEquals(ex.getDescription(), "Subscriptions containing user lifecycle topics require the all purpose filter.");
        }

        @Test
        public void testCreateLifecycleSubscriptionWithAllFilterSucceeds() {
                Topic topic = new Topic("t1", "org1", "user.data.change", "desc", "active");
                Map<String, Topic> resolved = new HashMap<>();
                resolved.put("user.data.change", topic);
                when(topicDAO.getTopicsByOrgAndNames(any(Connection.class), eq("org1"), any())).thenReturn(resolved);
                doNothing().when(subscriptionDAO).addSubscription(any(Connection.class), any(Subscription.class));

                FilterDTO filter = new FilterDTO(PurposeFilterMode.ALL, Collections.emptyList());
                DeliveryConfigDTO delivery = new DeliveryConfigDTO(DeliveryMode.POLL, null, "secret123");

                SubscriptionDTO result = subscriptionService.createMultiTopicSubscription("org1", "group1",
                                "sub-name", Collections.singletonList("user.data.change"), filter, delivery);
                assertNotNull(result);
                assertEquals(result.getStatus(), SubscriptionStatus.ACTIVE);
        }

        @Test
        public void testCreateSubscriptionTopicDeregisteredUnderLockReturns409() throws Exception {
                Topic topic = new Topic("t1", "org1", "user-consent", "desc", "active");
                Map<String, Topic> resolved = new HashMap<>();
                resolved.put("user-consent", topic);
                when(topicDAO.getTopicsByOrgAndNames(any(Connection.class), eq("org1"), any())).thenReturn(resolved);
                doThrow(new EventNotificationInvalidStateException(
                                org.wso2.dpdp.accelerator.event.notifications.common.constants.EventNotificationCommonConstants.ERROR_TOPIC_NOT_ACTIVE))
                                .when(subscriptionDAO).addSubscription(any(Connection.class), any(Subscription.class));

                FilterDTO filter = new FilterDTO(PurposeFilterMode.ALL, Collections.emptyList());
                DeliveryConfigDTO delivery = new DeliveryConfigDTO(DeliveryMode.POLL, null, "secret123");

                EventNotificationServiceException ex = org.testng.Assert.expectThrows(
                                EventNotificationServiceException.class,
                                () -> subscriptionService.createMultiTopicSubscription("org1", "group1",
                                                "sub-name", Collections.singletonList("user-consent"), filter, delivery));
                assertEquals(ex.getStatusCode(), 409);
                assertEquals(ex.getDescription(),
                                String.format(EventNotificationServiceConstants.TOPIC_NOT_ACTIVE_ERROR_MSG, "user-consent"));
        }

        @Test
        public void testCreateMultiTopicSubscriptionDeregisteredUnderLockReturns409() throws Exception {
                Topic topic1 = new Topic("t1", "org1", "user-consent", "desc", "active");
                Topic topic2 = new Topic("t2", "org1", "user-auth", "desc", "active");
                Map<String, Topic> resolved = new HashMap<>();
                resolved.put("user-consent", topic1);
                resolved.put("user-auth", topic2);
                when(topicDAO.getTopicsByOrgAndNames(any(Connection.class), eq("org1"), any())).thenReturn(resolved);
                doThrow(new EventNotificationInvalidStateException(
                                org.wso2.dpdp.accelerator.event.notifications.common.constants.EventNotificationCommonConstants.ERROR_TOPIC_NOT_ACTIVE))
                                .when(subscriptionDAO).addSubscription(any(Connection.class), any(Subscription.class));

                FilterDTO filter = new FilterDTO(PurposeFilterMode.ALL, Collections.emptyList());
                DeliveryConfigDTO delivery = new DeliveryConfigDTO(DeliveryMode.POLL, null, "secret123");

                EventNotificationServiceException ex = org.testng.Assert.expectThrows(
                                EventNotificationServiceException.class,
                                () -> subscriptionService.createMultiTopicSubscription("org1", "group1",
                                                "sub-name", java.util.Arrays.asList("user-consent", "user-auth"), filter, delivery));
                assertEquals(ex.getStatusCode(), 409);
                assertEquals(ex.getDescription(), EventNotificationServiceConstants.TOPICS_NOT_ACTIVE_ERROR_MSG);
        }

        @Test
        public void testListSubscriptions() {
                Subscription sub = sub("sub1", "org1", "t1", "POLL", null, "ACTIVE");
                PaginatedDAOResult<Subscription> daoResult = new PaginatedDAOResult<>(Collections.singletonList(sub),
                                1);
                when(subscriptionDAO.listSubscriptions(any(Connection.class), eq("org1"), eq("active"), any(), any(),
                                eq(10), eq(0), eq("asc"))).thenReturn(daoResult);
                when(topicDAO.getTopicsByIds(any(Connection.class), eq(Collections.singletonList("t1")), eq("org1")))
                                .thenReturn(Collections.singletonList(
                                                new Topic("t1", "org1", "user-consent", "desc", "active")));

                PaginatedResult<SubscriptionDTO> result = subscriptionService.listSubscriptions("org1", "active", null,
                                null, 10, 0, "asc");
                assertNotNull(result);
                assertEquals(result.getTotal(), 1);
                assertEquals(result.getItems().get(0).getSubscriptionId(), "sub1");
        }

        @Test
        public void testGetSubscriptionSuccess() {
                Subscription sub = sub("sub1", "org1", "t1", "POLL", null, "ACTIVE");
                when(subscriptionDAO.getSubscriptionById(any(Connection.class), eq("sub1"), eq("org1")))
                                .thenReturn(Optional.of(sub));
                when(topicDAO.getTopicsByIds(any(Connection.class), eq(Collections.singletonList("t1")), eq("org1")))
                                .thenReturn(Collections.singletonList(
                                                new Topic("t1", "org1", "user-consent", "desc", "active")));

                SubscriptionDTO result = subscriptionService.getSubscription("org1", "sub1");
                assertNotNull(result);
                assertEquals(result.getSubscriptionId(), "sub1");
        }

        @Test
        public void testDeleteSubscriptionSuccess() {
                Subscription sub = sub("sub1", "org1", "t1", "POLL", null, "ACTIVE");
                when(subscriptionDAO.getSubscriptionById(any(Connection.class), eq("sub1"), eq("org1")))
                                .thenReturn(Optional.of(sub));
                when(subscriptionDAO.deleteSubscriptionAtomic(any(Connection.class), eq("sub1"), eq("org1"),
                                eq("ACTIVE"))).thenReturn(true);
                when(topicDAO.getTopicsByIds(any(Connection.class), eq(Collections.singletonList("t1")), eq("org1")))
                                .thenReturn(Collections.singletonList(
                                                new Topic("t1", "org1", "user-consent", "desc", "active")));

                SubscriptionDTO deleted = subscriptionService.deleteSubscription("org1", "sub1");
                assertNotNull(deleted);
                assertEquals(deleted.getSubscriptionId(), "sub1");
                assertEquals(deleted.getStatus(), SubscriptionStatus.DELETED);
                verify(subscriptionDAO).deleteSubscriptionAtomic(any(Connection.class), eq("sub1"), eq("org1"),
                                eq("ACTIVE"));
        }

        @Test(expectedExceptions = EventNotificationServiceException.class)
        public void testCreateWebhookSubscriptionInvalidCallbackUrl() {
                Topic topic = new Topic("t1", "org1", "user-consent", "desc", "active");
                when(topicDAO.getTopicByOrgAndName(any(Connection.class), eq("org1"), eq("user-consent")))
                                .thenReturn(Optional.of(topic));

                FilterDTO filter = new FilterDTO(PurposeFilterMode.ALL, Collections.emptyList());
                DeliveryConfigDTO delivery = new DeliveryConfigDTO(DeliveryMode.WEBHOOK,
                                "http://127.0.0.1:8080/callback", "secret123");

                subscriptionService.createMultiTopicSubscription("org1", "group1",
                                "sub-name", Collections.singletonList("user-consent"), filter, delivery);
        }

        @Test
        public void testCreateWebhookSubscriptionAllowsConfiguredCustomPort() {
                when(configurationService.getEventNotificationAllowedCallbackPorts())
                                .thenReturn(Collections.singleton(9443));
                Topic topic = new Topic("t1", "org1", "user-consent", "desc", "active");
                Map<String, Topic> resolved = new HashMap<>();
                resolved.put("user-consent", topic);
                when(topicDAO.getTopicsByOrgAndNames(any(Connection.class), eq("org1"), any())).thenReturn(resolved);
                doNothing().when(subscriptionDAO).addSubscription(any(Connection.class), any(Subscription.class));

                FilterDTO filter = new FilterDTO(PurposeFilterMode.ALL, Collections.emptyList());
                DeliveryConfigDTO delivery = new DeliveryConfigDTO(DeliveryMode.WEBHOOK,
                                "https://93.184.216.34:9443/callback", "secret123");

                SubscriptionDTO result = subscriptionService.createMultiTopicSubscription("org1", "group1",
                                "sub-name", Collections.singletonList("user-consent"), filter, delivery);
                assertNotNull(result);
        }

        @Test(expectedExceptions = EventNotificationServiceException.class)
        public void testCreateWebhookSubscriptionRejectsPrivateNetworkTargetByDefault() {
                Topic topic = new Topic("t1", "org1", "user-consent", "desc", "active");
                Map<String, Topic> resolved = new HashMap<>();
                resolved.put("user-consent", topic);
                when(topicDAO.getTopicsByOrgAndNames(any(Connection.class), eq("org1"), any())).thenReturn(resolved);

                FilterDTO filter = new FilterDTO(PurposeFilterMode.ALL, Collections.emptyList());
                DeliveryConfigDTO delivery = new DeliveryConfigDTO(DeliveryMode.WEBHOOK,
                                "https://192.168.1.10:443/callback", "secret123");

                subscriptionService.createMultiTopicSubscription("org1", "group1",
                                "sub-name", Collections.singletonList("user-consent"), filter, delivery);
        }

        @Test
        public void testCreateWebhookSubscriptionAllowsPrivateNetworkTargetWhenConfigured() {
                when(configurationService.isEventNotificationPrivateNetworkCallbackTargetsAllowed()).thenReturn(true);
                Topic topic = new Topic("t1", "org1", "user-consent", "desc", "active");
                Map<String, Topic> resolved = new HashMap<>();
                resolved.put("user-consent", topic);
                when(topicDAO.getTopicsByOrgAndNames(any(Connection.class), eq("org1"), any())).thenReturn(resolved);
                doNothing().when(subscriptionDAO).addSubscription(any(Connection.class), any(Subscription.class));

                FilterDTO filter = new FilterDTO(PurposeFilterMode.ALL, Collections.emptyList());
                DeliveryConfigDTO delivery = new DeliveryConfigDTO(DeliveryMode.WEBHOOK,
                                "https://192.168.1.10:443/callback", "secret123");

                SubscriptionDTO result = subscriptionService.createMultiTopicSubscription("org1", "group1",
                                "sub-name", Collections.singletonList("user-consent"), filter, delivery);
                assertNotNull(result);
        }

        @Test
        public void testCreateWebhookSubscriptionRequiresSharedSecret() {
                Topic topic = new Topic("t1", "org1", "user-consent", "desc", "active");
                Map<String, Topic> resolved = new HashMap<>();
                resolved.put("user-consent", topic);
                when(topicDAO.getTopicsByOrgAndNames(any(Connection.class), eq("org1"), any())).thenReturn(resolved);

                FilterDTO filter = new FilterDTO(PurposeFilterMode.ALL, Collections.emptyList());
                DeliveryConfigDTO delivery = new DeliveryConfigDTO(DeliveryMode.WEBHOOK,
                                "https://93.184.216.34:443/callback", " ");

                EventNotificationServiceException exception = expectThrows(EventNotificationServiceException.class,
                                () -> subscriptionService.createMultiTopicSubscription("org1", "group1",
                                                "sub-name", Collections.singletonList("user-consent"), filter, delivery));
                assertEquals(exception.getStatusCode(), 400);
                assertEquals(exception.getDescription(),
                                EventNotificationServiceConstants.SHARED_SECRET_REQUIRED_ERROR_MSG);
                verify(subscriptionDAO, never()).addSubscription(any(Connection.class), any(Subscription.class));
        }

        @Test(expectedExceptions = EventNotificationServiceException.class)
        public void testRetryVerificationForPendingSubscriptionWithoutCallbackUrl() {
                Subscription sub = sub("sub1", "org1", "t1", "WEBHOOK", null, "PENDING");
                when(subscriptionDAO.getSubscriptionById(any(Connection.class), eq("sub1"), eq("org1")))
                                .thenReturn(Optional.of(sub));

                subscriptionService.retryVerification("org1", "sub1");
        }

        @Test
        public void testCreateWebhookSubscriptionDifferentCallbacksSucceeds() {
                Topic topic = new Topic("t1", "org1", "user-consent", "desc", "active");
                Map<String, Topic> resolved = new HashMap<>();
                resolved.put("user-consent", topic);
                when(topicDAO.getTopicsByOrgAndNames(any(Connection.class), eq("org1"), any())).thenReturn(resolved);

                Subscription existingSub = sub("sub1", "org1", "t1", "WEBHOOK", "https://93.184.216.34:443/callback1",
                                "ACTIVE");
                when(subscriptionDAO.getLiveSubscriptionsByOrgAndTopics(any(Connection.class), eq("org1"), any()))
                                .thenReturn(Collections.singletonList(existingSub));
                doNothing().when(subscriptionDAO).addSubscription(any(Connection.class), any(Subscription.class));

                FilterDTO filter = new FilterDTO(PurposeFilterMode.ALL, Collections.emptyList());
                DeliveryConfigDTO delivery = new DeliveryConfigDTO(DeliveryMode.WEBHOOK,
                                "https://93.184.216.34:443/callback2", "secret2");

                SubscriptionDTO result = subscriptionService.createMultiTopicSubscription("org1", "org1",
                                "sub-name", Collections.singletonList("user-consent"), filter, delivery);
                assertNotNull(result);
                assertEquals(result.getStatus(), SubscriptionStatus.PENDING);
        }

        @Test(expectedExceptions = EventNotificationServiceException.class)
        public void testCreateWebhookSubscriptionSameCallbackFailsWithConflict() {
                Topic topic = new Topic("t1", "org1", "user-consent", "desc", "active");
                Map<String, Topic> resolved = new HashMap<>();
                resolved.put("user-consent", topic);
                when(topicDAO.getTopicsByOrgAndNames(any(Connection.class), eq("org1"), any())).thenReturn(resolved);

                Subscription existingSub = sub("sub1", "org1", "t1", "WEBHOOK", "https://93.184.216.34:443/callback1",
                                "ACTIVE");
                when(subscriptionDAO.getLiveSubscriptionsByOrgAndTopics(any(Connection.class), eq("org1"), any()))
                                .thenReturn(Collections.singletonList(existingSub));

                FilterDTO filter = new FilterDTO(PurposeFilterMode.ALL, Collections.emptyList());
                DeliveryConfigDTO delivery = new DeliveryConfigDTO(DeliveryMode.WEBHOOK,
                                "https://93.184.216.34:443/callback1", "secret2");

                subscriptionService.createMultiTopicSubscription("org1", null,
                                "sub-name", Collections.singletonList("user-consent"), filter, delivery);
        }

        @Test(expectedExceptions = EventNotificationServiceException.class)
        public void testCreatePollSubscriptionWhenWebhookExistsFailsWithConflict() {
                Topic topic = new Topic("t1", "org1", "user-consent", "desc", "active");
                Map<String, Topic> resolved = new HashMap<>();
                resolved.put("user-consent", topic);
                when(topicDAO.getTopicsByOrgAndNames(any(Connection.class), eq("org1"), any())).thenReturn(resolved);

                Subscription existingSub = sub("sub1", "org1", "t1", "WEBHOOK", "https://93.184.216.34:443/callback",
                                "ACTIVE");
                when(subscriptionDAO.getLiveSubscriptionsByOrgAndTopics(any(Connection.class), eq("org1"), any()))
                                .thenReturn(Collections.singletonList(existingSub));

                FilterDTO filter = new FilterDTO(PurposeFilterMode.ALL, Collections.emptyList());
                DeliveryConfigDTO delivery = new DeliveryConfigDTO(DeliveryMode.POLL, null, "secret2");

                subscriptionService.createMultiTopicSubscription("org1", null,
                                "sub-name", Collections.singletonList("user-consent"), filter, delivery);
        }

        @Test(expectedExceptions = EventNotificationServiceException.class)
        public void testCreateWebhookSubscriptionWhenPollExistsFailsWithConflict() {
                Topic topic = new Topic("t1", "org1", "user-consent", "desc", "active");
                Map<String, Topic> resolved = new HashMap<>();
                resolved.put("user-consent", topic);
                when(topicDAO.getTopicsByOrgAndNames(any(Connection.class), eq("org1"), any())).thenReturn(resolved);

                Subscription existingSub = sub("sub1", "org1", "t1", "POLL", null, "ACTIVE");
                when(subscriptionDAO.getLiveSubscriptionsByOrgAndTopics(any(Connection.class), eq("org1"), any()))
                                .thenReturn(Collections.singletonList(existingSub));

                FilterDTO filter = new FilterDTO(PurposeFilterMode.ALL, Collections.emptyList());
                DeliveryConfigDTO delivery = new DeliveryConfigDTO(DeliveryMode.WEBHOOK,
                                "https://93.184.216.34:443/callback", "secret2");

                subscriptionService.createMultiTopicSubscription("org1", null,
                                "sub-name", Collections.singletonList("user-consent"), filter, delivery);
        }

        @Test
        public void testCreateSubscriptionNullOrEmptyNameThrowsBadRequest() {
                FilterDTO filter = new FilterDTO(PurposeFilterMode.ALL, Collections.emptyList());
                DeliveryConfigDTO delivery = new DeliveryConfigDTO(DeliveryMode.POLL, null, "secret123");

                EventNotificationServiceException ex1 = expectThrows(EventNotificationServiceException.class,
                                () -> subscriptionService.createMultiTopicSubscription("org1", "group1",
                                                null, Collections.singletonList("t1"), filter, delivery));
                assertEquals(ex1.getStatusCode(), 400);

                EventNotificationServiceException ex2 = expectThrows(EventNotificationServiceException.class,
                                () -> subscriptionService.createMultiTopicSubscription("org1", "group1",
                                                "", Collections.singletonList("t1"), filter, delivery));
                assertEquals(ex2.getStatusCode(), 400);

                EventNotificationServiceException ex3 = expectThrows(EventNotificationServiceException.class,
                                () -> subscriptionService.createMultiTopicSubscription("org1", "group1",
                                                "   ", Collections.singletonList("t1"), filter, delivery));
                assertEquals(ex3.getStatusCode(), 400);
        }

        @Test
        public void testCreateSubscriptionNameExceeds225CharsThrowsBadRequest() {
                FilterDTO filter = new FilterDTO(PurposeFilterMode.ALL, Collections.emptyList());
                DeliveryConfigDTO delivery = new DeliveryConfigDTO(DeliveryMode.POLL, null, "secret123");
                String longName = new String(new char[226]).replace('\0', 'a');

                EventNotificationServiceException ex = expectThrows(EventNotificationServiceException.class,
                                () -> subscriptionService.createMultiTopicSubscription("org1", "group1",
                                                longName, Collections.singletonList("t1"), filter, delivery));
                assertEquals(ex.getStatusCode(), 400);
        }

        @Test
        public void testCreateSubscriptionTrimsName() {
                Topic topic = new Topic("t1", "org1", "user-consent", "desc", "active");
                Map<String, Topic> resolved = new HashMap<>();
                resolved.put("user-consent", topic);
                when(topicDAO.getTopicsByOrgAndNames(any(Connection.class), eq("org1"), any())).thenReturn(resolved);
                doNothing().when(subscriptionDAO).addSubscription(any(Connection.class), any(Subscription.class));

                FilterDTO filter = new FilterDTO(PurposeFilterMode.ALL, Collections.emptyList());
                DeliveryConfigDTO delivery = new DeliveryConfigDTO(DeliveryMode.POLL, null, "secret123");

                SubscriptionDTO result = subscriptionService.createMultiTopicSubscription("org1", "group1",
                                "  trimmed-name  ", Collections.singletonList("user-consent"), filter, delivery);
                assertNotNull(result);
                assertEquals(result.getName(), "trimmed-name");
        }

        // ---- helpers ----

        private static Subscription sub(String id, String orgId, String topicId, String deliveryMode,
                        String callbackUrl, String status) {
                Subscription s = new Subscription();
                s.setSubscriptionId(id);
                s.setName(id + "-name");
                s.setOrgId(orgId);
                s.setGroupId(orgId);
                s.setTopicIds(Collections.singletonList(topicId));
                s.setTopicNames(Collections.emptyList());
                s.setPurposeFilterMode("ALL");
                s.setPurposes(Collections.emptyList());
                s.setDeliveryMode(deliveryMode);
                s.setCallbackUrl(callbackUrl);
                s.setSharedSecret("secret");
                s.setStatus(status);
                s.setCreatedAt(new Timestamp(System.currentTimeMillis()));
                s.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
                return s;
        }
}
