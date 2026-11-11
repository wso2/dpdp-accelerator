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

import org.h2.tools.RunScript;
import org.testng.annotations.Test;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/** Verifies that the shipped H2 schema retains production database invariants. */
public class H2SchemaParityTest {

    private static final String SCHEMA_PATH =
            "accelerators/dpdp-is/carbon-home/dbscripts/dpdp-accelerator/event-notification/h2.sql";
    private static final String MIGRATION_PATH =
            "accelerators/dpdp-is/carbon-home/dbscripts/dpdp-accelerator/event-notification/migrations/h2.sql";

    @Test
    public void shippedSchemaEnforcesDeliveryIntegrityAndIndexes() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:enf_schema_parity;DB_CLOSE_DELAY=-1");
                Reader schema = Files.newBufferedReader(findSchema(), StandardCharsets.UTF_8)) {
            RunScript.execute(connection, schema);

            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO TOPIC "
                        + "(TOPIC_ID, ORG_ID, NAME, STATUS, INITIATED_BY) "
                        + "VALUES ('topic-1', 'org-1', 'accounts', 'active', 'USER')");
                statement.executeUpdate("INSERT INTO EVENT "
                        + "(EVENT_ID, ORG_ID, GROUP_ID, TOPIC_ID, PAYLOAD) "
                        + "VALUES ('event-1', 'org-1', 'group-1', 'topic-1', '{}')");
                statement.executeUpdate("INSERT INTO SUBSCRIPTION "
                        + "(SUBSCRIPTION_ID, ORG_ID, NAME, GROUP_ID, STATUS, PURPOSE_FILTER_MODE, "
                        + "PURPOSE_SET_HASH, DELIVERY_MODE, UPDATED_AT) VALUES "
                        + "('sub-1', 'org-1', 'Sub 1', 'group-1', 'active', 'all', '', 'webhook', "
                        + "TIMESTAMP '2000-01-01 00:00:00')");
                statement.executeUpdate("INSERT INTO SUBSCRIPTION_TOPIC VALUES ('org-1', 'sub-1', 'topic-1')");
                statement.executeUpdate("INSERT INTO WEBHOOK_DELIVERY "
                        + "(DELIVERY_ID, ORG_ID, SUBSCRIPTION_ID, EVENT_ID, STATUS, UPDATED_AT) VALUES "
                        + "('delivery-1', 'org-1', 'sub-1', 'event-1', 'pending', TIMESTAMP '2000-01-01 00:00:00')");

                expectThrows(SQLException.class, () -> statement.executeUpdate("INSERT INTO WEBHOOK_DELIVERY "
                        + "(DELIVERY_ID, ORG_ID, SUBSCRIPTION_ID, EVENT_ID, STATUS, UPDATED_AT) "
                        + "VALUES ('delivery-2', 'org-1', 'sub-1', 'event-1', 'pending', TIMESTAMP '2000-01-01 00:00:00')"));
                expectThrows(SQLException.class, () -> statement.executeUpdate("INSERT INTO WEBHOOK_DELIVERY "
                        + "(DELIVERY_ID, ORG_ID, SUBSCRIPTION_ID, EVENT_ID, STATUS, UPDATED_AT) "
                        + "VALUES ('delivery-3', 'org-1', 'sub-1', 'missing-event', 'pending', TIMESTAMP '2000-01-01 00:00:00')"));
                expectThrows(SQLException.class, () -> statement.executeUpdate("INSERT INTO WEBHOOK_DELIVERY "
                        + "(DELIVERY_ID, ORG_ID, SUBSCRIPTION_ID, EVENT_ID, STATUS, UPDATED_AT) "
                        + "VALUES ('delivery-4', 'wrong-org', 'sub-1', 'event-1', 'pending', TIMESTAMP '2000-01-01 00:00:00')"));
                expectThrows(SQLException.class, () -> statement.executeUpdate("INSERT INTO WEBHOOK_DELIVERY_ACK "
                        + "(ACK_ID, DELIVERY_ID, ORG_ID, COMPLETION_STATUS, COMPLETION_EVIDENCE) "
                        + "VALUES ('ack-bad-org', 'delivery-1', 'wrong-org', 'completed', 'evidence')"));
                expectThrows(SQLException.class, () -> statement.executeUpdate("INSERT INTO POLL_DELIVERY "
                        + "(DELIVERY_ID, ORG_ID, SUBSCRIPTION_ID, EVENT_ID, STATUS) "
                        + "VALUES ('poll-bad-org', 'wrong-org', 'sub-1', 'event-1', 'pending')"));
                expectThrows(SQLException.class, () -> statement.executeUpdate("INSERT INTO EVENT "
                        + "(EVENT_ID, ORG_ID, GROUP_ID, TOPIC_ID, PAYLOAD) "
                        + "VALUES ('event-bad-org', 'wrong-org', 'group-1', 'topic-1', '{}')"));
                expectThrows(SQLException.class, () -> statement.executeUpdate("INSERT INTO EVENT_PURPOSE "
                        + "(EVENT_ID, ORG_ID, PURPOSE_NAME) VALUES ('event-1', 'wrong-org', 'marketing')"));
                expectThrows(SQLException.class, () -> statement.executeUpdate("INSERT INTO SUBSCRIPTION_PURPOSE "
                        + "(SUBSCRIPTION_ID, ORG_ID, PURPOSE_NAME) VALUES ('sub-1', 'wrong-org', 'marketing')"));
                expectThrows(SQLException.class, () -> statement.executeUpdate("UPDATE WEBHOOK_DELIVERY "
                        + "SET STATUS = 'invalid' WHERE DELIVERY_ID = 'delivery-1'"));

                statement.executeUpdate("UPDATE SUBSCRIPTION SET STATUS = 'pending' "
                        + "WHERE SUBSCRIPTION_ID = 'sub-1'");
                statement.executeUpdate("UPDATE WEBHOOK_DELIVERY SET STATUS = 'in_flight' "
                        + "WHERE DELIVERY_ID = 'delivery-1'");
            }

            assertTimestampUpdated(connection, "SUBSCRIPTION", "SUBSCRIPTION_ID", "sub-1");
            assertTimestampUpdated(connection, "WEBHOOK_DELIVERY", "DELIVERY_ID", "delivery-1");
            assertRequiredIndexes(connection);
            for (String table : new String[] {"EVENT_PURPOSE", "SUBSCRIPTION_PURPOSE", "WEBHOOK_DELIVERY",
                    "WEBHOOK_DELIVERY_ACK", "POLL_DELIVERY"}) {
                assertOrgIdNotNull(connection, table);
            }
        }
    }

    private static void assertOrgIdNotNull(Connection connection, String table) throws SQLException {
        try (ResultSet rs = connection.getMetaData().getColumns(null, null, table, "ORG_ID")) {
            assertTrue(rs.next(), "Table " + table + " is missing ORG_ID column");
            org.testng.Assert.assertEquals(rs.getInt("NULLABLE"), java.sql.DatabaseMetaData.columnNoNulls,
                    "ORG_ID in table " + table + " must be NOT NULL");
        }
    }

    private static void assertTimestampUpdated(Connection connection, String table, String idColumn,
            String id) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT UPDATED_AT FROM " + table
                        + " WHERE " + idColumn + " = '" + id + "'")) {
            assertTrue(result.next());
            assertTrue(result.getTimestamp(1).after(Timestamp.valueOf("2000-01-01 00:00:00")));
        }
    }

    private static void assertRequiredIndexes(Connection connection) throws SQLException {
        Set<String> indexes = new HashSet<>();
        for (String table : new String[] {"EVENT", "EVENT_PURPOSE", "SUBSCRIPTION", "SUBSCRIPTION_PURPOSE",
                "WEBHOOK_DELIVERY", "WEBHOOK_DELIVERY_ACK", "POLL_DELIVERY"}) {
            try (ResultSet result = connection.getMetaData().getIndexInfo(null, null, table, false, false)) {
                while (result.next()) {
                    indexes.add(result.getString("INDEX_NAME"));
                }
            }
        }
        for (String required : new String[] {"IDX_EVENT_ORG_GROUP_TOPIC_CREATED", "IDX_EVENT_ORG_CREATED",
                "IDX_EVENT_ORG_TOPIC", "IDX_EVENT_PURPOSE_NAME",
                "IDX_EP_ORG", "IDX_SUB_STATUS_UPDATED", "IDX_SUB_MATCHING", "IDX_SUB_PURPOSE_NAME",
                "IDX_SP_ORG", "IDX_EDP_SUB", "IDX_WD_ORG", "IDX_EDA_COMPLETION_STATUS", "IDX_EDA_DELIVERY",
                "IDX_WDA_ORG", "IDX_EDPL_SUB", "IDX_PD_ORG"}) {
            assertTrue(indexes.contains(required), "Missing H2 index: " + required);
        }
    }

    private static Path findSchema() {
        return findRelativePath(SCHEMA_PATH);
    }

    private static Path findMigration() {
        return findRelativePath(MIGRATION_PATH);
    }

    private static Path findRelativePath(String relativePath) {
        Path current = Paths.get("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate " + relativePath);
    }

    @Test
    public void multiTopicSubscriptionMatchesBothTopicsAndPreservesPageCardinality() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:multi_topic_schema");
                Reader schema = Files.newBufferedReader(findSchema(), StandardCharsets.UTF_8)) {
            RunScript.execute(connection, schema);
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO TOPIC (TOPIC_ID, ORG_ID, NAME) VALUES "
                        + "('a', 'org', 'accounts'), ('b', 'org', 'billing'), ('c', 'other', 'private')");
            }
            org.wso2.dpdp.accelerator.event.notifications.dao.impl.SubscriptionDAOImpl dao =
                    new org.wso2.dpdp.accelerator.event.notifications.dao.impl.SubscriptionDAOImpl();
            org.wso2.dpdp.accelerator.event.notifications.dao.model.Subscription subscription =
                    new org.wso2.dpdp.accelerator.event.notifications.dao.model.Subscription();
            subscription.setSubscriptionId("sub");
            subscription.setName("Subscription Alpha");
            subscription.setOrgId("org");
            subscription.setGroupId("group");
            subscription.setTopicIds(java.util.Arrays.asList("a"));
            subscription.setPurposeFilterMode("all");
            subscription.setPurposes(java.util.Collections.emptyList());
            subscription.setDeliveryMode("poll");
            subscription.setSharedSecret("secret");
            subscription.setStatus("active");
            subscription.setTopicIds(java.util.Arrays.asList("b", "a"));
            dao.addSubscription(connection, subscription);
            connection.commit();
            org.testng.Assert.assertEquals(dao.getSubscriptionById(connection, "sub", "org").get().getTopicIds(),
                    java.util.Arrays.asList("a", "b"));
            org.testng.Assert.assertEquals(dao.getActiveSubscriptionsForFanOut(connection, "org", "a").size(), 1);
            org.testng.Assert.assertEquals(dao.getActiveSubscriptionsForFanOut(connection, "org", "b").size(), 1);
            org.testng.Assert.assertEquals(dao.getActiveSubscriptionsForFanOut(connection, "other", "a").size(), 0);
            PaginatedDAOResult<org.wso2.dpdp.accelerator.event.notifications.dao.model.Subscription> page =
                    dao.listSubscriptions(connection, "org", null, null, "billing", 1, 0, null);
            org.testng.Assert.assertEquals(page.getTotal(), 1L);
            org.testng.Assert.assertEquals(page.getItems().get(0).getTopicNames(),
                    java.util.Arrays.asList("accounts", "billing"));
            try (Statement statement = connection.createStatement()) {
                expectThrows(SQLException.class, () -> statement.executeUpdate(
                        "INSERT INTO SUBSCRIPTION_TOPIC VALUES ('org', 'sub', 'c')"));
                expectThrows(SQLException.class, () -> statement.executeUpdate(
                        "INSERT INTO SUBSCRIPTION_TOPIC VALUES ('other', 'sub', 'c')"));
            }
            connection.rollback();
            subscription.setSubscriptionId("rollback");
            subscription.setName("Rollback Sub");
            subscription.setGroupId("new-group");
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE SUBSCRIPTION_TOPIC ADD CONSTRAINT FAIL_SECOND "
                        + "CHECK (SUBSCRIPTION_ID <> 'rollback' OR TOPIC_ID <> 'b')");
            }
            expectThrows(
                    org.wso2.dpdp.accelerator.event.notifications.common.exception.dao
                            .EventNotificationDuplicateResourceException.class,
                    () -> dao.addSubscription(connection, subscription));
            connection.rollback();
            assertTrue(!dao.getSubscriptionById(connection, "rollback", "org").isPresent());
        }
    }

    @Test
    public void subscriptionNameUniquenessAndReuseAfterDelete() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:sub_name_test");
                Reader schema = Files.newBufferedReader(findSchema(), StandardCharsets.UTF_8)) {
            RunScript.execute(connection, schema);
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO TOPIC (TOPIC_ID, ORG_ID, NAME) VALUES "
                        + "('t1', 'org-1', 'orders'), ('t2', 'org-2', 'orders')");
            }

            org.wso2.dpdp.accelerator.event.notifications.dao.impl.SubscriptionDAOImpl dao =
                    new org.wso2.dpdp.accelerator.event.notifications.dao.impl.SubscriptionDAOImpl();

            org.wso2.dpdp.accelerator.event.notifications.dao.model.Subscription sub1 =
                    new org.wso2.dpdp.accelerator.event.notifications.dao.model.Subscription();
            sub1.setSubscriptionId("sub-1");
            sub1.setName("Orders Webhook");
            sub1.setOrgId("org-1");
            sub1.setGroupId("group-1");
            sub1.setTopicIds(Collections.singletonList("t1"));
            sub1.setPurposeFilterMode("all");
            sub1.setPurposes(Collections.emptyList());
            sub1.setDeliveryMode("poll");
            sub1.setStatus("active");
            dao.addSubscription(connection, sub1);
            connection.commit();

            org.wso2.dpdp.accelerator.event.notifications.dao.model.Subscription fetched =
                    dao.getSubscriptionById(connection, "sub-1", "org-1").get();
            org.testng.Assert.assertEquals(fetched.getName(), "Orders Webhook");

            // Case-insensitive duplicate in same org fails
            org.wso2.dpdp.accelerator.event.notifications.dao.model.Subscription sub2 =
                    new org.wso2.dpdp.accelerator.event.notifications.dao.model.Subscription();
            sub2.setSubscriptionId("sub-2");
            sub2.setName("orders webhook");
            sub2.setOrgId("org-1");
            sub2.setGroupId("group-1");
            sub2.setTopicIds(Collections.singletonList("t1"));
            sub2.setPurposeFilterMode("all");
            sub2.setPurposes(Collections.emptyList());
            sub2.setDeliveryMode("poll");
            sub2.setStatus("active");
            expectThrows(org.wso2.dpdp.accelerator.event.notifications.common.exception.dao
                    .EventNotificationDuplicateResourceException.class,
                    () -> dao.addSubscription(connection, sub2));
            connection.rollback();

            // Same name in different org succeeds
            org.wso2.dpdp.accelerator.event.notifications.dao.model.Subscription sub3 =
                    new org.wso2.dpdp.accelerator.event.notifications.dao.model.Subscription();
            sub3.setSubscriptionId("sub-3");
            sub3.setName("Orders Webhook");
            sub3.setOrgId("org-2");
            sub3.setGroupId("group-2");
            sub3.setTopicIds(Collections.singletonList("t2"));
            sub3.setPurposeFilterMode("all");
            sub3.setPurposes(Collections.emptyList());
            sub3.setDeliveryMode("poll");
            sub3.setStatus("active");
            dao.addSubscription(connection, sub3);
            connection.commit();

            // Soft-delete sub1 in org-1
            org.testng.Assert.assertTrue(dao.deleteSubscriptionAtomic(connection, "sub-1", "org-1", "active"));
            connection.commit();

            // Name is freed and reusable after delete
            org.wso2.dpdp.accelerator.event.notifications.dao.model.Subscription sub4 =
                    new org.wso2.dpdp.accelerator.event.notifications.dao.model.Subscription();
            sub4.setSubscriptionId("sub-4");
            sub4.setName("ORDERS WEBHOOK");
            sub4.setOrgId("org-1");
            sub4.setGroupId("group-1");
            sub4.setTopicIds(Collections.singletonList("t1"));
            sub4.setPurposeFilterMode("all");
            sub4.setPurposes(Collections.emptyList());
            sub4.setDeliveryMode("poll");
            sub4.setStatus("active");
            dao.addSubscription(connection, sub4);
            connection.commit();

            org.wso2.dpdp.accelerator.event.notifications.dao.model.Subscription fetched4 =
                    dao.getSubscriptionById(connection, "sub-4", "org-1").get();
            org.testng.Assert.assertEquals(fetched4.getName(), "ORDERS WEBHOOK");
        }
    }

    @Test
    public void migrationBackfillsCompletedWebhookDeliveriesIdempotently() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:enf_migration_test");
                Reader schema = Files.newBufferedReader(findSchema(), StandardCharsets.UTF_8)) {
            RunScript.execute(connection, schema);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO TOPIC (TOPIC_ID, ORG_ID, NAME) VALUES ('t1', 'org-1', 'orders')");
                statement.executeUpdate("INSERT INTO EVENT (EVENT_ID, ORG_ID, GROUP_ID, TOPIC_ID, PAYLOAD) VALUES "
                        + "('e1', 'org-1', 'g1', 't1', '{}'), "
                        + "('e2', 'org-1', 'g1', 't1', '{}'), "
                        + "('e3', 'org-1', 'g1', 't1', '{}')");
                statement.executeUpdate("INSERT INTO SUBSCRIPTION "
                        + "(SUBSCRIPTION_ID, ORG_ID, NAME, GROUP_ID, STATUS, PURPOSE_FILTER_MODE, "
                        + "PURPOSE_SET_HASH, DELIVERY_MODE) VALUES "
                        + "('s1', 'org-1', 'Sub 1', 'g1', 'active', 'all', '', 'webhook')");
                statement.executeUpdate("INSERT INTO WEBHOOK_DELIVERY "
                        + "(DELIVERY_ID, ORG_ID, SUBSCRIPTION_ID, EVENT_ID, STATUS) VALUES "
                        + "('d-completed', 'org-1', 's1', 'e1', 'delivered'), "
                        + "('d-disputed', 'org-1', 's1', 'e2', 'delivered'), "
                        + "('d-no-ack', 'org-1', 's1', 'e3', 'delivered')");
                statement.executeUpdate("INSERT INTO WEBHOOK_DELIVERY_ACK "
                        + "(ACK_ID, DELIVERY_ID, ORG_ID, COMPLETION_STATUS, COMPLETION_EVIDENCE) VALUES "
                        + "('ack-1', 'd-completed', 'org-1', 'completed', 'https://example.com/ev1'), "
                        + "('ack-2', 'd-disputed', 'org-1', 'disputed', 'https://example.com/ev2')");
            }

            try (Reader migration = Files.newBufferedReader(findMigration(), StandardCharsets.UTF_8)) {
                RunScript.execute(connection, migration);
            }
            // Re-run to verify idempotency
            try (Reader migration = Files.newBufferedReader(findMigration(), StandardCharsets.UTF_8)) {
                RunScript.execute(connection, migration);
            }

            try (Statement statement = connection.createStatement()) {
                try (ResultSet rs = statement.executeQuery(
                        "SELECT DELIVERY_ID, STATUS FROM WEBHOOK_DELIVERY ORDER BY DELIVERY_ID")) {
                    assertTrue(rs.next());
                    org.testng.Assert.assertEquals(rs.getString("DELIVERY_ID"), "d-completed");
                    org.testng.Assert.assertEquals(rs.getString("STATUS"), "completed");
                    assertTrue(rs.next());
                    org.testng.Assert.assertEquals(rs.getString("DELIVERY_ID"), "d-disputed");
                    org.testng.Assert.assertEquals(rs.getString("STATUS"), "delivered");
                    assertTrue(rs.next());
                    org.testng.Assert.assertEquals(rs.getString("DELIVERY_ID"), "d-no-ack");
                    org.testng.Assert.assertEquals(rs.getString("STATUS"), "delivered");
                }
                try (ResultSet rs = statement.executeQuery(
                        "SELECT COUNT(*) FROM WEBHOOK_DELIVERY_ACK WHERE COMPLETION_STATUS = 'completed'")) {
                    assertTrue(rs.next());
                    org.testng.Assert.assertEquals(rs.getInt(1), 1);
                }
            }
        }
    }

    @Test
    public void migrationNormalizesLegacyDeregisteredTopicsIdempotently() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:enf_topic_migration_test")) {
            try (Statement statement = connection.createStatement()) {
                // Simulate the pre-unified-deletion TOPIC DDL with an unnamed inline CHECK constraint.
                statement.execute("CREATE TABLE TOPIC ("
                        + "TOPIC_ID VARCHAR(64) NOT NULL, "
                        + "ORG_ID VARCHAR(128) NOT NULL, "
                        + "NAME VARCHAR(225) NOT NULL, "
                        + "DESCRIPTION VARCHAR(255) DEFAULT NULL, "
                        + "STATUS VARCHAR(32) DEFAULT 'active' NOT NULL CHECK (STATUS IN ('active', 'deregistered')), "
                        + "INITIATED_BY VARCHAR(32) DEFAULT 'USER' NOT NULL, "
                        + "CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL, "
                        + "ACTIVE_NAME VARCHAR(225) GENERATED ALWAYS AS "
                        + "(CASE WHEN STATUS = 'active' THEN LOWER(NAME) ELSE NULL END), "
                        + "PRIMARY KEY (TOPIC_ID), "
                        + "CONSTRAINT UQ_TOPIC_ORG_ID UNIQUE (ORG_ID, TOPIC_ID))");
                statement.execute("CREATE UNIQUE INDEX UQ_TOPIC_ORG_ACTIVE_NAME ON TOPIC (ORG_ID, ACTIVE_NAME)");
                statement.execute("CREATE TABLE EVENT ("
                        + "EVENT_ID VARCHAR(64) NOT NULL, ORG_ID VARCHAR(128) NOT NULL, "
                        + "GROUP_ID VARCHAR(128) NOT NULL, TOPIC_ID VARCHAR(64) NOT NULL, "
                        + "PAYLOAD CLOB NOT NULL, PRIMARY KEY (EVENT_ID))");
                statement.execute("CREATE TABLE SUBSCRIPTION ("
                        + "SUBSCRIPTION_ID VARCHAR(64) NOT NULL, ORG_ID VARCHAR(128) NOT NULL, "
                        + "PRIMARY KEY (SUBSCRIPTION_ID))");
                statement.execute("CREATE TABLE WEBHOOK_DELIVERY ("
                        + "DELIVERY_ID VARCHAR(64) NOT NULL, ORG_ID VARCHAR(128) NOT NULL, "
                        + "SUBSCRIPTION_ID VARCHAR(64) NOT NULL, EVENT_ID VARCHAR(64) NOT NULL, "
                        + "STATUS VARCHAR(32) DEFAULT 'pending' NOT NULL, PRIMARY KEY (DELIVERY_ID))");
                statement.execute("CREATE TABLE WEBHOOK_DELIVERY_ACK ("
                        + "ACK_ID VARCHAR(64) NOT NULL, DELIVERY_ID VARCHAR(64) NOT NULL, "
                        + "ORG_ID VARCHAR(128) NOT NULL, COMPLETION_STATUS VARCHAR(32) NOT NULL, "
                        + "COMPLETION_EVIDENCE VARCHAR(512) NOT NULL, PRIMARY KEY (ACK_ID))");

                statement.executeUpdate("INSERT INTO TOPIC (TOPIC_ID, ORG_ID, NAME, STATUS) VALUES "
                        + "('t-active', 'org-1', 'orders', 'active'), "
                        + "('t-legacy', 'org-1', 'retired', 'deregistered')");
            }

            try (Reader migration = Files.newBufferedReader(findMigration(), StandardCharsets.UTF_8)) {
                RunScript.execute(connection, migration);
            }
            // Re-run to verify idempotency.
            try (Reader migration = Files.newBufferedReader(findMigration(), StandardCharsets.UTF_8)) {
                RunScript.execute(connection, migration);
            }

            try (Statement statement = connection.createStatement()) {
                try (ResultSet rs = statement.executeQuery(
                        "SELECT TOPIC_ID, STATUS FROM TOPIC ORDER BY TOPIC_ID")) {
                    assertTrue(rs.next());
                    org.testng.Assert.assertEquals(rs.getString("TOPIC_ID"), "t-active");
                    org.testng.Assert.assertEquals(rs.getString("STATUS"), "active");
                    assertTrue(rs.next());
                    org.testng.Assert.assertEquals(rs.getString("TOPIC_ID"), "t-legacy");
                    org.testng.Assert.assertEquals(rs.getString("STATUS"), "deleted");
                }

                // Legacy status value must now be rejected, while re-using the deleted topic's name is allowed.
                expectThrows(SQLException.class, () -> statement.executeUpdate(
                        "INSERT INTO TOPIC (TOPIC_ID, ORG_ID, NAME, STATUS) VALUES "
                                + "('t-bad', 'org-1', 'other', 'deregistered')"));
                statement.executeUpdate("INSERT INTO TOPIC (TOPIC_ID, ORG_ID, NAME, STATUS) VALUES "
                        + "('t-reused', 'org-1', 'RETIRED', 'active')");
            }
        }
    }
}
