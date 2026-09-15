/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * Licensed under the Apache License, Version 2.0.
 */
package org.wso2.dpdp.accelerator.event.notifications.dao;

import org.h2.tools.RunScript;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.DeliveryMode;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.DeliveryStatus;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.PurposeFilterMode;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.PollStatus;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.SubscriptionStatus;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.TopicStatus;
import org.wso2.dpdp.accelerator.event.notifications.common.exception.EventNotificationDuplicateResourceException;
import org.wso2.dpdp.accelerator.event.notifications.dao.impl.DeliveryDAOImpl;
import org.wso2.dpdp.accelerator.event.notifications.dao.impl.EventDAOImpl;
import org.wso2.dpdp.accelerator.event.notifications.dao.impl.SubscriptionDAOImpl;
import org.wso2.dpdp.accelerator.event.notifications.dao.impl.TopicDAOImpl;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.PollDelivery;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.PollDeliveryError;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Event;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Subscription;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Topic;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDelivery;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
                        + "GROUP_ID VARCHAR(128) NOT NULL, TOPIC_ID VARCHAR(64) NOT NULL, PURPOSE_FILTER_MODE VARCHAR(32) NOT NULL, "
                        + "PURPOSE_SET_HASH VARCHAR(64) NOT NULL, DELIVERY_MODE VARCHAR(32) NOT NULL, CALLBACK_URL VARCHAR(512), "
                        + "SHARED_SECRET VARCHAR(512), STATUS VARCHAR(32) NOT NULL, CREATED_AT TIMESTAMP NOT NULL, UPDATED_AT TIMESTAMP NOT NULL);"
                        + "CREATE TABLE SUBSCRIPTION_PURPOSE (SUBSCRIPTION_ID VARCHAR(64), PURPOSE_NAME VARCHAR(128), "
                        + "PRIMARY KEY(SUBSCRIPTION_ID, PURPOSE_NAME));"
                        + "CREATE TABLE POLL_DELIVERY (DELIVERY_ID VARCHAR(64) PRIMARY KEY, SUBSCRIPTION_ID VARCHAR(64), "
                        + "EVENT_ID VARCHAR(64), STATUS VARCHAR(32), ERROR_CODE VARCHAR(64), "
                        + "ERROR_DETAIL VARCHAR(1024), "
                        + "CREATED_AT TIMESTAMP, COMPLETED_AT TIMESTAMP);"
                        + "CREATE TABLE EVENT (EVENT_ID VARCHAR(64) PRIMARY KEY, ORG_ID VARCHAR(128) NOT NULL, "
                        + "GROUP_ID VARCHAR(128) NOT NULL, TOPIC_ID VARCHAR(64) NOT NULL, PAYLOAD VARCHAR(4096), "
                        + "CREATED_AT TIMESTAMP NOT NULL);"
                        + "CREATE TABLE WEBHOOK_DELIVERY (DELIVERY_ID VARCHAR(64) PRIMARY KEY, "
                        + "SUBSCRIPTION_ID VARCHAR(64), EVENT_ID VARCHAR(64), STATUS VARCHAR(32), "
                        + "ATTEMPT_COUNT INT, MANUAL_RETRY_USED BOOLEAN DEFAULT FALSE, NEXT_RETRY_AT TIMESTAMP, "
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
        Subscription subscription = new Subscription("sub-1", "org-1", "group-1", "topic-1", "ALL",
                Collections.singletonList("marketing"), "WEBHOOK", "https://example.com/callback", "secret",
                "PENDING", new Timestamp(System.currentTimeMillis()), new Timestamp(System.currentTimeMillis()));

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
    public void manualRetryCanBePreparedOnlyOnceAfterAutomaticRetriesAreExhausted() throws Exception {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        new TopicDAOImpl().addTopic(connection,
                new Topic("topic-1", "org-1", "accounts", "", TopicStatus.ACTIVE.getValue()));
        new EventDAOImpl().addEvent(connection,
                new Event("event-1", "org-1", "group-1", "topic-1", "{}", now));
        new SubscriptionDAOImpl().addSubscription(connection,
                new Subscription("sub-1", "org-1", "group-1", "topic-1", "all",
                        Collections.emptyList(), "webhook", "https://example.com/callback", "secret",
                        "active", now, now));
        DeliveryDAOImpl dao = new DeliveryDAOImpl();
        dao.addWebhookDelivery(connection, new WebhookDelivery("delivery-1", "sub-1", "event-1", "failed", 6,
                null, now, now, null));

        assertTrue(dao.getWebhookDeliveryDispatchContext(connection, "org-1", "sub-1", "delivery-1").isPresent());
        assertTrue(dao.prepareManualRetry(connection, "org-1", "sub-1", "delivery-1", 5));
        assertFalse(dao.prepareManualRetry(connection, "org-1", "sub-1", "delivery-1", 5));

        WebhookDelivery prepared = dao.getWebhookDeliveryById(connection, "delivery-1", "org-1").get();
        assertEquals(prepared.getStatus(), DeliveryStatus.PENDING.getValue());
        assertEquals(prepared.getAttemptCount(), 6);
        assertTrue(prepared.isManualRetryUsed());
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
    public void topicCanBeUpdatedAndDeregisteredWithoutSubscriptions() throws Exception {
        TopicDAOImpl dao = new TopicDAOImpl();
        assertTrue(dao.addTopic(connection,
                new Topic("topic-1", "org-1", "accounts", "", TopicStatus.ACTIVE.getValue())));
        assertTrue(dao.updateTopicStatus(connection, "topic-1", "org-1", TopicStatus.ACTIVE));
        assertTrue(dao.deregisterTopicAtomic(connection, "topic-1", "org-1"));
        try (PreparedStatement ps = connection.prepareStatement("SELECT STATUS FROM TOPIC WHERE TOPIC_ID = ?")) {
            ps.setString(1, "topic-1");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(rs.getString(1), TopicStatus.DEREGISTERED.getValue());
            }
        }
    }

    @Test
    public void deregisteredTopicNameCanBeRecreatedWithANewIdRepeatedly() throws Exception {
        TopicDAOImpl dao = new TopicDAOImpl();
        String previousTopicId = null;

        for (int cycle = 1; cycle <= 3; cycle++) {
            String topicId = "topic-" + cycle;
            assertNotEquals(topicId, previousTopicId);
            assertTrue(dao.addTopic(connection,
                    new Topic(topicId, "org-1", cycle % 2 == 0 ? "ACCOUNTS" : "accounts", "",
                            TopicStatus.ACTIVE.getValue())));
            assertTrue(dao.deregisterTopicAtomic(connection, topicId, "org-1"));
            assertFalse(dao.deregisterTopicAtomic(connection, topicId, "org-1"),
                    "A deregistered topic must not transition again");
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
        subscriptionDAO.addSubscription(connection,
                new Subscription("sub-1", "org-1", "group-1", "topic-1", PurposeFilterMode.ALL.getValue(),
                        Collections.emptyList(), DeliveryMode.WEBHOOK.getValue(), "https://example.com/callback",
                        "secret", SubscriptionStatus.ACTIVE.getValue(), now, now));

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
                        new WebhookDelivery("delivery-1", "sub-1", "event-1",
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
        subscriptionDAO.addSubscription(connection,
                new Subscription("sub-1", "org-1", "group-1", "topic-1", PurposeFilterMode.ALL.getValue(),
                        Collections.emptyList(), DeliveryMode.WEBHOOK.getValue(), "https://example.com/callback",
                        "secret", SubscriptionStatus.PENDING.getValue(), now, now));
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
        subscriptionDAO.addSubscription(connection,
                new Subscription("sub-1", "org-1", "group-1", "topic-1", PurposeFilterMode.ALL.getValue(),
                        Collections.emptyList(), DeliveryMode.POLL.getValue(), null, null,
                        SubscriptionStatus.ACTIVE.getValue(), now, now));
        assertTrue(deliveryDAO.addPollDelivery(connection,
                new PollDelivery("delivery-1", "sub-1", "event-1", PollStatus.PENDING.getValue(), now, null)));

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
