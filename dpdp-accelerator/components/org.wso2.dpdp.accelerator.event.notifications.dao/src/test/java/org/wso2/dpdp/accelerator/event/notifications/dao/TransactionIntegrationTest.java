/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * Licensed under the Apache License, Version 2.0.
 */
package org.wso2.dpdp.accelerator.event.notifications.dao;

import org.h2.tools.RunScript;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.DeliveryMode;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.DeliveryStatus;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.PollStatus;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.PurposeFilterMode;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.SubscriptionStatus;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.TopicStatus;
import org.wso2.dpdp.accelerator.event.notifications.common.exception.dao.EventNotificationDuplicateResourceException;
import org.wso2.dpdp.accelerator.event.notifications.dao.impl.DeliveryDAOImpl;
import org.wso2.dpdp.accelerator.event.notifications.dao.impl.EventDAOImpl;
import org.wso2.dpdp.accelerator.event.notifications.dao.impl.SubscriptionDAOImpl;
import org.wso2.dpdp.accelerator.event.notifications.dao.impl.TopicDAOImpl;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Event;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.PollDelivery;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.PollDeliveryError;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Subscription;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Topic;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDelivery;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/** Real H2 checks for the connection-aware persistence methods. */
public class TransactionIntegrationTest {

    private Connection connection;
    private String databaseName;

    @BeforeMethod
    public void setUp() throws Exception {
        databaseName = "enf_" + System.nanoTime();
        connection = DriverManager.getConnection("jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1");
        RunScript.execute(connection, new StringReader(
                "CREATE TABLE TOPIC (TOPIC_ID VARCHAR(64) PRIMARY KEY, ORG_ID VARCHAR(128) NOT NULL, "
                        + "NAME VARCHAR(225) NOT NULL, DESCRIPTION VARCHAR(255), STATUS VARCHAR(32) NOT NULL, "
                        + "INITIATED_BY VARCHAR(32) NOT NULL, ACTIVE_NAME VARCHAR(225) GENERATED ALWAYS AS "
                        + "(CASE WHEN STATUS = 'active' THEN LOWER(NAME) ELSE NULL END));"
                        + "CREATE UNIQUE INDEX UQ_TOPIC_ORG_ACTIVE_NAME ON TOPIC(ORG_ID, ACTIVE_NAME);"
                        + "CREATE TABLE SUBSCRIPTION (SUBSCRIPTION_ID VARCHAR(64) PRIMARY KEY, ORG_ID VARCHAR(128) NOT NULL, "
                        + "NAME VARCHAR(225) NOT NULL, "
                        + "GROUP_ID VARCHAR(128) NOT NULL, PURPOSE_FILTER_MODE VARCHAR(32) NOT NULL, "
                        + "PURPOSE_SET_HASH VARCHAR(64) NOT NULL, DELIVERY_MODE VARCHAR(32) NOT NULL, CALLBACK_URL VARCHAR(512), "
                        + "SHARED_SECRET VARCHAR(512), STATUS VARCHAR(32) NOT NULL, CREATED_AT TIMESTAMP NOT NULL, UPDATED_AT TIMESTAMP NOT NULL, "
                        + "ACTIVE_NAME VARCHAR(225) GENERATED ALWAYS AS (CASE WHEN STATUS <> 'deleted' THEN LOWER(NAME) ELSE NULL END));"
                        + "CREATE UNIQUE INDEX UQ_SUB_ORG_ACTIVE_NAME ON SUBSCRIPTION(ORG_ID, ACTIVE_NAME);"
                        + "CREATE TABLE SUBSCRIPTION_TOPIC (ORG_ID VARCHAR(128), SUBSCRIPTION_ID VARCHAR(64), TOPIC_ID VARCHAR(64), PRIMARY KEY(SUBSCRIPTION_ID, TOPIC_ID));"
                        + "CREATE TABLE SUBSCRIPTION_PURPOSE (SUBSCRIPTION_ID VARCHAR(64), ORG_ID VARCHAR(128), PURPOSE_NAME VARCHAR(128), "
                        + "PRIMARY KEY(SUBSCRIPTION_ID, PURPOSE_NAME));"
                        + "CREATE TABLE POLL_DELIVERY (DELIVERY_ID VARCHAR(64) PRIMARY KEY, ORG_ID VARCHAR(128), SUBSCRIPTION_ID VARCHAR(64), "
                        + "EVENT_ID VARCHAR(64), STATUS VARCHAR(32), ERROR_CODE VARCHAR(64), "
                        + "ERROR_DETAIL VARCHAR(1024), "
                        + "CREATED_AT TIMESTAMP, COMPLETED_AT TIMESTAMP);"
                        + "CREATE TABLE EVENT (EVENT_ID VARCHAR(64) PRIMARY KEY, ORG_ID VARCHAR(128) NOT NULL, "
                        + "GROUP_ID VARCHAR(128) NOT NULL, TOPIC_ID VARCHAR(64) NOT NULL, PAYLOAD VARCHAR(4096), "
                        + "CREATED_AT TIMESTAMP NOT NULL);"
                        + "CREATE TABLE WEBHOOK_DELIVERY (DELIVERY_ID VARCHAR(64) PRIMARY KEY, ORG_ID VARCHAR(128), "
                        + "SUBSCRIPTION_ID VARCHAR(64), EVENT_ID VARCHAR(64), STATUS VARCHAR(32), "
                        + "ATTEMPT_COUNT INT, NEXT_RETRY_AT TIMESTAMP, "
                        + "CREATED_AT TIMESTAMP, UPDATED_AT TIMESTAMP, "
                        + "DELIVERED_AT TIMESTAMP);"));
    }

    @AfterMethod
    public void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    public void topicInsertCanBeRolledBackByCaller() throws Exception {
        TopicDAOImpl dao = new TopicDAOImpl();
        connection.setAutoCommit(false);
        assertTrue(dao.addTopic(connection, new Topic("topic-1", "org-1", "accounts", "", 
                TopicStatus.ACTIVE.getValue())));
        connection.rollback();

        try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM TOPIC WHERE TOPIC_ID = ?")) {
            ps.setString(1, "topic-1");
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertEquals(rs.getInt(1), 0);
            }
        }
    }

    @Test
    public void subscriptionPersistsNormalizedEnumValuesAndPurposes() throws Exception {
        TopicDAOImpl topicDAO = new TopicDAOImpl();
        topicDAO.addTopic(connection, new Topic("topic-1", "org-1", "accounts", "", TopicStatus.ACTIVE.getValue()));
        Subscription subscription = new Subscription();
        subscription.setSubscriptionId("sub-1");
        subscription.setName("sub-name");
        subscription.setOrgId("org-1");
        subscription.setGroupId("group-1");
        subscription.setTopicIds(Collections.singletonList("topic-1"));
        subscription.setPurposeFilterMode("ALL");
        subscription.setPurposes(Collections.singletonList("marketing"));
        subscription.setDeliveryMode("WEBHOOK");
        subscription.setCallbackUrl("https://example.com/callback");
        subscription.setSharedSecret("secret");
        subscription.setStatus("PENDING");
        subscription.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        subscription.setUpdatedAt(new Timestamp(System.currentTimeMillis()));

        new SubscriptionDAOImpl().addSubscription(connection, subscription);
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT PURPOSE_FILTER_MODE, DELIVERY_MODE, STATUS FROM SUBSCRIPTION WHERE SUBSCRIPTION_ID = ?")) {
            ps.setString(1, "sub-1");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(rs.getString(1), PurposeFilterMode.ALL.getValue());
                assertEquals(rs.getString(2), DeliveryMode.WEBHOOK.getValue());
                assertEquals(rs.getString(3), SubscriptionStatus.PENDING.getValue());
            }
        }
    }

    @Test
    public void pollDeliveryInsertUsesCallerTransaction() throws Exception {
        connection.setAutoCommit(false);
        PollDelivery delivery = new PollDelivery("delivery-1", "sub-1", "event-1", "pending",
                new Timestamp(System.currentTimeMillis()), null);
        assertTrue(new DeliveryDAOImpl().addPollDelivery(connection, delivery));
        connection.rollback();

        try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM POLL_DELIVERY")) {
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertEquals(rs.getInt(1), 0);
            }
        }
    }

    @Test
    public void manualRetryCanBePreparedRepeatedlyWheneverFailed() throws Exception {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        new TopicDAOImpl().addTopic(connection,
                new Topic("topic-1", "org-1", "accounts", "", TopicStatus.ACTIVE.getValue()));
        new EventDAOImpl().addEvent(connection,
                new Event("event-1", "org-1", "group-1", "topic-1", "{}", now));
        Subscription sub1 = new Subscription();
        sub1.setSubscriptionId("sub-1");
        sub1.setName("sub1-name");
        sub1.setOrgId("org-1");
        sub1.setGroupId("group-1");
        sub1.setTopicIds(Collections.singletonList("topic-1"));
        sub1.setPurposeFilterMode("all");
        sub1.setPurposes(Collections.emptyList());
        sub1.setDeliveryMode("webhook");
        sub1.setCallbackUrl("https://example.com/callback");
        sub1.setSharedSecret("secret");
        sub1.setStatus("active");
        sub1.setCreatedAt(now);
        sub1.setUpdatedAt(now);
        new SubscriptionDAOImpl().addSubscription(connection, sub1);
        DeliveryDAOImpl dao = new DeliveryDAOImpl();
        dao.addWebhookDelivery(connection, new WebhookDelivery("delivery-1", "org-1", "sub-1", "event-1", "failed", 6,
                null, now, now, null));

        assertTrue(dao.getWebhookDeliveryDispatchContext(connection, "org-1", "sub-1", "delivery-1").isPresent());
        assertTrue(dao.prepareManualRetry(connection, "org-1", "sub-1", "delivery-1", 5));
        // While in pending, a concurrent or second retry attempt is rejected
        assertFalse(dao.prepareManualRetry(connection, "org-1", "sub-1", "delivery-1", 5));

        WebhookDelivery prepared = dao.getWebhookDeliveryById(connection, "delivery-1", "org-1").get();
        assertEquals(prepared.getStatus(), DeliveryStatus.PENDING.getValue());
        assertEquals(prepared.getOrgId(), "org-1");
        assertEquals(prepared.getAttemptCount(), 6);

        // Simulate claiming and executing the retry, which fails again (status in_flight -> failed, attemptCount=7)
        assertTrue(dao.claimWebhookDelivery(connection, "delivery-1"));
        WebhookDelivery failedAgain = new WebhookDelivery("delivery-1", "org-1", "sub-1", "event-1",
                DeliveryStatus.FAILED.getValue(), 7, null, now, new Timestamp(System.currentTimeMillis()), null);
        assertTrue(dao.updateWebhookDeliveryStatus(connection, failedAgain));

        // A second manual retry can now be prepared successfully
        assertTrue(dao.prepareManualRetry(connection, "org-1", "sub-1", "delivery-1", 5));
        WebhookDelivery preparedAgain = dao.getWebhookDeliveryById(connection, "delivery-1", "org-1").get();
        assertEquals(preparedAgain.getStatus(), DeliveryStatus.PENDING.getValue());
        assertEquals(preparedAgain.getAttemptCount(), 7);
    }

    @Test
    public void duplicateTopicDoesNotCommitASecondRow() throws Exception {
        TopicDAOImpl dao = new TopicDAOImpl();
        dao.addTopic(connection, new Topic("topic-1", "org-1", "accounts", "", TopicStatus.ACTIVE.getValue()));
        try {
            dao.addTopic(connection, new Topic("topic-2", "org-1", "accounts", "", TopicStatus.ACTIVE.getValue()));
        } catch (RuntimeException expected) {
            // Duplicate detection happens before insertion; the original row remains the only row.
        }
        try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM TOPIC")) {
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertEquals(rs.getInt(1), 1);
            }
        }
    }

    @Test
    public void topicCanBeUpdatedAndDeletedWithoutSubscriptions() throws Exception {
        TopicDAOImpl dao = new TopicDAOImpl();
        assertTrue(dao.addTopic(connection,
                new Topic("topic-1", "org-1", "accounts", "", TopicStatus.ACTIVE.getValue())));
        assertTrue(dao.updateTopicStatus(connection, "topic-1", "org-1", TopicStatus.ACTIVE));
        assertTrue(dao.deleteTopicAtomic(connection, "topic-1", "org-1"));
        try (PreparedStatement ps = connection.prepareStatement("SELECT STATUS FROM TOPIC WHERE TOPIC_ID = ?")) {
            ps.setString(1, "topic-1");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(rs.getString(1), TopicStatus.DELETED.getValue());
            }
        }
    }

    @Test
    public void deletedTopicNameCanBeRecreatedWithANewIdRepeatedly() throws Exception {
        TopicDAOImpl dao = new TopicDAOImpl();
        String previousTopicId = null;

        for (int cycle = 1; cycle <= 3; cycle++) {
            String topicId = "topic-" + cycle;
            assertNotEquals(topicId, previousTopicId);
            assertTrue(dao.addTopic(connection,
                    new Topic(topicId, "org-1", cycle % 2 == 0 ? "ACCOUNTS" : "accounts", "",
                            TopicStatus.ACTIVE.getValue())));
            assertTrue(dao.deleteTopicAtomic(connection, topicId, "org-1"));
            assertFalse(dao.deleteTopicAtomic(connection, topicId, "org-1"),
                    "A deleted topic must not transition again");
            previousTopicId = topicId;
        }

        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*), COUNT(DISTINCT TOPIC_ID) FROM TOPIC WHERE ORG_ID = ? AND LOWER(NAME) = LOWER(?)")) {
            ps.setString(1, "org-1");
            ps.setString(2, "accounts");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(rs.getInt(1), 3);
                assertEquals(rs.getInt(2), 3);
            }
        }
    }

    @Test
    public void activeTopicNameIsUniqueIgnoringCase() throws Exception {
        TopicDAOImpl dao = new TopicDAOImpl();
        assertTrue(dao.addTopic(connection,
                new Topic("topic-1", "org-1", "Accounts", "", TopicStatus.ACTIVE.getValue())));

        expectThrows(EventNotificationDuplicateResourceException.class,
                () -> dao.addTopic(connection,
                        new Topic("topic-2", "org-1", "accounts", "", TopicStatus.ACTIVE.getValue())));

        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM TOPIC WHERE ORG_ID = ? AND STATUS = ?")) {
            ps.setString(1, "org-1");
            ps.setString(2, TopicStatus.ACTIVE.getValue());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(rs.getInt(1), 1);
            }
        }
    }

    @Test(timeOut = 10000)
    public void concurrentReversedTopicSetsAllowOnlyOneSubscription() throws Exception {
        TopicDAOImpl topics = new TopicDAOImpl();
        topics.addTopic(connection, new Topic("topic-a", "org-1", "a", "", TopicStatus.ACTIVE.getValue()));
        topics.addTopic(connection, new Topic("topic-b", "org-1", "b", "", TopicStatus.ACTIVE.getValue()));
        connection.commit();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> createMultiTopicWhenReady(start, "sub-a", false));
            Future<Boolean> second = executor.submit(() -> createMultiTopicWhenReady(start, "sub-b", true));
            start.countDown();
            assertTrue(first.get(5, TimeUnit.SECONDS) ^ second.get(5, TimeUnit.SECONDS));
            try (Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM SUBSCRIPTION_TOPIC")) {
                assertTrue(rows.next());
                assertEquals(rows.getInt(1), 2, "Only the winning subscription's associations must commit");
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean createMultiTopicWhenReady(CountDownLatch start, String id, boolean reverse) throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1")) {
            conn.setAutoCommit(false);
            start.await();
            Timestamp now = new Timestamp(System.currentTimeMillis());
            Subscription subscription = new Subscription();
            subscription.setSubscriptionId(id);
            subscription.setName(id + "-name");
            subscription.setOrgId("org-1");
            subscription.setGroupId("group-1");
            subscription.setTopicIds(reverse ? java.util.Arrays.asList("topic-b", "topic-a")
                    : java.util.Arrays.asList("topic-a", "topic-b"));
            subscription.setPurposeFilterMode("ALL");
            subscription.setPurposes(Collections.emptyList());
            subscription.setDeliveryMode("POLL");
            subscription.setStatus("active");
            subscription.setCreatedAt(now);
            subscription.setUpdatedAt(now);
            try {
                new SubscriptionDAOImpl().addSubscription(conn, subscription);
                conn.commit();
                return true;
            } catch (EventNotificationDuplicateResourceException expected) {
                conn.rollback();
                return false;
            }
        }
    }

    @Test(timeOut = 10000)
    public void concurrentCaseInsensitiveTopicCreationAllowsOneActiveRow() throws Exception {
        try (Connection first = DriverManager.getConnection("jdbc:h2:mem:" + databaseName
                + ";DB_CLOSE_DELAY=-1");
                Connection second = DriverManager.getConnection("jdbc:h2:mem:" + databaseName
                        + ";DB_CLOSE_DELAY=-1")) {
            first.setAutoCommit(false);
            second.setAutoCommit(false);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<Boolean> firstCreate = executor.submit(
                        () -> createTopicWhenReady(first, start, "topic-1", "Accounts"));
                Future<Boolean> secondCreate = executor.submit(
                        () -> createTopicWhenReady(second, start, "topic-2", "accounts"));
                start.countDown();
                assertTrue(firstCreate.get() ^ secondCreate.get(),
                        "Exactly one concurrent request must create the active topic");
            } finally {
                executor.shutdownNow();
            }
        }

        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM TOPIC WHERE ORG_ID = ? AND STATUS = ?")) {
            ps.setString(1, "org-1");
            ps.setString(2, TopicStatus.ACTIVE.getValue());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(rs.getInt(1), 1);
            }
        }
    }

    @Test(timeOut = 10000)
    public void pollDeliveryClaimIsSerializedAcrossConnections() throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO POLL_DELIVERY (DELIVERY_ID, SUBSCRIPTION_ID, EVENT_ID, STATUS, CREATED_AT) "
                        + "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)")) {
            ps.setString(1, "delivery-1");
            ps.setString(2, "sub-1");
            ps.setString(3, "event-1");
            ps.setString(4, "pending");
            ps.executeUpdate();
        }

        try (Connection first = DriverManager.getConnection("jdbc:h2:mem:" + databaseName
                + ";DB_CLOSE_DELAY=-1");
                Connection second = DriverManager.getConnection("jdbc:h2:mem:" + databaseName
                        + ";DB_CLOSE_DELAY=-1")) {
            first.setAutoCommit(false);
            second.setAutoCommit(false);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            Future<Boolean> firstClaim = executor.submit(() -> claimWhenReady(first, start));
            Future<Boolean> secondClaim = executor.submit(() -> claimWhenReady(second, start));
            start.countDown();
            boolean oneClaimed = firstClaim.get();
            boolean twoClaimed = secondClaim.get();
            executor.shutdownNow();
            assertTrue(oneClaimed ^ twoClaimed,
                    "Exactly one concurrent claimant must transition the pending delivery");
        }
    }

    @Test(timeOut = 10000)
    public void topicDeregistrationWaitsForEventPublicationTransaction() throws Exception {
        TopicDAOImpl topicDAO = new TopicDAOImpl();
        EventDAOImpl eventDAO = new EventDAOImpl();
        assertTrue(topicDAO.addTopic(connection,
                new Topic("topic-1", "org-1", "accounts", "", TopicStatus.ACTIVE.getValue())));

        try (Connection publisher = newConnection(); Connection deregister = newConnection()) {
            publisher.setAutoCommit(false);
            deregister.setAutoCommit(false);
            assertTrue(topicDAO.getActiveTopicByOrgAndNameForUpdate(publisher, "org-1", "accounts").isPresent());

            ExecutorService executor = Executors.newSingleThreadExecutor();
            CountDownLatch attempted = new CountDownLatch(1);
            try {
                Future<Boolean> deregistration = executor.submit(() -> {
                    attempted.countDown();
                    boolean result = topicDAO.deregisterTopicAtomic(deregister, "topic-1", "org-1");
                    deregister.commit();
                    return result;
                });
                attempted.await();
                expectThrows(TimeoutException.class,
                        () -> deregistration.get(200, TimeUnit.MILLISECONDS));

                Event event = new Event("event-1", "org-1", "group-1", "topic-1", "{}",
                        new Timestamp(System.currentTimeMillis()));
                assertTrue(eventDAO.addEvent(publisher, event));
                publisher.commit();
                assertTrue(deregistration.get(5, TimeUnit.SECONDS));
            } finally {
                executor.shutdownNow();
            }
        }

        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM EVENT WHERE EVENT_ID = ? AND ORG_ID = ?")) {
            ps.setString(1, "event-1");
            ps.setString(2, "org-1");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(rs.getInt(1), 1);
            }
        }
    }

    @Test(timeOut = 10000)
    public void subscriptionDeletionCannotOvertakeFanOutDeliveryInsert() throws Exception {
        TopicDAOImpl topicDAO = new TopicDAOImpl();
        SubscriptionDAOImpl subscriptionDAO = new SubscriptionDAOImpl();
        DeliveryDAOImpl deliveryDAO = new DeliveryDAOImpl();
        EventDAOImpl eventDAO = new EventDAOImpl();
        assertTrue(topicDAO.addTopic(connection,
                new Topic("topic-1", "org-1", "accounts", "", TopicStatus.ACTIVE.getValue())));
        Timestamp now = new Timestamp(System.currentTimeMillis());
        Subscription subFanOut = new Subscription();
        subFanOut.setSubscriptionId("sub-1");
        subFanOut.setName("sub-fan-out");
        subFanOut.setOrgId("org-1");
        subFanOut.setGroupId("group-1");
        subFanOut.setTopicIds(Collections.singletonList("topic-1"));
        subFanOut.setPurposeFilterMode(PurposeFilterMode.ALL.getValue());
        subFanOut.setPurposes(Collections.emptyList());
        subFanOut.setDeliveryMode(DeliveryMode.WEBHOOK.getValue());
        subFanOut.setCallbackUrl("https://example.com/callback");
        subFanOut.setSharedSecret("secret");
        subFanOut.setStatus(SubscriptionStatus.ACTIVE.getValue());
        subFanOut.setCreatedAt(now);
        subFanOut.setUpdatedAt(now);
        subscriptionDAO.addSubscription(connection, subFanOut);

        try (Connection fanOut = newConnection(); Connection delete = newConnection()) {
            fanOut.setAutoCommit(false);
            delete.setAutoCommit(false);
            assertEquals(subscriptionDAO.getActiveSubscriptionsForFanOut(fanOut, "org-1", "topic-1").size(), 1);

            ExecutorService executor = Executors.newSingleThreadExecutor();
            CountDownLatch attempted = new CountDownLatch(1);
            try {
                Future<Boolean> deletion = executor.submit(() -> {
                    attempted.countDown();
                    boolean result = subscriptionDAO.deleteSubscriptionAtomic(delete, "sub-1", "org-1",
                            SubscriptionStatus.ACTIVE.getValue());
                    delete.commit();
                    return result;
                });
                attempted.await();
                expectThrows(TimeoutException.class, () -> deletion.get(200, TimeUnit.MILLISECONDS));

                assertTrue(eventDAO.addEvent(fanOut,
                        new Event("event-1", "org-1", "group-1", "topic-1", "{}", now)));
                assertTrue(deliveryDAO.addWebhookDelivery(fanOut,
                        new WebhookDelivery("delivery-1", "org-1", "sub-1", "event-1",
                                DeliveryStatus.PENDING.getValue(), 0, null, now, now, null)));
                fanOut.commit();
                assertFalse(deletion.get(5, TimeUnit.SECONDS),
                        "Deletion must see the pending delivery inserted by fan-out");
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    public void eventInsertRejectsTopicThatIsNoLongerActive() throws Exception {
        TopicDAOImpl topicDAO = new TopicDAOImpl();
        assertTrue(topicDAO.addTopic(connection,
                new Topic("topic-1", "org-1", "accounts", "", TopicStatus.ACTIVE.getValue())));
        assertTrue(topicDAO.deregisterTopicAtomic(connection, "topic-1", "org-1"));

        Event event = new Event("event-1", "org-1", "group-1", "topic-1", "{}",
                new Timestamp(System.currentTimeMillis()));
        assertFalse(new EventDAOImpl().addEvent(connection, event));
    }

    @Test(timeOut = 10000)
    public void pendingVerificationRowCanBeOwnedByOnlyOneTransaction() throws Exception {
        TopicDAOImpl topicDAO = new TopicDAOImpl();
        SubscriptionDAOImpl subscriptionDAO = new SubscriptionDAOImpl();
        assertTrue(topicDAO.addTopic(connection,
                new Topic("topic-1", "org-1", "accounts", "", TopicStatus.ACTIVE.getValue())));
        Timestamp now = new Timestamp(System.currentTimeMillis());
        Subscription subVerify = new Subscription();
        subVerify.setSubscriptionId("sub-1");
        subVerify.setName("sub-verify");
        subVerify.setOrgId("org-1");
        subVerify.setGroupId("group-1");
        subVerify.setTopicIds(Collections.singletonList("topic-1"));
        subVerify.setPurposeFilterMode(PurposeFilterMode.ALL.getValue());
        subVerify.setPurposes(Collections.emptyList());
        subVerify.setDeliveryMode(DeliveryMode.WEBHOOK.getValue());
        subVerify.setCallbackUrl("https://example.com/callback");
        subVerify.setSharedSecret("secret");
        subVerify.setStatus(SubscriptionStatus.PENDING.getValue());
        subVerify.setCreatedAt(now);
        subVerify.setUpdatedAt(now);
        subscriptionDAO.addSubscription(connection, subVerify);
        try (Connection first = newConnection(); Connection second = newConnection()) {
            first.setAutoCommit(false);
            second.setAutoCommit(false);
            CountDownLatch firstLocked = new CountDownLatch(1);
            CountDownLatch secondStarted = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<Boolean> firstClaim = executor.submit(
                        () -> holdVerificationLock(first, firstLocked, releaseFirst));
                Future<Boolean> secondClaim = executor.submit(
                        () -> claimVerificationAfterFirstLock(second, firstLocked, secondStarted));
                assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
                releaseFirst.countDown();
                assertTrue(firstClaim.get());
                assertFalse(secondClaim.get(),
                        "The waiting transaction must observe the ACTIVE state and skip verification");
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test(timeOut = 10000)
    public void concurrentPollErrorCannotOverwriteAcknowledgement() throws Exception {
        TopicDAOImpl topicDAO = new TopicDAOImpl();
        SubscriptionDAOImpl subscriptionDAO = new SubscriptionDAOImpl();
        DeliveryDAOImpl deliveryDAO = new DeliveryDAOImpl();
        Timestamp now = new Timestamp(System.currentTimeMillis());
        assertTrue(topicDAO.addTopic(connection,
                new Topic("topic-1", "org-1", "accounts", "", TopicStatus.ACTIVE.getValue())));
        Subscription subPoll = new Subscription();
        subPoll.setSubscriptionId("sub-1");
        subPoll.setName("sub-poll");
        subPoll.setOrgId("org-1");
        subPoll.setGroupId("group-1");
        subPoll.setTopicIds(Collections.singletonList("topic-1"));
        subPoll.setPurposeFilterMode(PurposeFilterMode.ALL.getValue());
        subPoll.setPurposes(Collections.emptyList());
        subPoll.setDeliveryMode(DeliveryMode.POLL.getValue());
        subPoll.setStatus(SubscriptionStatus.ACTIVE.getValue());
        subPoll.setCreatedAt(now);
        subPoll.setUpdatedAt(now);
        subscriptionDAO.addSubscription(connection, subPoll);
        assertTrue(deliveryDAO.addPollDelivery(connection,
                new PollDelivery("delivery-1", "org-1", "sub-1", "event-1", PollStatus.PENDING.getValue(),
                        null, null, now, null)));

        try (Connection first = newConnection(); Connection second = newConnection()) {
            first.setAutoCommit(false);
            second.setAutoCommit(false);
            CountDownLatch acknowledgementUpdated = new CountDownLatch(1);
            CountDownLatch errorStarted = new CountDownLatch(1);
            CountDownLatch releaseAcknowledgement = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<Void> acknowledgement = executor.submit(() -> {
                    deliveryDAO.updatePollDeliveryStatusesByDeliveryIds(first, "org-1", "group-1", "sub-1",
                            Collections.singletonList("delivery-1"), Collections.emptyMap());
                    acknowledgementUpdated.countDown();
                    releaseAcknowledgement.await();
                    first.commit();
                    return null;
                });
                Future<Void> error = executor.submit(() -> {
                    acknowledgementUpdated.await();
                    errorStarted.countDown();
                    deliveryDAO.updatePollDeliveryStatusesByDeliveryIds(second, "org-1", "group-1", "sub-1",
                            Collections.emptyList(), Collections.singletonMap("delivery-1",
                                    new PollDeliveryError("processing_failed", "Unable to process event")));
                    second.commit();
                    return null;
                });
                assertTrue(errorStarted.await(5, TimeUnit.SECONDS));
                expectThrows(TimeoutException.class, () -> error.get(200, TimeUnit.MILLISECONDS));
                releaseAcknowledgement.countDown();
                acknowledgement.get(5, TimeUnit.SECONDS);
                error.get(5, TimeUnit.SECONDS);
            } finally {
                releaseAcknowledgement.countDown();
                executor.shutdownNow();
            }
        }

        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT STATUS FROM POLL_DELIVERY WHERE DELIVERY_ID = ?")) {
            ps.setString(1, "delivery-1");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(rs.getString(1), PollStatus.ACKNOWLEDGED.getValue());
            }
        }
    }

    private Connection newConnection() throws Exception {
        return DriverManager.getConnection("jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1");
    }

    private boolean claimWhenReady(Connection conn, CountDownLatch start) throws Exception {
        start.await();
        boolean claimed = new DeliveryDAOImpl().claimPollDelivery(conn, "delivery-1");
        conn.commit();
        return claimed;
    }

    private boolean holdVerificationLock(Connection conn, CountDownLatch firstLocked, CountDownLatch releaseFirst)
            throws Exception {
        SubscriptionDAOImpl dao = new SubscriptionDAOImpl();
        boolean claimed = dao.lockSubscriptionForVerification(conn, "sub-1", "org-1",
                SubscriptionStatus.PENDING.getValue()).isPresent();
        firstLocked.countDown();
        releaseFirst.await();
        dao.updateSubscriptionStatus(conn, "sub-1", "org-1", SubscriptionStatus.PENDING.getValue(),
                SubscriptionStatus.ACTIVE.getValue());
        conn.commit();
        return claimed;
    }

    private boolean claimVerificationAfterFirstLock(Connection conn, CountDownLatch firstLocked,
            CountDownLatch secondStarted) throws Exception {
        firstLocked.await();
        secondStarted.countDown();
        boolean claimed = new SubscriptionDAOImpl().lockSubscriptionForVerification(conn, "sub-1", "org-1",
                SubscriptionStatus.PENDING.getValue()).isPresent();
        conn.commit();
        return claimed;
    }

    private boolean createTopicWhenReady(Connection conn, CountDownLatch start, String topicId, String topicName)
            throws Exception {
        start.await();
        try {
            boolean created = new TopicDAOImpl().addTopic(conn,
                    new Topic(topicId, "org-1", topicName, "", TopicStatus.ACTIVE.getValue()));
            conn.commit();
            return created;
        } catch (EventNotificationDuplicateResourceException e) {
            conn.rollback();
            return false;
        }
    }

}
