/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.dpdp.accelerator.consent.extensions.dao.impl;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.consent.extensions.dao.ConsentHistoryDAO;
import org.wso2.dpdp.accelerator.consent.extensions.dao.exceptions.ConsentHistoryDataInsertionException;
import org.wso2.dpdp.accelerator.consent.extensions.dao.exceptions.ConsentHistoryDataRetrievalException;
import org.wso2.dpdp.accelerator.consent.extensions.dao.models.ConsentHistoryRecord;
import org.wso2.dpdp.accelerator.consent.extensions.dao.models.ConsentStatusAuditRecord;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Exercises the real SQL against an in-memory H2 database - a plain interface/impl mock would
 * only prove the mock was called, not that the queries are actually correct.
 */
public class ConsentHistoryDAOImplTest {

    private static final String TENANT_DOMAIN = "tenant-a.com";
    private static final String CONSENT_ID = "consent-1234";

    private Connection connection;
    private ConsentHistoryDAO consentHistoryDAO;

    @BeforeMethod
    public void setUp() throws SQLException {

        connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE DPDP_CONSENT_STATUS_AUDIT ("
                    + "AUDIT_ID VARCHAR(36) NOT NULL PRIMARY KEY,"
                    + "CONSENT_ID VARCHAR(255) NOT NULL,"
                    + "ORG_ID VARCHAR(255) NOT NULL,"
                    + "PREVIOUS_STATUS VARCHAR(64),"
                    + "CURRENT_STATUS VARCHAR(64) NOT NULL,"
                    + "ACTION_TYPE VARCHAR(64) NOT NULL,"
                    + "ACTION_BY VARCHAR(255),"
                    + "ACTION_TIME BIGINT NOT NULL)");
            statement.execute("CREATE TABLE DPDP_CONSENT_HISTORY ("
                    + "HISTORY_ID VARCHAR(36) NOT NULL PRIMARY KEY,"
                    + "CONSENT_ID VARCHAR(255) NOT NULL,"
                    + "ORG_ID VARCHAR(255) NOT NULL,"
                    + "ACTION_TYPE VARCHAR(64) NOT NULL,"
                    + "SNAPSHOT CLOB,"
                    + "ACTION_BY VARCHAR(255),"
                    + "ACTION_TIME BIGINT NOT NULL)");
        }
        consentHistoryDAO = new ConsentHistoryDAOImpl();
    }

    @AfterMethod
    public void tearDown() throws SQLException {

        connection.close();
    }

    @Test
    public void insertAndRetrieveStatusAuditHistoryOrderedByActionTimeDescending() throws Exception {

        insertStatusAudit(null, "PENDING", "CREATE", 1000L);
        insertStatusAudit("PENDING", "ACTIVE", "AUTHORIZE", 2000L);
        insertStatusAudit("ACTIVE", "REVOKED", "REVOKE", 3000L);

        List<ConsentStatusAuditRecord> records = consentHistoryDAO.getStatusAuditHistory(connection, TENANT_DOMAIN,
                CONSENT_ID, 20, 0);

        assertEquals(records.size(), 3);
        assertEquals(records.get(0).getActionType(), "REVOKE");
        assertEquals(records.get(0).getCurrentStatus(), "REVOKED");
        assertEquals(records.get(2).getActionType(), "CREATE");
        assertEquals(records.get(2).getPreviousStatus(), null);

        assertEquals(consentHistoryDAO.getStatusAuditHistoryCount(connection, TENANT_DOMAIN, CONSENT_ID), 3);
    }

    @Test
    public void statusAuditHistoryRespectsLimitAndOffset() throws Exception {

        insertStatusAudit(null, "PENDING", "CREATE", 1000L);
        insertStatusAudit("PENDING", "ACTIVE", "AUTHORIZE", 2000L);
        insertStatusAudit("ACTIVE", "REVOKED", "REVOKE", 3000L);

        List<ConsentStatusAuditRecord> page = consentHistoryDAO.getStatusAuditHistory(connection, TENANT_DOMAIN,
                CONSENT_ID, 1, 1);

        assertEquals(page.size(), 1);
        assertEquals(page.get(0).getActionType(), "AUTHORIZE");
    }

    @Test
    public void statusAuditHistoryIsScopedByOrgId() throws Exception {

        insertStatusAudit(null, "PENDING", "CREATE", 1000L);

        List<ConsentStatusAuditRecord> otherTenant = consentHistoryDAO.getStatusAuditHistory(connection,
                "tenant-b.com", CONSENT_ID, 20, 0);

        assertTrue(otherTenant.isEmpty());
    }

    @Test
    public void insertAndRetrieveConsentHistorySnapshots() throws Exception {

        insertHistorySnapshot("REVOKE", "{\"state\":\"ACTIVE\"}", 1000L);
        insertHistorySnapshot("DELETE", "{\"state\":\"REVOKED\"}", 2000L);

        List<ConsentHistoryRecord> records = consentHistoryDAO.getConsentHistory(connection, TENANT_DOMAIN,
                CONSENT_ID, 20, 0);

        assertEquals(records.size(), 2);
        assertEquals(records.get(0).getActionType(), "DELETE");
        assertEquals(records.get(0).getSnapshot(), "{\"state\":\"REVOKED\"}");

        assertEquals(consentHistoryDAO.getConsentHistoryCount(connection, TENANT_DOMAIN, CONSENT_ID), 2);
    }

    // A closed connection is the cheapest way to make the driver raise a real SQLException, so the
    // DAO's translation of it into the module's unchecked exceptions is exercised end to end
    // rather than against a mocked ResultSet.

    @Test(expectedExceptions = ConsentHistoryDataInsertionException.class)
    public void insertStatusAuditWrapsSqlFailure() throws Exception {

        connection.close();
        insertStatusAudit(null, "PENDING", "CREATE", 1000L);
    }

    @Test(expectedExceptions = ConsentHistoryDataInsertionException.class)
    public void insertHistorySnapshotWrapsSqlFailure() throws Exception {

        connection.close();
        insertHistorySnapshot("CREATE", "{}", 1000L);
    }

    @Test(expectedExceptions = ConsentHistoryDataRetrievalException.class)
    public void getStatusAuditHistoryWrapsSqlFailure() throws Exception {

        connection.close();
        consentHistoryDAO.getStatusAuditHistory(connection, TENANT_DOMAIN, CONSENT_ID, 20, 0);
    }

    @Test(expectedExceptions = ConsentHistoryDataRetrievalException.class)
    public void getStatusAuditHistoryCountWrapsSqlFailure() throws Exception {

        connection.close();
        consentHistoryDAO.getStatusAuditHistoryCount(connection, TENANT_DOMAIN, CONSENT_ID);
    }

    @Test(expectedExceptions = ConsentHistoryDataRetrievalException.class)
    public void getConsentHistoryWrapsSqlFailure() throws Exception {

        connection.close();
        consentHistoryDAO.getConsentHistory(connection, TENANT_DOMAIN, CONSENT_ID, 20, 0);
    }

    @Test(expectedExceptions = ConsentHistoryDataRetrievalException.class)
    public void getConsentHistoryCountWrapsSqlFailure() throws Exception {

        connection.close();
        consentHistoryDAO.getConsentHistoryCount(connection, TENANT_DOMAIN, CONSENT_ID);
    }

    private void insertStatusAudit(String previousStatus, String currentStatus, String actionType, long actionTime)
            throws Exception {

        ConsentStatusAuditRecord record = new ConsentStatusAuditRecord();
        record.setAuditId(UUID.randomUUID().toString());
        record.setConsentId(CONSENT_ID);
        record.setOrgId(TENANT_DOMAIN);
        record.setPreviousStatus(previousStatus);
        record.setCurrentStatus(currentStatus);
        record.setActionType(actionType);
        record.setActionBy("jdoe@carbon.super");
        record.setActionTime(actionTime);
        consentHistoryDAO.insertStatusAudit(connection, record);
    }

    private void insertHistorySnapshot(String actionType, String snapshot, long actionTime) throws Exception {

        ConsentHistoryRecord record = new ConsentHistoryRecord();
        record.setHistoryId(UUID.randomUUID().toString());
        record.setConsentId(CONSENT_ID);
        record.setOrgId(TENANT_DOMAIN);
        record.setActionType(actionType);
        record.setSnapshot(snapshot);
        record.setActionBy("jdoe@carbon.super");
        record.setActionTime(actionTime);
        consentHistoryDAO.insertHistorySnapshot(connection, record);
    }
}
