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

package org.wso2.dpdp.anonymization.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.wso2.dpdp.anonymization.config.EventPayloadRule;
import org.wso2.dpdp.anonymization.config.ToolConfig;
import org.wso2.dpdp.anonymization.database.ConnectionFactory;
import org.wso2.dpdp.anonymization.model.AnonymizationRequest;
import org.wso2.dpdp.anonymization.model.AnonymizationResult;
import org.wso2.dpdp.anonymization.model.AnonymizationStatus;
import org.wso2.dpdp.anonymization.model.ExecutionMode;
import org.wso2.dpdp.anonymization.processor.DpdpAnonymizationProcessor;
import org.wso2.dpdp.anonymization.validation.AnonymizationException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class H2AnonymizationIntegrationTest {

    private static final String SOURCE = "18b7c17d-ef74-48c5-a0c8-a1df9b21ff87";
    private static final String TARGET = "216d6aac-7e84-4484-a71e-c52f89b3cb1d";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private String url;
    private DpdpAnonymizationProcessor processor;

    @BeforeEach
    void setUp() throws Exception {
        url = "jdbc:h2:mem:dpdp_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        Class.forName("org.h2.Driver");
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            createSchema(statement);
            seed(statement);
        }
        EventPayloadRule rule = new EventPayloadRule();
        rule.setTopic("user.data.change");
        rule.setPaths(Collections.singletonList("/data/userId"));
        ToolConfig config = new ToolConfig();
        config.setEventPayloadRules(Collections.singletonList(rule));
        ConnectionFactory factory = () -> DriverManager.getConnection(url, "sa", "");
        processor = new DpdpAnonymizationProcessor(factory, config);
    }

    @Test
    void dryRunRollsBackEveryCoveredField() throws Exception {
        AnonymizationResult result = processor.process(request(ExecutionMode.DRY_RUN));
        assertEquals(AnonymizationStatus.DRY_RUN, result.getStatus());
        assertEquals(SOURCE, scalar("SELECT USER_ID FROM COMPLAINT WHERE ORG_ID='example.com'"));
        assertEquals(SOURCE, jsonScalar("SELECT PAYLOAD FROM EVENT WHERE ORG_ID='example.com'", "/data/userId"));
    }

    @Test
    void executeIsTenantScopedAndSecondRunIsIdempotent() throws Exception {
        AnonymizationResult result = processor.process(request(ExecutionMode.EXECUTE));
        assertEquals(AnonymizationStatus.COMMITTED, result.getStatus());
        assertEquals(TARGET, scalar("SELECT USER_ID FROM COMPLAINT WHERE ORG_ID='example.com'"));
        assertEquals(TARGET, scalar("SELECT ACTOR_USER_ID FROM COMPLAINT_EVENT WHERE ORG_ID='example.com'"));
        assertEquals(TARGET, scalar("SELECT ACTION_BY FROM DPDP_CONSENT_STATUS_AUDIT WHERE ORG_ID='example.com'"));
        assertEquals(TARGET, jsonScalar("SELECT SNAPSHOT FROM DPDP_CONSENT_HISTORY WHERE ORG_ID='example.com'", "/piiPrincipalId"));
        assertEquals(TARGET, jsonScalar("SELECT PAYLOAD FROM EVENT WHERE ORG_ID='example.com'", "/data/userId"));
        assertEquals(SOURCE, scalar("SELECT USER_ID FROM COMPLAINT WHERE ORG_ID='other.com'"));

        AnonymizationResult second = processor.process(request(ExecutionMode.EXECUTE));
        assertEquals(AnonymizationStatus.TARGET_PRESENT_SOURCE_ABSENT, second.getStatus());
    }

    @Test
    void trustedUsernameCannotOverwriteDifferentNonNullId() throws Exception {
        try (Connection connection = DriverManager.getConnection(url, "sa", ""); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO COMPLAINT VALUES ('c3','example.com','aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa','shared','r3','cat','LOW','OPEN','d',1,1,1)");
        }
        AnonymizationRequest request = new AnonymizationRequest("example.com", SOURCE, TARGET,
                new LinkedHashSet<>(Collections.singletonList("shared")), ExecutionMode.EXECUTE);
        assertThrows(AnonymizationException.class, () -> processor.process(request));
        assertEquals("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                scalar("SELECT USER_ID FROM COMPLAINT WHERE COMPLAINT_ID='c3'"));
        assertEquals(SOURCE, scalar("SELECT USER_ID FROM COMPLAINT WHERE COMPLAINT_ID='c1'"));
    }

    @Test
    void malformedJsonRollsBack() throws Exception {
        try (Connection connection = DriverManager.getConnection(url, "sa", ""); Statement statement = connection.createStatement()) {
            statement.execute("UPDATE DPDP_CONSENT_HISTORY SET SNAPSHOT='{broken' WHERE HISTORY_ID='h1'");
        }
        assertThrows(AnonymizationException.class, () -> processor.process(request(ExecutionMode.EXECUTE)));
        assertEquals(SOURCE, scalar("SELECT USER_ID FROM COMPLAINT WHERE ORG_ID='example.com'"));
    }

    @Test
    void distinguishesTenantWithoutDpdpData() throws Exception {
        AnonymizationRequest request = new AnonymizationRequest("empty.example", SOURCE, TARGET,
                Collections.<String>emptySet(), ExecutionMode.DRY_RUN);
        assertEquals(AnonymizationStatus.NO_TENANT_DATA, processor.process(request).getStatus());
    }

    private AnonymizationRequest request(ExecutionMode mode) {
        return new AnonymizationRequest("example.com", SOURCE, TARGET, Collections.<String>emptySet(), mode);
    }

    private String scalar(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }

    private String jsonScalar(String sql, String pointer) throws Exception {
        JsonNode value = OBJECT_MAPPER.readTree(scalar(sql)).at(pointer);
        return value.asText();
    }

    private void createSchema(Statement statement) throws Exception {
        statement.execute("CREATE TABLE COMPLAINT (COMPLAINT_ID VARCHAR(36), ORG_ID VARCHAR(255), USER_ID VARCHAR(255), USER_NAME VARCHAR(255), REFERENCE_ID VARCHAR(32), CATEGORY VARCHAR(64), PRIORITY VARCHAR(16), STATUS VARCHAR(32), DESCRIPTION CLOB, CREATED_TIME BIGINT, UPDATED_TIME BIGINT, STATUTORY_DUE_TIME BIGINT, PRIMARY KEY(COMPLAINT_ID, ORG_ID))");
        statement.execute("CREATE TABLE COMPLAINT_EVENT (COMPLAINT_EVENT_ID VARCHAR(36), ORG_ID VARCHAR(255), COMPLAINT_ID VARCHAR(36), ACTOR_USER_ID VARCHAR(255), ACTOR_USER_NAME VARCHAR(255), PRIMARY KEY(COMPLAINT_EVENT_ID, ORG_ID))");
        statement.execute("CREATE TABLE DPDP_CONSENT_STATUS_AUDIT (AUDIT_ID VARCHAR(36) PRIMARY KEY, ORG_ID VARCHAR(255), ACTION_BY VARCHAR(255))");
        statement.execute("CREATE TABLE DPDP_CONSENT_HISTORY (HISTORY_ID VARCHAR(36) PRIMARY KEY, ORG_ID VARCHAR(255), SNAPSHOT CLOB, ACTION_BY VARCHAR(255))");
        statement.execute("CREATE TABLE TOPIC (TOPIC_ID VARCHAR(64) PRIMARY KEY, ORG_ID VARCHAR(128), NAME VARCHAR(225))");
        statement.execute("CREATE TABLE EVENT (EVENT_ID VARCHAR(64) PRIMARY KEY, ORG_ID VARCHAR(128), TOPIC_ID VARCHAR(64), PAYLOAD CLOB)");
    }

    private void seed(Statement statement) throws Exception {
        statement.execute("INSERT INTO COMPLAINT VALUES ('c1','example.com','" + SOURCE + "','alice','r1','cat','LOW','OPEN','d',1,1,1)");
        statement.execute("INSERT INTO COMPLAINT VALUES ('c2','other.com','" + SOURCE + "','alice','r2','cat','LOW','OPEN','d',1,1,1)");
        statement.execute("INSERT INTO COMPLAINT_EVENT VALUES ('ce1','example.com','c1','" + SOURCE + "','alice')");
        statement.execute("INSERT INTO DPDP_CONSENT_STATUS_AUDIT VALUES ('a1','example.com','alice')");
        statement.execute("INSERT INTO DPDP_CONSENT_HISTORY VALUES ('h1','example.com','{\"piiPrincipalId\":\"" + SOURCE + "\",\"authorizations\":[{\"userId\":\"" + SOURCE + "\"}]}','alice')");
        statement.execute("INSERT INTO TOPIC VALUES ('t1','example.com','user.data.change')");
        statement.execute("INSERT INTO TOPIC VALUES ('t2','other.com','user.data.change')");
        statement.execute("INSERT INTO EVENT VALUES ('e1','example.com','t1','{\"data\":{\"userId\":\"" + SOURCE + "\"},\"note\":\"keep " + SOURCE + "\"}')");
        statement.execute("INSERT INTO EVENT VALUES ('e2','other.com','t2','{\"data\":{\"userId\":\"" + SOURCE + "\"}}')");
    }
}
