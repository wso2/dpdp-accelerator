package org.wso2.dpdp.accelerator.event.notifications.service.impl;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.common.persistence.JDBCPersistenceManager;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.Initiator;
import org.wso2.dpdp.accelerator.event.notifications.dao.TopicDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Topic;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.Optional;

import javax.sql.DataSource;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TopicServiceBranchesTest {
    @Mock private TopicDAO dao;
    private TopicServiceImpl service;
    private Connection connection;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        connection = mock(Connection.class);
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        setStaticInstance(null);
        setStaticDataSource(dataSource);
        service = new TopicServiceImpl(dao);
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

    @Test(expectedExceptions = org.wso2.dpdp.accelerator.event.notifications.common.exception.service.EventNotificationServiceException.class)
    public void createTopicFalseAddIsRejected() {
        when(dao.getTopicByOrgAndName(any(Connection.class), eq("org"), eq("topic"))).thenReturn(Optional.empty());
        when(dao.addTopic(any(Connection.class), any(Topic.class))).thenReturn(false);
        service.createTopic("org", "topic", null);
    }

    @Test(expectedExceptions = org.wso2.dpdp.accelerator.event.notifications.common.exception.service.EventNotificationServiceException.class)
    public void deleteSystemTopicIsRejected() {
        Topic topic = new Topic("t", "org", "system", null, "active", Initiator.SYSTEM.getValue());
        when(dao.getTopicById(any(Connection.class), eq("t"), eq("org"))).thenReturn(Optional.of(topic));
        service.deleteTopic("org", "t");
    }

    @Test(expectedExceptions = org.wso2.dpdp.accelerator.event.notifications.common.exception.service.EventNotificationServiceException.class)
    public void deleteDeletedTopicIsRejected() {
        Topic topic = new Topic("t", "org", "topic", null, "deleted", Initiator.USER.getValue());
        when(dao.getTopicById(any(Connection.class), eq("t"), eq("org"))).thenReturn(Optional.of(topic));
        service.deleteTopic("org", "t");
    }

    @Test(expectedExceptions = org.wso2.dpdp.accelerator.event.notifications.common.exception.service.EventNotificationServiceException.class)
    public void deleteLegacyDeregisteredTopicIsRejected() {
        Topic topic = new Topic("t", "org", "topic", null, "deregistered", Initiator.USER.getValue());
        when(dao.getTopicById(any(Connection.class), eq("t"), eq("org"))).thenReturn(Optional.of(topic));
        service.deleteTopic("org", "t");
    }

    @Test(expectedExceptions = org.wso2.dpdp.accelerator.event.notifications.common.exception.service.EventNotificationServiceException.class)
    public void deleteFalseUpdateIsRejected() {
        Topic topic = new Topic("t", "org", "topic", null, "active", Initiator.USER.getValue());
        when(dao.getTopicById(any(Connection.class), eq("t"), eq("org"))).thenReturn(Optional.of(topic));
        when(dao.deleteTopicAtomic(any(Connection.class), eq("t"), eq("org"))).thenReturn(false);
        service.deleteTopic("org", "t");
    }

    @Test
    public void getTopicReturnsEmptyForWrongOrgAndReturnsMappedTopic() {
        Topic wrong = new Topic("t", "other", "topic", null, "active", Initiator.USER.getValue());
        when(dao.getTopicById(any(Connection.class), eq("t"), eq("org"))).thenReturn(Optional.of(wrong));
        org.testng.Assert.assertTrue(service.getTopic("org", "t").isEmpty());
        when(dao.getTopicById(any(Connection.class), eq("t"), eq("org"))).thenReturn(Optional.of(
                new Topic("t", "org", "topic", "desc", "active", Initiator.USER.getValue())));
        org.testng.Assert.assertEquals(service.getTopic("org", "t").get().getName(), "topic");
    }

    @Test(expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = "database unavailable")
    public void getTopicPropagatesPersistenceFailure() {
        when(dao.getTopicById(any(Connection.class), eq("t"), eq("org")))
                .thenThrow(new RuntimeException("database unavailable"));

        service.getTopic("org", "t");
    }
}
