package org.wso2.dpdp.accelerator.event.notifications.dao.impl;

import org.h2.jdbcx.JdbcDataSource;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Event;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.PollDelivery;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDelivery;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDeliveryAck;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.Optional;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class EventAndAckDAOImplTest {

    private String databaseName;
    private Connection connection;

    @BeforeMethod
    public void setUp() throws Exception {
        databaseName = "dao_event_" + System.nanoTime();
        connection = DriverManager.getConnection("jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1");
        connection.createStatement().execute("CREATE TABLE TOPIC (TOPIC_ID VARCHAR(64) PRIMARY KEY, "
                + "ORG_ID VARCHAR(128), NAME VARCHAR(225), STATUS VARCHAR(32), CONSTRAINT UQ_TOPIC_ORG_ID UNIQUE (ORG_ID, TOPIC_ID))");
        connection.createStatement().execute("CREATE TABLE EVENT (EVENT_ID VARCHAR(64) PRIMARY KEY, "
                + "ORG_ID VARCHAR(128), GROUP_ID VARCHAR(128), TOPIC_ID VARCHAR(64), PAYLOAD VARCHAR(4096), CREATED_AT TIMESTAMP, "
                + "CONSTRAINT UQ_EVENT_ORG_ID UNIQUE (ORG_ID, EVENT_ID), "
                + "CONSTRAINT FK_E_ORG_TOPIC FOREIGN KEY (ORG_ID, TOPIC_ID) REFERENCES TOPIC (ORG_ID, TOPIC_ID))");
        connection.createStatement().execute("CREATE TABLE EVENT_PURPOSE (EVENT_ID VARCHAR(64), "
                + "ORG_ID VARCHAR(128), PURPOSE_NAME VARCHAR(128), "
                + "CONSTRAINT FK_EVENT_PURPOSES_EVENT FOREIGN KEY (ORG_ID, EVENT_ID) REFERENCES EVENT (ORG_ID, EVENT_ID) ON DELETE CASCADE)");
        connection.createStatement().execute("CREATE TABLE WEBHOOK_DELIVERY (DELIVERY_ID VARCHAR(64) PRIMARY KEY, "
                + "ORG_ID VARCHAR(128), SUBSCRIPTION_ID VARCHAR(64), EVENT_ID VARCHAR(64), STATUS VARCHAR(32), ATTEMPT_COUNT INT DEFAULT 0, "
                + "NEXT_RETRY_AT TIMESTAMP, CREATED_AT TIMESTAMP, UPDATED_AT TIMESTAMP, DELIVERED_AT TIMESTAMP, "
                + "ERROR_DETAIL VARCHAR(1024))");
        connection.createStatement().execute("CREATE TABLE POLL_DELIVERY (DELIVERY_ID VARCHAR(64) PRIMARY KEY, "
                + "ORG_ID VARCHAR(128), SUBSCRIPTION_ID VARCHAR(64), EVENT_ID VARCHAR(64), STATUS VARCHAR(32), "
                + "ERROR_CODE VARCHAR(64), ERROR_DETAIL VARCHAR(1024), CREATED_AT TIMESTAMP, COMPLETED_AT TIMESTAMP)");
        connection.createStatement().execute("CREATE TABLE WEBHOOK_DELIVERY_ACK (ACK_ID VARCHAR(64) PRIMARY KEY, "
                + "DELIVERY_ID VARCHAR(64), ORG_ID VARCHAR(128), COMPLETED_AT TIMESTAMP, COMPLETION_STATUS VARCHAR(32), COMPLETION_EVIDENCE VARCHAR(4096))");
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1");
        setManagerDataSource(dataSource);
        connection.createStatement().execute(
                "INSERT INTO TOPIC (TOPIC_ID, ORG_ID, STATUS) VALUES ('topic-1', 'org-1', 'active')");
    }

    @AfterMethod
    public void tearDown() throws Exception {
        if (connection != null) {
            connection.close();
        }
        setManagerDataSource(null);
    }

    @Test
    public void eventCrudAndPurposeLookupUseSharedConnection() throws Exception {
        Event event = new Event("event-1", "org-1", "group-1", "topic-1", "{\"x\":1}",
                new Timestamp(System.currentTimeMillis()));
        EventDAOImpl dao = new EventDAOImpl();
        assertTrue(dao.addEvent(connection, event));
        dao.addEventPurposes(connection, "event-1", "org-1", java.util.Arrays.asList("marketing", " ", null));
        assertEquals(dao.getEventPurposes(connection, "event-1", "org-1"), Collections.singletonList("marketing"));
        assertEquals(dao.getEventPurposes(connection, "event-1", "wrong-org"), Collections.emptyList());
        assertTrue(dao.hasActiveEventsForTopic(connection, "topic-1", "org-1"));
        assertFalse(dao.hasActiveEventsForTopic(connection, "topic-1", "wrong-org"));
        Optional<Event> fetched = dao.getEventById(connection, "event-1", "org-1");
        assertTrue(fetched.isPresent());
        assertEquals(fetched.get().getPurposes(), Collections.singletonList("marketing"));
        assertFalse(dao.getEventById(connection, "missing", "org-1").isPresent());
    }

    @Test
    public void deliveryAckCanBeInsertedAndRead() {
        DeliveryAckDAOImpl dao = new DeliveryAckDAOImpl();
        WebhookDeliveryAck ack = new WebhookDeliveryAck("ack-1", "delivery-1", "org-1",
                new Timestamp(System.currentTimeMillis()), "completed", "200");
        assertTrue(dao.addDeliveryAck(connection, ack));
        WebhookDeliveryAck fetched = dao.getDeliveryAckByDeliveryId(connection, "delivery-1", "org-1").get();
        assertEquals(fetched.getAckId(), "ack-1");
        assertEquals(fetched.getOrgId(), "org-1");
        assertFalse(dao.getDeliveryAckByDeliveryId(connection, "missing", "org-1").isPresent());
        assertFalse(dao.getDeliveryAckByDeliveryId(connection, "delivery-1", "wrong-org").isPresent());
    }

    @Test
    public void eventSearchCorrelatesSubscriptionAndStatusToOneDelivery() throws Exception {
        connection.createStatement().executeUpdate("UPDATE TOPIC SET NAME = 'accounts' WHERE TOPIC_ID = 'topic-1'");
        connection.createStatement().executeUpdate("INSERT INTO EVENT "
                + "(EVENT_ID, ORG_ID, GROUP_ID, TOPIC_ID, PAYLOAD, CREATED_AT) "
                + "VALUES ('event-1', 'org-1', 'group-1', 'topic-1', '{}', CURRENT_TIMESTAMP)");
        connection.createStatement().executeUpdate("INSERT INTO WEBHOOK_DELIVERY "
                + "(DELIVERY_ID, ORG_ID, SUBSCRIPTION_ID, EVENT_ID, STATUS) "
                + "VALUES ('delivery-1', 'org-1', 'sub-1', 'event-1', 'delivered')");
        connection.createStatement().executeUpdate("INSERT INTO WEBHOOK_DELIVERY "
                + "(DELIVERY_ID, ORG_ID, SUBSCRIPTION_ID, EVENT_ID, STATUS) "
                + "VALUES ('delivery-2', 'org-1', 'sub-2', 'event-1', 'failed')");

        EventDAOImpl dao = new EventDAOImpl();
        assertEquals(dao.searchEvents(connection, "org-1", null, "failed", null, "sub-1", null, null, 20, 0)
                .getTotal(), 0);
        assertEquals(dao.searchEvents(connection, "org-1", null, "delivered", null, "sub-1", null, null, 20, 0)
                .getTotal(), 1);
    }

    @Test
    public void webhookAndPollDeliveryCanBeInsertedAndReadWithOrgIsolation() {
        DeliveryDAOImpl dao = new DeliveryDAOImpl();
        Timestamp now = new Timestamp(System.currentTimeMillis());

        WebhookDelivery webhook = new WebhookDelivery("wd-1", "org-1", "sub-1", "event-1",
                "delivered", 1, null, now, now, now);
        assertTrue(dao.addWebhookDelivery(connection, webhook));

        Optional<WebhookDelivery> fetchedWebhook = dao.getWebhookDeliveryById(connection, "wd-1", "org-1");
        assertTrue(fetchedWebhook.isPresent());
        assertEquals(fetchedWebhook.get().getDeliveryId(), "wd-1");
        assertEquals(fetchedWebhook.get().getStatus(), "delivered");

        assertTrue(dao.markWebhookDeliveryCompleted(connection, "wd-1", "org-1"));
        Optional<WebhookDelivery> completedWebhook = dao.getWebhookDeliveryById(connection, "wd-1", "org-1");
        assertTrue(completedWebhook.isPresent());
        assertEquals(completedWebhook.get().getStatus(), "completed");

        assertFalse(dao.getWebhookDeliveryById(connection, "wd-1", "wrong-org").isPresent());
        assertFalse(dao.getWebhookDeliveryById(connection, "missing", "org-1").isPresent());

        PollDelivery poll = new PollDelivery("pd-1", "org-1", "sub-1", "event-1",
                "pending", null, null, now, null);
        assertTrue(dao.addPollDelivery(connection, poll));

        Optional<PollDelivery> fetchedPoll = dao.getPollDeliveryById(connection, "pd-1", "org-1");
        assertTrue(fetchedPoll.isPresent());
        assertEquals(fetchedPoll.get().getDeliveryId(), "pd-1");
        assertEquals(fetchedPoll.get().getOrgId(), "org-1");
        assertFalse(dao.getPollDeliveryById(connection, "pd-1", "wrong-org").isPresent());
        assertFalse(dao.getPollDeliveryById(connection, "missing", "org-1").isPresent());
    }

    private void setManagerDataSource(Object dataSource) throws Exception {
        Field field = Class.forName("org.wso2.dpdp.accelerator.common.persistence.JDBCPersistenceManager")
                .getDeclaredField("dataSource");
        field.setAccessible(true);
        field.set(null, dataSource);
    }
}
