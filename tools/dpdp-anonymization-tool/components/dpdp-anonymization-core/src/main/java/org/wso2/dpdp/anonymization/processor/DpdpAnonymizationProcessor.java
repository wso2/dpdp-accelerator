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

package org.wso2.dpdp.anonymization.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.dpdp.anonymization.config.EventPayloadRule;
import org.wso2.dpdp.anonymization.config.ToolConfig;
import org.wso2.dpdp.anonymization.database.ConnectionFactory;
import org.wso2.dpdp.anonymization.database.DatabaseDialect;
import org.wso2.dpdp.anonymization.json.JsonPathExpression;
import org.wso2.dpdp.anonymization.json.JsonValueRewriter;
import org.wso2.dpdp.anonymization.model.AnonymizationRequest;
import org.wso2.dpdp.anonymization.model.AnonymizationResult;
import org.wso2.dpdp.anonymization.model.AnonymizationStatus;
import org.wso2.dpdp.anonymization.model.ExecutionMode;
import org.wso2.dpdp.anonymization.model.IdentityEvidence;
import org.wso2.dpdp.anonymization.validation.AnonymizationException;
import org.wso2.dpdp.anonymization.validation.RequestValidator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DpdpAnonymizationProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(DpdpAnonymizationProcessor.class);

    private final ConnectionFactory connectionFactory;
    private final Map<String, List<JsonPathExpression>> eventPaths;
    private final int fetchSize;
    private final JsonValueRewriter jsonRewriter = new JsonValueRewriter();

    public DpdpAnonymizationProcessor(ConnectionFactory connectionFactory, ToolConfig config)
            throws AnonymizationException {
        this.connectionFactory = connectionFactory;
        this.eventPaths = indexEventPaths(config.getEventPayloadRules());
        this.fetchSize = config.getBatchSize();
    }

    public AnonymizationResult process(AnonymizationRequest request) throws AnonymizationException {
        RequestValidator.validate(request);
        Connection connection = null;
        try {
            connection = connectionFactory.getConnection();
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            DatabaseDialect dialect = DatabaseDialect.detect(connection);
            AnonymizationResult result = new AnonymizationResult();
            if (!hasTenantData(connection, request.getTenantDomain())) {
                connection.rollback();
                result.setStatus(AnonymizationStatus.NO_TENANT_DATA);
                return result;
            }
            IdentityEvidence evidence = discoverIdentityEvidence(connection, dialect, request);
            validateNoAmbiguousAliases(connection, dialect, request.getTenantDomain(), evidence);

            long sourceMatches = countMatches(connection, dialect, request.getTenantDomain(), evidence);
            IdentityEvidence replacementEvidence = new IdentityEvidence(request.getPseudonym(),
                    Collections.singleton(request.getPseudonym()));
            long replacementMatches = countMatches(connection, dialect, request.getTenantDomain(), replacementEvidence);

            result.setDiscoveredAliasCount(discoveredAliasCount(request, evidence));
            result.setTrustedUsernames(evidence.getTrustedUsernames());
            if (sourceMatches == 0 && replacementMatches > 0) {
                connection.rollback();
                result.setStatus(AnonymizationStatus.TARGET_PRESENT_SOURCE_ABSENT);
                return result;
            }
            if (sourceMatches == 0) {
                connection.rollback();
                result.setStatus(AnonymizationStatus.NO_MATCH);
                return result;
            }
            if (replacementMatches > 0) {
                throw new AnonymizationException("The replacement UUID is already present in covered fields for this tenant.");
            }

            updateComplaints(connection, dialect, request, evidence, result);
            updateComplaintEvents(connection, dialect, request, evidence, result);
            updateConsentAudit(connection, dialect, request, evidence, result);
            updateConsentHistory(connection, dialect, request, evidence, result);
            updateEvents(connection, dialect, request, evidence, result);

            long remaining = countMatches(connection, dialect, request.getTenantDomain(), evidence);
            if (remaining != 0) {
                throw new AnonymizationException("Post-update verification found " + remaining
                        + " remaining values in covered fields.");
            }
            if (request.getExecutionMode() == ExecutionMode.EXECUTE) {
                connection.commit();
                result.setStatus(AnonymizationStatus.COMMITTED);
            } else {
                connection.rollback();
                result.setStatus(AnonymizationStatus.DRY_RUN);
            }
            return result;
        } catch (SQLException e) {
            rollback(connection);
            throw new AnonymizationException("DPDP anonymization failed and was rolled back.", e);
        } catch (AnonymizationException e) {
            rollback(connection);
            throw e;
        } finally {
            close(connection);
        }
    }

    private int discoveredAliasCount(AnonymizationRequest request, IdentityEvidence evidence) {
        int explicitCount = new IdentityEvidence(request.getSourceUserId(), request.getExplicitUsernames())
                .getTrustedUsernames().size();
        return Math.max(0, evidence.getTrustedUsernames().size() - explicitCount);
    }

    private boolean hasTenantData(Connection connection, String tenant) throws SQLException {
        String[] tables = {"COMPLAINT", "COMPLAINT_EVENT", "DPDP_CONSENT_STATUS_AUDIT",
                "DPDP_CONSENT_HISTORY", "EVENT"};
        for (String table : tables) {
            try (PreparedStatement statement = prepareQuery(connection,
                    "SELECT 1 FROM " + table + " WHERE ORG_ID = ? LIMIT 1")) {
                statement.setString(1, tenant);
                try (ResultSet results = statement.executeQuery()) {
                    if (results.next()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private IdentityEvidence discoverIdentityEvidence(Connection connection, DatabaseDialect dialect,
                                                       AnonymizationRequest request) throws SQLException {
        IdentityEvidence evidence = new IdentityEvidence(request.getSourceUserId(), request.getExplicitUsernames());
        discoverAliases(connection,
                "SELECT USER_NAME FROM COMPLAINT WHERE ORG_ID = ? AND USER_ID = ?" + dialect.forUpdate(),
                request, evidence);
        discoverAliases(connection,
                "SELECT ACTOR_USER_NAME FROM COMPLAINT_EVENT WHERE ORG_ID = ? AND ACTOR_USER_ID = ?"
                        + dialect.forUpdate(), request, evidence);
        return evidence;
    }

    private void discoverAliases(Connection connection, String sql, AnonymizationRequest request,
                                 IdentityEvidence evidence) throws SQLException {
        try (PreparedStatement statement = prepareQuery(connection, sql)) {
            statement.setString(1, request.getTenantDomain());
            statement.setString(2, request.getSourceUserId());
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    evidence.addTrustedUsername(results.getString(1));
                }
            }
        }
    }

    private void validateNoAmbiguousAliases(Connection connection, DatabaseDialect dialect, String tenant,
                                            IdentityEvidence evidence) throws SQLException, AnonymizationException {
        if (evidence.getTrustedUsernames().isEmpty()) {
            return;
        }
        String complaintSql = "SELECT USER_ID, USER_NAME FROM COMPLAINT WHERE ORG_ID = ?" + dialect.forUpdate();
        try (PreparedStatement statement = prepareQuery(connection, complaintSql)) {
            statement.setString(1, tenant);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    String id = results.getString("USER_ID");
                    String username = results.getString("USER_NAME");
                    if (evidence.matchesTrustedUsername(username) && !evidence.matchesIdentifier(id)) {
                        throw new AnonymizationException("A trusted username is associated with a different complaint user ID.");
                    }
                }
            }
        }
        String eventSql = "SELECT ACTOR_USER_ID, ACTOR_USER_NAME FROM COMPLAINT_EVENT WHERE ORG_ID = ?"
                + dialect.forUpdate();
        try (PreparedStatement statement = prepareQuery(connection, eventSql)) {
            statement.setString(1, tenant);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    String id = results.getString("ACTOR_USER_ID");
                    String username = results.getString("ACTOR_USER_NAME");
                    if (id != null && evidence.matchesTrustedUsername(username) && !evidence.matchesIdentifier(id)) {
                        throw new AnonymizationException(
                                "A trusted username is associated with a different complaint-event actor ID.");
                    }
                }
            }
        }
    }

    private long countMatches(Connection connection, DatabaseDialect dialect, String tenant,
                              IdentityEvidence evidence) throws SQLException, AnonymizationException {
        long count = 0;
        count += countComplaintMatches(connection, dialect, tenant, evidence);
        count += countComplaintEventMatches(connection, dialect, tenant, evidence);
        count += countColumnMatches(connection, dialect, "DPDP_CONSENT_STATUS_AUDIT", "ACTION_BY", tenant, evidence);
        count += countConsentHistoryMatches(connection, dialect, tenant, evidence);
        count += countEventMatches(connection, dialect, tenant, evidence);
        return count;
    }

    private long countComplaintMatches(Connection connection, DatabaseDialect dialect, String tenant,
                                       IdentityEvidence evidence) throws SQLException {
        long count = 0;
        String sql = "SELECT USER_ID, USER_NAME FROM COMPLAINT WHERE ORG_ID = ?" + dialect.forUpdate();
        try (PreparedStatement statement = prepareQuery(connection, sql)) {
            statement.setString(1, tenant);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    if (evidence.matchesIdentifier(results.getString("USER_ID"))) {
                        count++;
                    } else if (evidence.matchesTrustedUsername(results.getString("USER_NAME"))) {
                        // A username alone is collision evidence but never authorizes replacing a different ID.
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private long countComplaintEventMatches(Connection connection, DatabaseDialect dialect, String tenant,
                                            IdentityEvidence evidence) throws SQLException {
        long count = 0;
        String sql = "SELECT ACTOR_USER_ID, ACTOR_USER_NAME FROM COMPLAINT_EVENT WHERE ORG_ID = ?"
                + dialect.forUpdate();
        try (PreparedStatement statement = prepareQuery(connection, sql)) {
            statement.setString(1, tenant);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    if (evidence.matchesIdentifier(results.getString("ACTOR_USER_ID"))) {
                        count++;
                    } else if (evidence.matchesTrustedUsername(results.getString("ACTOR_USER_NAME"))) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private long countColumnMatches(Connection connection, DatabaseDialect dialect, String table, String column,
                                    String tenant, IdentityEvidence evidence) throws SQLException {
        long count = 0;
        String sql = "SELECT " + column + " FROM " + table + " WHERE ORG_ID = ?" + dialect.forUpdate();
        try (PreparedStatement statement = prepareQuery(connection, sql)) {
            statement.setString(1, tenant);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    if (evidence.matchesIdentifier(results.getString(1))) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private long countConsentHistoryMatches(Connection connection, DatabaseDialect dialect, String tenant,
                                            IdentityEvidence evidence) throws SQLException, AnonymizationException {
        long count = 0;
        String sql = "SELECT SNAPSHOT, ACTION_BY FROM DPDP_CONSENT_HISTORY WHERE ORG_ID = ?" + dialect.forUpdate();
        try (PreparedStatement statement = prepareQuery(connection, sql)) {
            statement.setString(1, tenant);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    String actionBy = results.getString("ACTION_BY");
                    if (evidence.matchesIdentifier(actionBy)) {
                        count++;
                    }
                    String snapshot = results.getString("SNAPSHOT");
                    if (snapshot != null) {
                        count += jsonRewriter.rewriteConsentSnapshot(snapshot, evidence, evidence.getSourceUserId())
                                .getReplacementCount();
                    }
                }
            }
        }
        return count;
    }

    private long countEventMatches(Connection connection, DatabaseDialect dialect, String tenant,
                                   IdentityEvidence evidence) throws SQLException, AnonymizationException {
        long count = 0;
        String sql = "SELECT E.PAYLOAD, T.NAME FROM EVENT E JOIN TOPIC T ON T.TOPIC_ID = E.TOPIC_ID "
                + "AND T.ORG_ID = E.ORG_ID WHERE E.ORG_ID = ?" + dialect.forUpdate();
        try (PreparedStatement statement = prepareQuery(connection, sql)) {
            statement.setString(1, tenant);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    List<JsonPathExpression> paths = eventPaths.get(results.getString("NAME"));
                    if (paths != null) {
                        count += jsonRewriter.rewriteEventPayload(results.getString("PAYLOAD"), evidence,
                                evidence.getSourceUserId(), paths).getReplacementCount();
                    }
                }
            }
        }
        return count;
    }

    private void updateComplaints(Connection connection, DatabaseDialect dialect, AnonymizationRequest request,
                                  IdentityEvidence evidence, AnonymizationResult result) throws SQLException {
        String select = "SELECT COMPLAINT_ID, USER_ID, USER_NAME FROM COMPLAINT WHERE ORG_ID = ?"
                + dialect.forUpdate();
        List<String> ids = new ArrayList<>();
        try (PreparedStatement statement = prepareQuery(connection, select)) {
            statement.setString(1, request.getTenantDomain());
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    if (evidence.matchesIdentifier(results.getString("USER_ID"))) {
                        ids.add(results.getString("COMPLAINT_ID"));
                    }
                }
            }
        }
        String update = "UPDATE COMPLAINT SET USER_ID = ?, USER_NAME = ? WHERE COMPLAINT_ID = ? AND ORG_ID = ?";
        for (String id : ids) {
            executeUpdate(connection, update, request.getPseudonym(), request.getPseudonym(), id,
                    request.getTenantDomain());
            result.increment("complaint.rows");
        }
    }

    private void updateComplaintEvents(Connection connection, DatabaseDialect dialect, AnonymizationRequest request,
                                       IdentityEvidence evidence, AnonymizationResult result) throws SQLException {
        String select = "SELECT COMPLAINT_EVENT_ID, ACTOR_USER_ID, ACTOR_USER_NAME FROM COMPLAINT_EVENT WHERE ORG_ID = ?"
                + dialect.forUpdate();
        List<ComplaintEventChange> changes = new ArrayList<>();
        try (PreparedStatement statement = prepareQuery(connection, select)) {
            statement.setString(1, request.getTenantDomain());
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    String actorId = results.getString("ACTOR_USER_ID");
                    String actorName = results.getString("ACTOR_USER_NAME");
                    if (evidence.matchesIdentifier(actorId)) {
                        changes.add(new ComplaintEventChange(results.getString("COMPLAINT_EVENT_ID"), true));
                    } else if (actorId == null && evidence.matchesTrustedUsername(actorName)) {
                        changes.add(new ComplaintEventChange(results.getString("COMPLAINT_EVENT_ID"), false));
                    }
                }
            }
        }
        for (ComplaintEventChange change : changes) {
            String update = change.replaceId
                    ? "UPDATE COMPLAINT_EVENT SET ACTOR_USER_ID = ?, ACTOR_USER_NAME = ? "
                        + "WHERE COMPLAINT_EVENT_ID = ? AND ORG_ID = ?"
                    : "UPDATE COMPLAINT_EVENT SET ACTOR_USER_NAME = ? WHERE COMPLAINT_EVENT_ID = ? AND ORG_ID = ?";
            if (change.replaceId) {
                executeUpdate(connection, update, request.getPseudonym(), request.getPseudonym(), change.id,
                        request.getTenantDomain());
            } else {
                executeUpdate(connection, update, request.getPseudonym(), change.id, request.getTenantDomain());
            }
            result.increment("complaintEvent.rows");
        }
    }

    private void updateConsentAudit(Connection connection, DatabaseDialect dialect, AnonymizationRequest request,
                                    IdentityEvidence evidence, AnonymizationResult result) throws SQLException {
        String select = "SELECT AUDIT_ID, ACTION_BY FROM DPDP_CONSENT_STATUS_AUDIT WHERE ORG_ID = ?"
                + dialect.forUpdate();
        List<String> ids = new ArrayList<>();
        try (PreparedStatement statement = prepareQuery(connection, select)) {
            statement.setString(1, request.getTenantDomain());
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    if (evidence.matchesIdentifier(results.getString("ACTION_BY"))) {
                        ids.add(results.getString("AUDIT_ID"));
                    }
                }
            }
        }
        String update = "UPDATE DPDP_CONSENT_STATUS_AUDIT SET ACTION_BY = ? WHERE AUDIT_ID = ? AND ORG_ID = ?";
        for (String id : ids) {
            executeUpdate(connection, update, request.getPseudonym(), id, request.getTenantDomain());
            result.increment("consentAudit.rows");
        }
    }

    private void updateConsentHistory(Connection connection, DatabaseDialect dialect, AnonymizationRequest request,
                                      IdentityEvidence evidence, AnonymizationResult result)
            throws SQLException, AnonymizationException {
        String select = "SELECT HISTORY_ID, SNAPSHOT, ACTION_BY FROM DPDP_CONSENT_HISTORY WHERE ORG_ID = ?"
                + dialect.forUpdate();
        List<HistoryChange> changes = new ArrayList<>();
        try (PreparedStatement statement = prepareQuery(connection, select)) {
            statement.setString(1, request.getTenantDomain());
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    String snapshot = results.getString("SNAPSHOT");
                    JsonValueRewriter.RewriteResult rewritten = snapshot == null
                            ? new JsonValueRewriter.RewriteResult(null, 0)
                            : jsonRewriter.rewriteConsentSnapshot(snapshot, evidence, request.getPseudonym());
                    boolean actionChanged = evidence.matchesIdentifier(results.getString("ACTION_BY"));
                    if (rewritten.getReplacementCount() > 0 || actionChanged) {
                        changes.add(new HistoryChange(results.getString("HISTORY_ID"), rewritten.getJson(), actionChanged,
                                rewritten.getReplacementCount()));
                    }
                }
            }
        }
        String update = "UPDATE DPDP_CONSENT_HISTORY SET SNAPSHOT = ?, ACTION_BY = ? WHERE HISTORY_ID = ? AND ORG_ID = ?";
        for (HistoryChange change : changes) {
            try (PreparedStatement statement = connection.prepareStatement(update)) {
                statement.setString(1, change.snapshot);
                if (change.actionChanged) {
                    statement.setString(2, request.getPseudonym());
                } else {
                    String actionBy = loadHistoryActionBy(connection, change.id, request.getTenantDomain());
                    statement.setString(2, actionBy);
                }
                statement.setString(3, change.id);
                statement.setString(4, request.getTenantDomain());
                requireSingleUpdate(statement);
            }
            result.increment("consentHistory.rows");
            for (int i = 0; i < change.jsonReplacementCount; i++) {
                result.increment("consentHistory.jsonValues");
            }
        }
    }

    private String loadHistoryActionBy(Connection connection, String id, String tenant) throws SQLException {
        try (PreparedStatement statement = prepareQuery(connection,
                "SELECT ACTION_BY FROM DPDP_CONSENT_HISTORY WHERE HISTORY_ID = ? AND ORG_ID = ?")) {
            statement.setString(1, id);
            statement.setString(2, tenant);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Consent history row disappeared during anonymization.");
                }
                return result.getString(1);
            }
        }
    }

    private void updateEvents(Connection connection, DatabaseDialect dialect, AnonymizationRequest request,
                              IdentityEvidence evidence, AnonymizationResult result)
            throws SQLException, AnonymizationException {
        String select = "SELECT E.EVENT_ID, E.PAYLOAD, T.NAME FROM EVENT E JOIN TOPIC T ON T.TOPIC_ID = E.TOPIC_ID "
                + "AND T.ORG_ID = E.ORG_ID WHERE E.ORG_ID = ?" + dialect.forUpdate();
        List<EventChange> changes = new ArrayList<>();
        try (PreparedStatement statement = prepareQuery(connection, select)) {
            statement.setString(1, request.getTenantDomain());
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    String topic = results.getString("NAME");
                    List<JsonPathExpression> paths = eventPaths.get(topic);
                    if (paths == null) {
                        result.increment("event.unconfiguredTopics");
                        continue;
                    }
                    JsonValueRewriter.RewriteResult rewritten = jsonRewriter.rewriteEventPayload(
                            results.getString("PAYLOAD"), evidence, request.getPseudonym(), paths);
                    if (rewritten.getReplacementCount() > 0) {
                        changes.add(new EventChange(results.getString("EVENT_ID"), rewritten.getJson(),
                                rewritten.getReplacementCount()));
                    }
                }
            }
        }
        String update = "UPDATE EVENT SET PAYLOAD = ? WHERE EVENT_ID = ? AND ORG_ID = ?";
        for (EventChange change : changes) {
            executeUpdate(connection, update, change.payload, change.id, request.getTenantDomain());
            result.increment("event.rows");
            for (int i = 0; i < change.jsonReplacementCount; i++) {
                result.increment("event.jsonValues");
            }
        }
    }

    private static void executeUpdate(Connection connection, String sql, String... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                statement.setString(i + 1, values[i]);
            }
            requireSingleUpdate(statement);
        }
    }

    private PreparedStatement prepareQuery(Connection connection, String sql) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setFetchSize(fetchSize);
        return statement;
    }

    private static void requireSingleUpdate(PreparedStatement statement) throws SQLException {
        int updated = statement.executeUpdate();
        if (updated != 1) {
            throw new SQLException("Expected to update exactly one locked row but updated " + updated + '.');
        }
    }

    private static Map<String, List<JsonPathExpression>> indexEventPaths(List<EventPayloadRule> rules)
            throws AnonymizationException {
        Map<String, List<JsonPathExpression>> indexed = new HashMap<>();
        for (EventPayloadRule rule : rules) {
            List<JsonPathExpression> paths = indexed.get(rule.getTopic());
            if (paths == null) {
                paths = new ArrayList<>();
                indexed.put(rule.getTopic(), paths);
            }
            for (String path : rule.getPaths()) {
                paths.add(JsonPathExpression.parse(path));
            }
        }
        return indexed;
    }

    private static void rollback(Connection connection) {
        if (connection != null) {
            try {
                connection.rollback();
            } catch (SQLException e) {
                LOG.error("Could not roll back the DPDP anonymization transaction.", e);
            }
        }
    }

    private static void close(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                LOG.warn("Could not close the DPDP database connection.", e);
            }
        }
    }

    private static final class HistoryChange {
        private final String id;
        private final String snapshot;
        private final boolean actionChanged;
        private final int jsonReplacementCount;

        private HistoryChange(String id, String snapshot, boolean actionChanged, int jsonReplacementCount) {
            this.id = id;
            this.snapshot = snapshot;
            this.actionChanged = actionChanged;
            this.jsonReplacementCount = jsonReplacementCount;
        }
    }

    private static final class EventChange {
        private final String id;
        private final String payload;
        private final int jsonReplacementCount;

        private EventChange(String id, String payload, int jsonReplacementCount) {
            this.id = id;
            this.payload = payload;
            this.jsonReplacementCount = jsonReplacementCount;
        }
    }

    private static final class ComplaintEventChange {
        private final String id;
        private final boolean replaceId;

        private ComplaintEventChange(String id, boolean replaceId) {
            this.id = id;
            this.replaceId = replaceId;
        }
    }
}
