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
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.common.persistence.JDBCPersistenceManager;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.Initiator;
import org.wso2.dpdp.accelerator.event.notifications.common.exception.dao.EventNotificationDuplicateResourceException;
import org.wso2.dpdp.accelerator.event.notifications.common.exception.dao.EventNotificationInvalidStateException;
import org.wso2.dpdp.accelerator.event.notifications.common.exception.service.EventNotificationServiceException;
import org.wso2.dpdp.accelerator.event.notifications.dao.PaginatedDAOResult;
import org.wso2.dpdp.accelerator.event.notifications.dao.TopicDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Topic;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.TopicDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.model.PaginatedResult;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.Collections;
import java.util.Optional;

import javax.sql.DataSource;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TopicServiceImplTest {

    @Mock
    private TopicDAO topicDAO;

    private TopicServiceImpl topicService;
    private Connection connection;
    private DataSource dataSource;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        connection = mock(Connection.class);
        dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        setStaticInstance(null);
        setStaticDataSource(dataSource);
        topicService = new TopicServiceImpl(topicDAO);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        setStaticDataSource(null);
        setStaticInstance(null);
    }

    private static void setStaticDataSource(DataSource dataSource) throws Exception {
        Field field = JDBCPersistenceManager.class.getDeclaredField("dataSource");
        field.setAccessible(true);
        field.set(null, dataSource);
    }

    private static void setStaticInstance(JDBCPersistenceManager instance) throws Exception {
        Field field = JDBCPersistenceManager.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, instance);
    }

    @Test
    public void testCreateTopicSuccess() {
        when(topicDAO.getTopicByOrgAndName(any(Connection.class), eq("org1"), eq("user-consent"))).thenReturn(Optional.empty());
        when(topicDAO.addTopic(any(Connection.class), any(Topic.class))).thenReturn(true);

        TopicDTO result = topicService.createTopic("org1", "user-consent", "User consent events");
        assertNotNull(result);
        assertEquals(result.getName(), "user-consent");
        assertEquals(result.getDescription(), "User consent events");
        assertEquals(result.getStatus(), "active");
    }

    @Test(expectedExceptions = EventNotificationServiceException.class)
    public void testCreateTopicMissingOrgId() {
        topicService.createTopic(null, "user-consent", "desc");
    }

    @Test(expectedExceptions = EventNotificationServiceException.class)
    public void testCreateTopicMissingName() {
        topicService.createTopic("org1", "", "desc");
    }

    @Test(expectedExceptions = EventNotificationServiceException.class)
    public void testCreateTopicAlreadyExists() {
        Topic existing = new Topic("t1", "org1", "user-consent", "desc", "active");
        when(topicDAO.getTopicByOrgAndName(any(Connection.class), eq("org1"), eq("user-consent"))).thenReturn(Optional.of(existing));

        topicService.createTopic("org1", "user-consent", "desc");
    }

    @Test
    public void testListTopics() {
        Topic topic = new Topic("t1", "org1", "user-consent", "desc", "active");
        PaginatedDAOResult<Topic> daoResult = new PaginatedDAOResult<>(Collections.singletonList(topic), 1);
        when(topicDAO.listTopics(any(Connection.class), eq("org1"), eq("active"), eq(null), eq(10), eq(0), eq("asc"))).thenReturn(daoResult);

        PaginatedResult<TopicDTO> result = topicService.listTopics("org1", " ACTIVE ", null, 10, 0, "asc");
        assertNotNull(result);
        assertEquals(result.getTotal(), 1);
        assertEquals(result.getItems().size(), 1);
        assertEquals(result.getItems().get(0).getTopicId(), "t1");
    }

    @Test
    public void testDeleteTopicSuccess() {
        Topic topic = new Topic("t1", "org1", "user-consent", "desc", "active");
        when(topicDAO.getTopicById(any(Connection.class), eq("t1"), eq("org1"))).thenReturn(Optional.of(topic));
        when(topicDAO.deleteTopicAtomic(any(Connection.class), eq("t1"), eq("org1"))).thenReturn(true);

        TopicDTO result = topicService.deleteTopic("org1", "t1");
        assertNotNull(result);
        assertEquals(result.getStatus(), "deleted");
    }

    @Test(expectedExceptions = EventNotificationServiceException.class)
    public void testDeleteTopicHasActiveSubscriptionsReturns409() {
        Topic topic = new Topic("t1", "org1", "user-consent", "desc", "active");
        when(topicDAO.getTopicById(any(Connection.class), eq("t1"), eq("org1"))).thenReturn(Optional.of(topic));
        when(topicDAO.deleteTopicAtomic(any(Connection.class), eq("t1"), eq("org1"))).thenThrow(
                new EventNotificationInvalidStateException(
                        org.wso2.dpdp.accelerator.event.notifications.common.constants.EventNotificationCommonConstants.ERROR_TOPIC_HAS_ACTIVE_SUBSCRIPTIONS));

        topicService.deleteTopic("org1", "t1");
    }

    @Test(expectedExceptions = EventNotificationServiceException.class)
    public void testDeleteTopicNotFound() {
        when(topicDAO.getTopicById(any(Connection.class), eq("t99"), eq("org1"))).thenReturn(Optional.empty());
        topicService.deleteTopic("org1", "t99");
    }

    @Test(expectedExceptions = EventNotificationServiceException.class)
    public void testCreateTopicDataAccessExceptionMappedTo409() {
        when(topicDAO.getTopicByOrgAndName(any(Connection.class), eq("org1"), eq("user-consent"))).thenReturn(Optional.empty());
        when(topicDAO.addTopic(any(Connection.class), any(Topic.class))).thenThrow(
                new EventNotificationDuplicateResourceException(
                        "Duplicate key", null));

        topicService.createTopic("org1", "user-consent", "desc");
    }

    @Test
    public void testEnsureSystemTopicCreatesProtectedTopic() {
        when(topicDAO.getTopicByOrgAndName(any(Connection.class), eq("org1"), eq("consent.update"))).thenReturn(Optional.empty());
        when(topicDAO.addTopic(any(Connection.class), any(Topic.class))).thenAnswer(invocation -> {
            Topic topic = invocation.getArgument(1);
            assertEquals(topic.getOrgId(), "org1");
            assertEquals(topic.getName(), "consent.update");
            assertEquals(topic.getInitiatedBy(), Initiator.SYSTEM.getValue());
            return true;
        });

        TopicDTO result = topicService.ensureSystemTopic(" org1 ", " consent.update ", " description ");

        assertEquals(result.getName(), "consent.update");
        assertEquals(result.getDescription(), "description");
        assertEquals(result.getStatus(), "active");
        assertEquals(result.getInitiatedBy(), "system");
    }

    @Test
    public void testEnsureSystemTopicIsIdempotent() {
        Topic existing = new Topic("t1", "org1", "consent.update", "desc", "active", "system");
        when(topicDAO.getTopicByOrgAndName(any(Connection.class), eq("org1"), eq("consent.update"))).thenReturn(Optional.of(existing));

        TopicDTO result = topicService.ensureSystemTopic("org1", "consent.update", "desc");

        assertEquals(result.getTopicId(), "t1");
        assertEquals(result.getInitiatedBy(), "system");
        verify(topicDAO, never()).addTopic(any(Connection.class), any(Topic.class));
    }

    @Test(expectedExceptions = EventNotificationServiceException.class)
    public void testEnsureSystemTopicRejectsUserTopicCollision() {
        Topic existing = new Topic("t1", "org1", "consent.update", "desc", "active", "user");
        when(topicDAO.getTopicByOrgAndName(any(Connection.class), eq("org1"), eq("consent.update"))).thenReturn(Optional.of(existing));

        topicService.ensureSystemTopic("org1", "consent.update", "desc");
    }

    @Test
    public void testEnsureSystemTopicHandlesConcurrentSystemCreation() throws Exception {
        Connection recoveryConnection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection, recoveryConnection);
        Topic existing = new Topic("t1", "org1", "consent.update", "desc", "active", "system");
        when(topicDAO.getTopicByOrgAndName(eq(connection), eq("org1"), eq("consent.update")))
                .thenReturn(Optional.empty());
        when(topicDAO.addTopic(eq(connection), any(Topic.class))).thenThrow(
                new EventNotificationDuplicateResourceException("Duplicate key", null));
        when(topicDAO.getTopicByOrgAndName(eq(recoveryConnection), eq("org1"), eq("consent.update")))
                .thenReturn(Optional.of(existing));

        TopicDTO result = topicService.ensureSystemTopic("org1", "consent.update", "desc");

        assertEquals(result.getTopicId(), "t1");
        assertEquals(result.getInitiatedBy(), "system");
        verify(connection).rollback();
        verify(connection).close();
        verify(recoveryConnection).commit();
        verify(recoveryConnection).close();
    }

    @Test(expectedExceptions = EventNotificationServiceException.class)
    public void testEnsureSystemTopicRejectsConcurrentUserTopicCollision() throws Exception {
        Connection recoveryConnection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection, recoveryConnection);
        Topic existing = new Topic("t1", "org1", "consent.update", "desc", "active", "user");
        when(topicDAO.getTopicByOrgAndName(eq(connection), eq("org1"), eq("consent.update")))
                .thenReturn(Optional.empty());
        when(topicDAO.addTopic(eq(connection), any(Topic.class))).thenThrow(
                new EventNotificationDuplicateResourceException("Duplicate key", null));
        when(topicDAO.getTopicByOrgAndName(eq(recoveryConnection), eq("org1"), eq("consent.update")))
                .thenReturn(Optional.of(existing));

        topicService.ensureSystemTopic("org1", "consent.update", "desc");
    }

    @Test(expectedExceptions = EventNotificationServiceException.class)
    public void testDeleteSystemTopicIsForbidden() {
        Topic topic = new Topic("t1", "org1", "consent.update", "desc", "active", "system");
        when(topicDAO.getTopicById(any(Connection.class), eq("t1"), eq("org1"))).thenReturn(Optional.of(topic));

        topicService.deleteTopic("org1", "t1");
    }
}
