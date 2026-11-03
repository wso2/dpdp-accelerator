package org.wso2.dpdp.accelerator.event.notifications.dao.impl;

import org.mockito.Mockito;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.common.persistence.JDBCPersistenceManager;
import org.wso2.dpdp.accelerator.event.notifications.common.constants.EventNotificationCommonConstants;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.TopicStatus;
import org.wso2.dpdp.accelerator.event.notifications.common.exception.dao.EventNotificationDuplicateResourceException;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Event;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.PollDelivery;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.PollDeliveryError;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Subscription;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Topic;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDelivery;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDeliveryAudit;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/** Covers JDBC write, claim, retry, and status-transition paths with a controlled connection. */
public class DaoWritePathCoverageTest {

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
        when(statement.executeUpdate()).thenReturn(1);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);
        when(connection.getAutoCommit()).thenReturn(true);
        setManagerDataSource(dataSource);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        setManagerDataSource(null);
    }

    @Test
    public void exercisesWebhookAndPollTransitions() throws Exception {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        WebhookDelivery webhook = new WebhookDelivery("d-1", "s-1", "e-1", "pending", 0,
                null, now, now, null);
        PollDelivery poll = new PollDelivery("p-1", "s-1", "e-1", "pending", now, null);
        WebhookDeliveryAudit audit = new WebhookDeliveryAudit("a-1", "e-1", "d-1", "org-1", "200", now, now);
        DeliveryDAOImpl dao = new DeliveryDAOImpl();

        assertTrue(dao.addWebhookDelivery(connection, webhook));
        assertTrue(dao.updateWebhookDeliveryStatus(connection, webhook));
        assertTrue(dao.markWebhookDeliveryCompleted(connection, "d-1", "org-1"));
        assertTrue(dao.addWebhookDeliveryAudit(connection, audit));
        assertTrue(dao.addPollDelivery(connection, poll));
        dao.updatePollDeliveryStatusesByDeliveryIds(connection, "org-1", "group-1", "s-1",
                Collections.singletonList("p-1"), Collections.emptyMap());
        assertTrue(dao.claimWebhookDelivery(connection, "d-1"));
        assertTrue(dao.claimStuckWebhookDelivery(connection, "d-1", now));
        assertTrue(dao.releaseWebhookDelivery(connection, "d-1", 1, now));
        assertTrue(dao.claimPollDelivery(connection, "p-1"));
        assertTrue(dao.updatePollDeliveryStatus(connection, "p-1", "completed"));
        assertTrue(dao.updatePollDeliveryStatus(connection, "p-1", "completed", "acknowledged"));
    }

    @Test
    public void pollStatusUpdatesAreScopedToTheSelectedSubscriptionAndPersistStructuredErrors() throws Exception {
        DeliveryDAOImpl dao = new DeliveryDAOImpl();
        Map<String, PollDeliveryError> errors = new LinkedHashMap<>();
        errors.put("delivery-2", new PollDeliveryError("processing_failed", "Unable to process event"));

        dao.updatePollDeliveryStatusesByDeliveryIds(connection, " org-1 ", " group-1 ", " subscription-1 ",
                Collections.singletonList(" delivery-1 "), errors);

        verify(statement, times(2)).addBatch();
        verify(statement, times(2)).executeBatch();
        verify(statement, times(2)).clearBatch();
        verify(connection).prepareStatement(contains("SUBSCRIPTION_ID = ?"));
    }

    @Test
    public void exercisesTransactionOwnedWrappersAndRetryOutcomes() {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        WebhookDelivery webhook = new WebhookDelivery("d-1", "s-1", "e-1", "pending", 0,
                null, now, now, null);
        WebhookDeliveryAudit audit = new WebhookDeliveryAudit("a-1", "e-1", "d-1", "org-1", "500", now, now);
        DeliveryDAOImpl dao = new DeliveryDAOImpl();

        assertTrue(dao.updateWebhookDeliveryStatus(connection, webhook));
        assertTrue(dao.recordSuccessfulAttempt(connection, audit, webhook));
        assertTrue(dao.recordRetryableFailure(connection, audit, "d-1", 1, now));
        assertTrue(dao.recordPermanentFailure(connection, audit, webhook));
        assertTrue(dao.addWebhookDeliveryAudit(connection, audit));
        assertTrue(dao.addPollDelivery(connection, new PollDelivery("p-1", "s-1", "e-1", "pending", now, null)));
        assertTrue(dao.claimWebhookDelivery(connection, "d-1"));
        assertTrue(dao.claimStuckWebhookDelivery(connection, "d-1", now));
        assertTrue(dao.releaseWebhookDelivery(connection, "d-1", 2, now));
        assertTrue(dao.claimPollDelivery(connection, "p-1"));
        assertTrue(dao.updatePollDeliveryStatus(connection, "p-1", "completed"));
        assertTrue(dao.updatePollDeliveryStatus(connection, "p-1", "completed", "acknowledged"));
    }

    @Test
    public void losingDeliveryStateTransitionDoesNotInsertAudit() throws Exception {
        when(statement.executeUpdate()).thenReturn(0);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        WebhookDelivery delivery = new WebhookDelivery("d-1", "s-1", "e-1", "delivered", 1,
                null, now, now, now);
        WebhookDeliveryAudit audit = new WebhookDeliveryAudit("a-1", "e-1", "d-1", "org-1", "200", now, now);
        DeliveryDAOImpl dao = new DeliveryDAOImpl();

        assertFalse(dao.recordSuccessfulAttempt(connection, audit, delivery));
        assertFalse(dao.recordRetryableFailure(connection, audit, "d-1", 1, now));
        assertFalse(dao.recordPermanentFailure(connection, audit, delivery));

        verify(connection, never()).prepareStatement(contains("INSERT INTO WEBHOOK_DELIVERY_AUDIT"));
    }

    @Test
    public void exercisesSubscriptionAndTopicWritePaths() throws Exception {
        when(resultSet.next()).thenReturn(true, true, false, false, false, false);
        when(resultSet.getString(1)).thenReturn("active");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        TopicDAOImpl topicDao = new TopicDAOImpl();
        assertTrue(topicDao.addTopic(connection, new Topic("t-1", "org-1", "accounts", "desc", "active")));
        assertTrue(topicDao.updateTopicStatus(connection, "t-1", "org-1", TopicStatus.ACTIVE));
        Subscription subscription = new Subscription();
        subscription.setSubscriptionId("s-1");
        subscription.setName("sub-write-coverage");
        subscription.setOrgId("org-1");
        subscription.setGroupId("group-1");
        subscription.setTopicIds(Collections.singletonList("t-1"));
        subscription.setPurposeFilterMode("all");
        subscription.setPurposes(Arrays.asList("marketing"));
        subscription.setDeliveryMode("webhook");
        subscription.setCallbackUrl("https://example.com/hook");
        subscription.setSharedSecret("secret");
        subscription.setStatus("pending");
        subscription.setCreatedAt(now);
        subscription.setUpdatedAt(now);
        SubscriptionDAOImpl subscriptionDao = new SubscriptionDAOImpl();
        try {
            subscriptionDao.addSubscription(connection, subscription);
        } catch (RuntimeException expected) {
            // Exercise the guarded invalid-state branch with the mock result set.
        }
        assertTrue(subscriptionDao.updateSubscriptionStatus(connection, "s-1", "org-1", "active"));
        assertTrue(subscriptionDao.updateSubscriptionStatus(connection, "s-1", "org-1", "pending", "active"));
        assertTrue(subscriptionDao.deleteSubscriptionAtomic(connection, "s-1", "org-1", "active"));
    }

    @Test
    public void translatesJdbcFailuresAcrossWritePaths() throws Exception {
        when(statement.executeUpdate()).thenThrow(new java.sql.SQLException("expected"));
        Timestamp now = new Timestamp(1L);
        DeliveryDAOImpl deliveries = new DeliveryDAOImpl();
        WebhookDelivery webhook = new WebhookDelivery("d", "s", "e", "pending", 0, null, now, now, null);
        WebhookDeliveryAudit audit = new WebhookDeliveryAudit("a", "e", "d", "org", "500", now, now);
        PollDelivery poll = new PollDelivery("p", "s", "e", "pending", now, null);
        expectThrows(RuntimeException.class, () -> deliveries.addWebhookDelivery(connection, webhook));
        expectThrows(RuntimeException.class, () -> deliveries.updateWebhookDeliveryStatus(connection, webhook));
        expectThrows(RuntimeException.class, () -> deliveries.addWebhookDeliveryAudit(connection, audit));
        expectThrows(RuntimeException.class, () -> deliveries.addPollDelivery(connection, poll));
        expectThrows(RuntimeException.class, () -> deliveries.claimWebhookDelivery(connection, "d"));
        expectThrows(RuntimeException.class, () -> deliveries.claimStuckWebhookDelivery(connection, "d", now));
        expectThrows(RuntimeException.class, () -> deliveries.releaseWebhookDelivery(connection, "d", 1, now));
        expectThrows(RuntimeException.class, () -> deliveries.claimPollDelivery(connection, "p"));
        expectThrows(RuntimeException.class, () -> deliveries.updatePollDeliveryStatus(connection, "p", "done"));
        expectThrows(RuntimeException.class, () -> deliveries.updatePollDeliveryStatus(connection, "p", "pending", "done"));

        TopicDAOImpl topics = new TopicDAOImpl();
        expectThrows(RuntimeException.class, () -> topics.addTopic(connection,
                new Topic("t", "org", "name", "desc", "active")));
        expectThrows(RuntimeException.class, () -> topics.updateTopicStatus(connection, "t", "org", TopicStatus.ACTIVE));
        SubscriptionDAOImpl subscriptions = new SubscriptionDAOImpl();
        expectThrows(RuntimeException.class, () -> subscriptions.updateSubscriptionStatus(connection, "s", "org", "active"));
        expectThrows(RuntimeException.class, () -> subscriptions.updateSubscriptionStatus(connection, "s", "org", "pending", "active"));
        expectThrows(RuntimeException.class, () -> subscriptions.deleteSubscriptionAtomic(connection, "s", "org", "active"));
    }

    @Test
    public void coversTransactionWrappersAndGuardBranches() {
        Timestamp now = new Timestamp(1L);
        EventDAOImpl events = new EventDAOImpl();
        Event event = new Event("e", "org", "group", "topic", "{}", now);
        expectThrows(IllegalArgumentException.class, () -> events.addEvent(null, event));
        expectThrows(IllegalArgumentException.class,
                () -> events.addEventPurposes(null, "e", "org", Arrays.asList("one", " ", null)));
        events.addEventPurposes(connection, "e", "org", Collections.emptyList());

        TopicDAOImpl topics = new TopicDAOImpl();
        assertTrue(topics.addTopic(connection, new Topic("t", "org", "name", "desc", "active")));
        assertTrue(topics.updateTopicStatus(connection, "t", "org", TopicStatus.ACTIVE));
        expectThrows(IllegalArgumentException.class,
                () -> topics.getTopicByOrgAndName(null, "org", "name"));

        SubscriptionDAOImpl subscriptions = new SubscriptionDAOImpl();
        assertTrue(subscriptions.updateSubscriptionStatus(connection, "s", "org", "active"));
        assertTrue(subscriptions.updateSubscriptionStatus(connection, "s", "org", null, "active"));
        assertTrue(subscriptions.deleteSubscriptionAtomic(connection, "s", "org", "active"));
        DeliveryDAOImpl deliveries = new DeliveryDAOImpl();
        expectThrows(IllegalArgumentException.class,
                () -> deliveries.addWebhookDelivery(null,
                        new WebhookDelivery("d", "s", "e", "pending", 0, null, now, now, null)));
        assertTrue(deliveries.claimWebhookDelivery(connection, " "));
        assertTrue(deliveries.claimStuckWebhookDelivery(connection, null, now));
        org.testng.Assert.assertFalse(deliveries.releaseWebhookDelivery(connection, "", 0, now));
        deliveries.updatePollDeliveryStatusesByDeliveryIds(connection, "org", "group", "subscription",
                Collections.emptyList(), Collections.emptyMap());
        expectThrows(IllegalArgumentException.class,
                () -> deliveries.updatePollDeliveryStatusesByDeliveryIds(null, "", "group", "subscription",
                        null, null));
    }

    @Test
    public void addSubscriptionDuplicateNameConstraintThrowsSubscriptionNameAlreadyExists() throws Exception {
        when(resultSet.next()).thenReturn(false, true, false);
        when(resultSet.getString(1)).thenReturn("active");
        when(statement.executeUpdate()).thenReturn(1)
                .thenThrow(new java.sql.SQLException("Duplicate entry for key 'UQ_SUB_ORG_ACTIVE_NAME'", "23505"));

        Subscription subscription = new Subscription();
        subscription.setSubscriptionId("s-1");
        subscription.setName("sub-name");
        subscription.setOrgId("org-1");
        subscription.setGroupId("group-1");
        subscription.setTopicIds(Collections.singletonList("t-1"));
        subscription.setDeliveryMode("webhook");
        subscription.setCallbackUrl("https://example.com/hook");

        SubscriptionDAOImpl subscriptionDao = new SubscriptionDAOImpl();
        EventNotificationDuplicateResourceException ex = expectThrows(
                EventNotificationDuplicateResourceException.class,
                () -> subscriptionDao.addSubscription(connection, subscription));
        assertEquals(ex.getMessage(),
                String.format(EventNotificationCommonConstants.ERROR_SUBSCRIPTION_NAME_ALREADY_EXISTS, "sub-name"));
    }

    @Test
    public void addSubscriptionOtherIntegrityConstraintThrowsDuplicateSubscription() throws Exception {
        when(resultSet.next()).thenReturn(false, true, false);
        when(resultSet.getString(1)).thenReturn("active");
        when(statement.executeUpdate()).thenReturn(1)
                .thenThrow(new java.sql.SQLException("Duplicate entry for key 'FK_SUB_TOPIC_NAME'", "23505"));

        Subscription subscription = new Subscription();
        subscription.setSubscriptionId("s-1");
        subscription.setName("sub-name");
        subscription.setOrgId("org-1");
        subscription.setGroupId("group-1");
        subscription.setTopicIds(Collections.singletonList("t-1"));
        subscription.setDeliveryMode("webhook");
        subscription.setCallbackUrl("https://example.com/hook");

        SubscriptionDAOImpl subscriptionDao = new SubscriptionDAOImpl();
        EventNotificationDuplicateResourceException ex = expectThrows(
                EventNotificationDuplicateResourceException.class,
                () -> subscriptionDao.addSubscription(connection, subscription));
        assertEquals(ex.getMessage(),
                EventNotificationCommonConstants.ERROR_DUPLICATE_SUBSCRIPTION);
    }

    private void setManagerDataSource(Object value) throws Exception {
        Field field = JDBCPersistenceManager.class.getDeclaredField("dataSource");
        field.setAccessible(true);
        field.set(null, value);
    }
}
