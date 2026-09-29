package org.wso2.dpdp.accelerator.event.notifications.dao.impl;

import org.mockito.Mockito;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.common.persistence.JDBCPersistenceManager;
import org.wso2.dpdp.accelerator.common.util.CryptoUtils;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.testng.Assert.expectThrows;

/** Exercises empty-result and validation paths across all read-only DAO entry points. */
public class DaoReadPathCoverageTest {

    private DataSource dataSource;
    private Connection connection;
    private PreparedStatement statement;
    private ResultSet resultSet;

    @BeforeMethod
    public void setUp() throws Exception {
        dataSource = Mockito.mock(DataSource.class);
        connection = Mockito.mock(Connection.class);
        statement = Mockito.mock(PreparedStatement.class);
        resultSet = Mockito.mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(connection.getAutoCommit()).thenReturn(true);
        setManagerDataSource(dataSource);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        setManagerDataSource(null);
    }

    @Test
    public void exercisesEmptyReadResultsAcrossDaos() throws Exception {
        SubscriptionDAOImpl subscriptions = new SubscriptionDAOImpl();
        subscriptions.getSubscriptionById(connection, "missing", "org");
        subscriptions.listSubscriptions(connection, "org", null, null, null, 20, 0, null);
        subscriptions.countActiveSubscriptionsForTopic(connection, "org", "topic");
        subscriptions.getPurposesBySubscriptionIds(connection, "org", java.util.Collections.singletonList("sub"));
        subscriptions.hasPendingOrInFlightDeliveries(connection, "sub", "org");
        subscriptions.getPendingSubscriptionsForRecovery(connection, new Timestamp(System.currentTimeMillis()), 10);

        TopicDAOImpl topics = new TopicDAOImpl();
        topics.getTopicById(connection, "missing", "org");
        topics.getTopicByOrgAndName(connection, "org", "missing");
        topics.listTopics(connection, "org", null, null, 20, 0, null);

        EventDAOImpl events = new EventDAOImpl();
        events.getEventById(connection, "missing", "org");
        events.getEventPurposes(connection, "event", "org");
        events.hasActiveEventsForTopic(connection, "topic", "org");
        events.searchEvents(connection, "org", null, null, null, null, null, null, 20, 0);

        DeliveryDAOImpl deliveries = new DeliveryDAOImpl();
        setConfiguration(deliveries);
        deliveries.getWebhookDeliveryById(connection, "delivery", "org");
        deliveries.getPendingWebhookDispatchContexts(connection, 10);
        deliveries.getStuckInFlightWebhookDispatchContexts(connection, 10, null);
        deliveries.getPendingPollDeliveries(connection, "org", "group", "subscription", 10);
        deliveries.getWebhookDeliveryAudits(connection, "delivery", "org");
        deliveries.getPollDeliveryById(connection, "delivery", "org");
        deliveries.getOrgDeliveryById(connection, "org", "delivery");
        deliveries.getSubscriptionDeliveryById(connection, "org", "sub", "delivery");
        deliveries.listSubscriptionDeliveries(connection, "org", "sub", 10, 0, new int[1]);
        deliveries.listOrgDeliveries(connection, "org", null, null, null, null, null, 10, 0, new int[1]);
        deliveries.listEventDeliveries(connection, "org", "event", 10, 0, new int[1]);
        deliveries.listOrgDeliveries(connection, "org", " delivered ", " sub ", " group ", "one, ,TWO", "a_%",
                10, 0, new int[1]);
        deliveries.listEventDeliveries(connection, "org", "event", 10, 0, null);
    }

    @Test
    public void translatesJdbcFailuresAcrossDaoReadPaths() throws Exception {
        when(statement.executeQuery()).thenThrow(new java.sql.SQLException("expected"));
        SubscriptionDAOImpl subscriptions = new SubscriptionDAOImpl();
        expectThrows(RuntimeException.class, () -> subscriptions.getSubscriptionById(connection, "sub", "org"));
        expectThrows(RuntimeException.class, () -> subscriptions.listSubscriptions(connection, "org", null, null, null, 20, 0, null));
        expectThrows(RuntimeException.class, () -> subscriptions.countActiveSubscriptionsForTopic(connection, "org", "topic"));
        expectThrows(RuntimeException.class, () -> subscriptions.hasPendingOrInFlightDeliveries(connection, "sub", "org"));
        expectThrows(RuntimeException.class, () -> subscriptions.getPendingSubscriptionsForRecovery(connection, new Timestamp(1), 10));

        TopicDAOImpl topics = new TopicDAOImpl();
        expectThrows(RuntimeException.class, () -> topics.getTopicById(connection, "topic", "org"));
        expectThrows(RuntimeException.class, () -> topics.getTopicByOrgAndName(connection, "org", "name"));
        expectThrows(RuntimeException.class, () -> topics.listTopics(connection, "org", null, null, 20, 0, null));

        EventDAOImpl events = new EventDAOImpl();
        expectThrows(RuntimeException.class, () -> events.getEventById(connection, "event", "org"));
        expectThrows(RuntimeException.class, () -> events.getEventPurposes(connection, "event", "org"));
        expectThrows(RuntimeException.class, () -> events.hasActiveEventsForTopic(connection, "topic", "org"));
        expectThrows(RuntimeException.class, () -> events.searchEvents(connection, "org", null, null, null, null, null, null, 20, 0));

        DeliveryDAOImpl deliveries = new DeliveryDAOImpl();
        setConfiguration(deliveries);
        expectThrows(RuntimeException.class, () -> deliveries.getWebhookDeliveryById(connection, "delivery", "org"));
        expectThrows(RuntimeException.class, () -> deliveries.getPendingWebhookDispatchContexts(connection, 10));
        expectThrows(RuntimeException.class,
                () -> deliveries.getPendingPollDeliveries(connection, "org", "group", "subscription", 10));
        expectThrows(RuntimeException.class, () -> deliveries.getWebhookDeliveryAudits(connection, "delivery", "org"));
        expectThrows(RuntimeException.class, () -> deliveries.getPollDeliveryById(connection, "delivery", "org"));
        expectThrows(RuntimeException.class, () -> deliveries.getOrgDeliveryById(connection, "org", "delivery"));
    }

    @Test
    public void coversEmptyInputReadGuards() throws Exception {
        DeliveryDAOImpl deliveries = new DeliveryDAOImpl();
        setConfiguration(deliveries);
        org.testng.Assert.assertTrue(deliveries.listEventDeliveries(connection, null, "event", 10, 0, null).isEmpty());
        org.testng.Assert.assertTrue(deliveries.listEventDeliveries(connection, "org", "", 10, 0, null).isEmpty());
        org.testng.Assert.assertTrue(new SubscriptionDAOImpl()
                .getPurposesBySubscriptionIds(connection, "org", Collections.emptyList()).isEmpty());
    }

    @Test
    public void loadDispatchContextsSkipsRowsWithDecryptionFailures() throws Exception {
        CryptoUtils.setTestModeEnabled(false);
        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getString(org.wso2.dpdp.accelerator.event.notifications.dao.constants.EventNotificationDBColumns.DELIVERY_ID))
                .thenReturn("del-fail", "del-ok");
        when(resultSet.getString(org.wso2.dpdp.accelerator.event.notifications.dao.constants.EventNotificationDBColumns.SHARED_SECRET))
                .thenReturn("dpdp_test_enc:bad-secret", "plain-secret");

        DeliveryDAOImpl deliveries = new DeliveryDAOImpl();
        setConfiguration(deliveries);
        java.util.List<org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDeliveryDispatchContext> result =
                deliveries.getPendingWebhookDispatchContexts(connection, 10);

        org.testng.Assert.assertEquals(result.size(), 1);
        org.testng.Assert.assertEquals(result.get(0).getDelivery().getDeliveryId(), "del-ok");
        org.testng.Assert.assertEquals(result.get(0).getSharedSecret(), "plain-secret");
    }

    private void setConfiguration(DeliveryDAOImpl dao) throws Exception {
        Field field = DeliveryDAOImpl.class.getDeclaredField("configurationService");
        field.setAccessible(true);
        DPDPConfigurationService config = Mockito.mock(DPDPConfigurationService.class);
        when(config.getEventNotificationStuckInFlightThresholdSeconds()).thenReturn(10);
        field.set(dao, config);
    }

    private void setManagerDataSource(Object value) throws Exception {
        Field field = JDBCPersistenceManager.class.getDeclaredField("dataSource");
        field.setAccessible(true);
        field.set(null, value);
    }
}
