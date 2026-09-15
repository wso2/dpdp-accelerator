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

package org.wso2.dpdp.accelerator.event.notifications.dao;

import org.testng.SkipException;
import org.testng.annotations.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.DeliveryMode;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.DeliveryStatus;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.PurposeFilterMode;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.SubscriptionStatus;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.TopicStatus;
import org.wso2.dpdp.accelerator.event.notifications.dao.impl.DeliveryDAOImpl;
import org.wso2.dpdp.accelerator.event.notifications.dao.impl.EventDAOImpl;
import org.wso2.dpdp.accelerator.event.notifications.dao.impl.SubscriptionDAOImpl;
import org.wso2.dpdp.accelerator.event.notifications.dao.impl.TopicDAOImpl;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Event;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Subscription;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Topic;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDelivery;
import org.wso2.dpdp.accelerator.event.notifications.dao.queries.EventNotificationMysqlDBQueries;
import org.wso2.dpdp.accelerator.event.notifications.dao.queries.EventNotificationPostgresDBQueries;
import org.wso2.dpdp.accelerator.event.notifications.dao.queries.EventNotificationQueryFactory;
import org.wso2.dpdp.accelerator.event.notifications.dao.queries.EventNotificationSqliteDBQueries;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
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
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Runs the publication/deletion locking protocol against each supported database
 * engine. Container tests are skipped only when Docker is unavailable; SQLite is
 * always exercised in-process.
 */
public class DatabaseDialectConcurrencyIntegrationTest {

    private static final int BLOCK_ASSERTION_MILLIS = 250;

    @Test(timeOut = 30000)
    public void sqliteSerializesPublicationAndDeletion() throws Exception {
        Path database = Files.createTempFile("dpdp-enf-sqlite-", ".db");
        String jdbcUrl = "jdbc:sqlite:" + database.toAbsolutePath();
        try {
            runConcurrencyScenarios(() -> openConnection(jdbcUrl, null, null, "sqlite"), "TEXT",
                    EventNotificationSqliteDBQueries.class);
        } finally {
            Files.deleteIfExists(database);
        }
    }

    @Test(timeOut = 120000)
    public void mysqlSerializesPublicationAndDeletion() throws Exception {
        requireDocker();
        try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.36")) {
            mysql.start();
            runConcurrencyScenarios(
                    () -> openConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword(), "mysql"),
                    "JSON", EventNotificationMysqlDBQueries.class);
        }
    }

    @Test(timeOut = 120000)
    public void postgresSerializesPublicationAndDeletion() throws Exception {
        requireDocker();
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.2-alpine")) {
            postgres.start();
            runConcurrencyScenarios(() -> openConnection(postgres.getJdbcUrl(), postgres.getUsername(),
                    postgres.getPassword(), "postgres"), "JSONB", EventNotificationPostgresDBQueries.class);
        }
    }

    private void runConcurrencyScenarios(ConnectionFactory connectionFactory, String payloadType,
            Class<?> expectedQueryProvider) throws Exception {
        initializeSchema(connectionFactory, payloadType);
        TopicDAOImpl topicDAO = new TopicDAOImpl();
        SubscriptionDAOImpl subscriptionDAO = new SubscriptionDAOImpl();
        EventDAOImpl eventDAO = new EventDAOImpl();
        DeliveryDAOImpl deliveryDAO = new DeliveryDAOImpl();
        Timestamp now = new Timestamp(System.currentTimeMillis());

        try (Connection seed = connectionFactory.open()) {
            assertEquals(EventNotificationQueryFactory.getQueryProvider(seed).getClass(), expectedQueryProvider);
            assertTrue(topicDAO.addTopic(seed,
                    new Topic("topic-fanout", "org-1", "accounts", "", TopicStatus.ACTIVE.getValue())));
            assertTrue(topicDAO.addTopic(seed,
                    new Topic("topic-publish", "org-1", "payments", "", TopicStatus.ACTIVE.getValue())));
            subscriptionDAO.addSubscription(seed,
                    new Subscription("sub-1", "org-1", "group-1", "topic-fanout",
                            PurposeFilterMode.ALL.getValue(), Collections.emptyList(),
                            DeliveryMode.WEBHOOK.getValue(), "https://example.com/callback", "secret",
                            SubscriptionStatus.ACTIVE.getValue(), now, now));
        }

        verifySubscriptionDeletionWaitsForFanOut(connectionFactory, subscriptionDAO, eventDAO, deliveryDAO, now);
        verifyTopicDeregistrationWaitsForPublication(connectionFactory, topicDAO, eventDAO, now);
    }

    private void verifySubscriptionDeletionWaitsForFanOut(ConnectionFactory connectionFactory,
            SubscriptionDAOImpl subscriptionDAO, EventDAOImpl eventDAO, DeliveryDAOImpl deliveryDAO,
            Timestamp now) throws Exception {
        try (Connection fanOut = connectionFactory.open(); Connection delete = connectionFactory.open()) {
            fanOut.setAutoCommit(false);
            delete.setAutoCommit(false);
            assertTrue(eventDAO.addEvent(fanOut,
                    new Event("event-fanout", "org-1", "group-1", "topic-fanout", "{}", now)));
            assertEquals(subscriptionDAO.getActiveSubscriptionsForFanOut(
                    fanOut, "org-1", "topic-fanout").size(), 1);

            ExecutorService executor = Executors.newSingleThreadExecutor();
            CountDownLatch attempted = new CountDownLatch(1);
            try {
                Future<Boolean> deletion = executor.submit(() -> {
                    attempted.countDown();
                    boolean deleted = subscriptionDAO.deleteSubscriptionAtomic(delete, "sub-1", "org-1",
                            SubscriptionStatus.ACTIVE.getValue());
                    delete.commit();
                    return deleted;
                });
                attempted.await();
                expectThrows(TimeoutException.class,
                        () -> deletion.get(BLOCK_ASSERTION_MILLIS, TimeUnit.MILLISECONDS));

                assertTrue(deliveryDAO.addWebhookDelivery(fanOut,
                        new WebhookDelivery("delivery-1", "sub-1", "event-fanout",
                                DeliveryStatus.PENDING.getValue(), 0, null, now, now, null)));
                fanOut.commit();
                assertFalse(deletion.get(5, TimeUnit.SECONDS));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    private void verifyTopicDeregistrationWaitsForPublication(ConnectionFactory connectionFactory,
            TopicDAOImpl topicDAO, EventDAOImpl eventDAO, Timestamp now) throws Exception {
        try (Connection publisher = connectionFactory.open(); Connection deregister = connectionFactory.open()) {
            publisher.setAutoCommit(false);
            deregister.setAutoCommit(false);
            assertTrue(topicDAO.getActiveTopicByOrgAndNameForUpdate(
                    publisher, "org-1", "payments").isPresent());
            assertTrue(eventDAO.addEvent(publisher,
                    new Event("event-publish", "org-1", "group-1", "topic-publish", "{}", now)));

            ExecutorService executor = Executors.newSingleThreadExecutor();
            CountDownLatch attempted = new CountDownLatch(1);
            try {
                Future<Boolean> deregistration = executor.submit(() -> {
                    attempted.countDown();
                    boolean deregistered = topicDAO.deregisterTopicAtomic(
                            deregister, "topic-publish", "org-1");
                    deregister.commit();
                    return deregistered;
                });
                attempted.await();
                expectThrows(TimeoutException.class,
                        () -> deregistration.get(BLOCK_ASSERTION_MILLIS, TimeUnit.MILLISECONDS));

                publisher.commit();
                assertTrue(deregistration.get(5, TimeUnit.SECONDS));
            } finally {
                executor.shutdownNow();
            }
        }

        try (Connection verify = connectionFactory.open(); Statement statement = verify.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT COUNT(*) FROM EVENT WHERE EVENT_ID = 'event-publish'")) {
            assertTrue(resultSet.next());
            assertEquals(resultSet.getInt(1), 1);
        }
    }

    private void initializeSchema(ConnectionFactory connectionFactory, String payloadType) throws Exception {
        try (Connection connection = connectionFactory.open(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE TOPIC (TOPIC_ID VARCHAR(64) PRIMARY KEY, ORG_ID VARCHAR(128) NOT NULL, " +
                    "NAME VARCHAR(225) NOT NULL, DESCRIPTION VARCHAR(255), STATUS VARCHAR(32) NOT NULL, " +
                    "INITIATED_BY VARCHAR(32) NOT NULL)");
            statement.execute("CREATE TABLE EVENT (EVENT_ID VARCHAR(64) PRIMARY KEY, ORG_ID VARCHAR(128) NOT NULL, " +
                    "GROUP_ID VARCHAR(128) NOT NULL, TOPIC_ID VARCHAR(64) NOT NULL, PAYLOAD " + payloadType +
                    " NOT NULL, CREATED_AT TIMESTAMP NOT NULL)");
            statement.execute("CREATE TABLE SUBSCRIPTION (SUBSCRIPTION_ID VARCHAR(64) PRIMARY KEY, " +
                    "ORG_ID VARCHAR(128) NOT NULL, GROUP_ID VARCHAR(128) NOT NULL, TOPIC_ID VARCHAR(64) NOT NULL, " +
                    "PURPOSE_FILTER_MODE VARCHAR(32) NOT NULL, PURPOSE_SET_HASH VARCHAR(64) NOT NULL, " +
                    "DELIVERY_MODE VARCHAR(32) NOT NULL, CALLBACK_URL VARCHAR(512), SHARED_SECRET VARCHAR(512), " +
                    "STATUS VARCHAR(32) NOT NULL, CREATED_AT TIMESTAMP NOT NULL, UPDATED_AT TIMESTAMP NOT NULL)");
            statement.execute("CREATE TABLE SUBSCRIPTION_PURPOSE (SUBSCRIPTION_ID VARCHAR(64) NOT NULL, " +
                    "PURPOSE_NAME VARCHAR(128) NOT NULL, PRIMARY KEY (SUBSCRIPTION_ID, PURPOSE_NAME))");
            statement.execute("CREATE TABLE WEBHOOK_DELIVERY (DELIVERY_ID VARCHAR(64) PRIMARY KEY, " +
                    "SUBSCRIPTION_ID VARCHAR(64) NOT NULL, EVENT_ID VARCHAR(64) NOT NULL, STATUS VARCHAR(32) NOT NULL, " +
                    "ERROR_DETAIL VARCHAR(1024), " +
                    "ATTEMPT_COUNT INTEGER NOT NULL, MANUAL_RETRY_USED BOOLEAN NOT NULL DEFAULT FALSE, " +
                    "NEXT_RETRY_AT TIMESTAMP, CREATED_AT TIMESTAMP NOT NULL, " +
                    "UPDATED_AT TIMESTAMP NOT NULL, DELIVERED_AT TIMESTAMP)");
            statement.execute("CREATE TABLE POLL_DELIVERY (DELIVERY_ID VARCHAR(64) PRIMARY KEY, " +
                    "SUBSCRIPTION_ID VARCHAR(64) NOT NULL, EVENT_ID VARCHAR(64) NOT NULL, STATUS VARCHAR(32) NOT NULL, " +
                    "ERROR_CODE VARCHAR(64), ERROR_DETAIL VARCHAR(1024), CREATED_AT TIMESTAMP NOT NULL, " +
                    "COMPLETED_AT TIMESTAMP)");
        }
    }

    private Connection openConnection(String jdbcUrl, String username, String password, String dialect)
            throws Exception {
        Connection connection = username == null
                ? DriverManager.getConnection(jdbcUrl)
                : DriverManager.getConnection(jdbcUrl, username, password);
        try (Statement statement = connection.createStatement()) {
            if ("sqlite".equals(dialect)) {
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("PRAGMA busy_timeout = 5000");
            } else if ("mysql".equals(dialect)) {
                statement.execute("SET innodb_lock_wait_timeout = 5");
            } else if ("postgres".equals(dialect)) {
                statement.execute("SET lock_timeout = '5s'");
            }
        }
        return connection;
    }

    private void requireDocker() {
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            throw new SkipException("Docker is required for MySQL/PostgreSQL integration tests.");
        }
    }

    @FunctionalInterface
    private interface ConnectionFactory {

        Connection open() throws Exception;
    }
}
