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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.dpdp.accelerator.common.util.LogSanitizer;
import org.wso2.dpdp.accelerator.consent.extensions.dao.ConsentHistoryDAO;
import org.wso2.dpdp.accelerator.consent.extensions.dao.constants.ConsentHistoryDBColumns;
import org.wso2.dpdp.accelerator.consent.extensions.dao.exceptions.ConsentHistoryDataInsertionException;
import org.wso2.dpdp.accelerator.consent.extensions.dao.exceptions.ConsentHistoryDataRetrievalException;
import org.wso2.dpdp.accelerator.consent.extensions.dao.models.ConsentHistoryRecord;
import org.wso2.dpdp.accelerator.consent.extensions.dao.models.ConsentStatusAuditRecord;
import org.wso2.dpdp.accelerator.consent.extensions.dao.queries.ConsentHistoryQueryFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ConsentHistoryDAOImpl implements ConsentHistoryDAO {

    private static final Log LOG = LogFactory.getLog(ConsentHistoryDAOImpl.class);

    @Override
    public void insertStatusAudit(Connection connection, ConsentStatusAuditRecord record) {

        try (PreparedStatement statement = connection
                .prepareStatement(ConsentHistoryQueryFactory.getQueryProvider(connection)
                        .getInsertStatusAuditQuery())) {
            statement.setString(1, record.getAuditId());
            statement.setString(2, record.getConsentId());
            statement.setString(3, record.getOrgId());
            statement.setString(4, record.getPreviousStatus());
            statement.setString(5, record.getCurrentStatus());
            statement.setString(6, record.getActionType());
            statement.setString(7, record.getActionBy());
            statement.setLong(8, record.getActionTime());
            statement.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Error while inserting a status-audit row for consent: "
                    + LogSanitizer.sanitize(record.getConsentId()), e);
            throw new ConsentHistoryDataInsertionException(
                    "Error while inserting a status-audit row for consent: " + record.getConsentId(), e);
        }
    }

    @Override
    public void insertHistorySnapshot(Connection connection, ConsentHistoryRecord record) {

        try (PreparedStatement statement = connection
                .prepareStatement(ConsentHistoryQueryFactory.getQueryProvider(connection)
                        .getInsertHistorySnapshotQuery())) {
            statement.setString(1, record.getHistoryId());
            statement.setString(2, record.getConsentId());
            statement.setString(3, record.getOrgId());
            statement.setString(4, record.getActionType());
            statement.setString(5, record.getSnapshot());
            statement.setString(6, record.getActionBy());
            statement.setLong(7, record.getActionTime());
            statement.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Error while inserting a history snapshot row for consent: "
                    + LogSanitizer.sanitize(record.getConsentId()), e);
            throw new ConsentHistoryDataInsertionException(
                    "Error while inserting a history snapshot row for consent: " + record.getConsentId(), e);
        }
    }

    @Override
    public List<ConsentStatusAuditRecord> getStatusAuditHistory(Connection connection, String orgId,
            String consentId, int limit, int offset) {

        List<ConsentStatusAuditRecord> records = new ArrayList<>();
        try (PreparedStatement statement = connection
                .prepareStatement(ConsentHistoryQueryFactory.getQueryProvider(connection)
                        .getStatusAuditHistoryQuery())) {
            statement.setString(1, consentId);
            statement.setString(2, orgId);
            statement.setInt(3, limit);
            statement.setInt(4, offset);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    records.add(mapStatusAuditRecord(resultSet));
                }
            }
        } catch (SQLException e) {
            LOG.error("Error while retrieving the status-audit history for consent: "
                    + LogSanitizer.sanitize(consentId), e);
            throw new ConsentHistoryDataRetrievalException(
                    "Error while retrieving the status-audit history for consent: " + consentId, e);
        }
        return records;
    }

    @Override
    public int getStatusAuditHistoryCount(Connection connection, String orgId, String consentId) {

        return getCount(connection,
                ConsentHistoryQueryFactory.getQueryProvider(connection).getStatusAuditHistoryCountQuery(), orgId,
                consentId);
    }

    @Override
    public List<ConsentHistoryRecord> getConsentHistory(Connection connection, String orgId, String consentId,
            int limit, int offset) {

        List<ConsentHistoryRecord> records = new ArrayList<>();
        try (PreparedStatement statement = connection
                .prepareStatement(ConsentHistoryQueryFactory.getQueryProvider(connection)
                        .getConsentHistoryQuery())) {
            statement.setString(1, consentId);
            statement.setString(2, orgId);
            statement.setInt(3, limit);
            statement.setInt(4, offset);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    records.add(mapHistoryRecord(resultSet));
                }
            }
        } catch (SQLException e) {
            LOG.error("Error while retrieving the history for consent: " + LogSanitizer.sanitize(consentId), e);
            throw new ConsentHistoryDataRetrievalException(
                    "Error while retrieving the history for consent: " + consentId, e);
        }
        return records;
    }

    @Override
    public int getConsentHistoryCount(Connection connection, String orgId, String consentId) {

        return getCount(connection,
                ConsentHistoryQueryFactory.getQueryProvider(connection).getConsentHistoryCountQuery(), orgId,
                consentId);
    }

    private int getCount(Connection connection, String countQuery, String orgId, String consentId) {

        try (PreparedStatement statement = connection.prepareStatement(countQuery)) {
            statement.setString(1, consentId);
            statement.setString(2, orgId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(ConsentHistoryDBColumns.COLUMN_TOTAL_COUNT) : 0;
            }
        } catch (SQLException e) {
            LOG.error("Error while counting history rows for consent: " + LogSanitizer.sanitize(consentId), e);
            throw new ConsentHistoryDataRetrievalException(
                    "Error while counting history rows for consent: " + consentId, e);
        }
    }

    private ConsentStatusAuditRecord mapStatusAuditRecord(ResultSet resultSet) throws SQLException {

        ConsentStatusAuditRecord record = new ConsentStatusAuditRecord();
        record.setAuditId(resultSet.getString(ConsentHistoryDBColumns.COLUMN_AUDIT_ID));
        record.setConsentId(resultSet.getString(ConsentHistoryDBColumns.COLUMN_CONSENT_ID));
        record.setOrgId(resultSet.getString(ConsentHistoryDBColumns.COLUMN_ORG_ID));
        record.setPreviousStatus(resultSet.getString(ConsentHistoryDBColumns.COLUMN_PREVIOUS_STATUS));
        record.setCurrentStatus(resultSet.getString(ConsentHistoryDBColumns.COLUMN_CURRENT_STATUS));
        record.setActionType(resultSet.getString(ConsentHistoryDBColumns.COLUMN_ACTION_TYPE));
        record.setActionBy(resultSet.getString(ConsentHistoryDBColumns.COLUMN_ACTION_BY));
        record.setActionTime(resultSet.getLong(ConsentHistoryDBColumns.COLUMN_ACTION_TIME));
        return record;
    }

    private ConsentHistoryRecord mapHistoryRecord(ResultSet resultSet) throws SQLException {

        ConsentHistoryRecord record = new ConsentHistoryRecord();
        record.setHistoryId(resultSet.getString(ConsentHistoryDBColumns.COLUMN_HISTORY_ID));
        record.setConsentId(resultSet.getString(ConsentHistoryDBColumns.COLUMN_CONSENT_ID));
        record.setOrgId(resultSet.getString(ConsentHistoryDBColumns.COLUMN_ORG_ID));
        record.setActionType(resultSet.getString(ConsentHistoryDBColumns.COLUMN_ACTION_TYPE));
        record.setSnapshot(resultSet.getString(ConsentHistoryDBColumns.COLUMN_SNAPSHOT));
        record.setActionBy(resultSet.getString(ConsentHistoryDBColumns.COLUMN_ACTION_BY));
        record.setActionTime(resultSet.getLong(ConsentHistoryDBColumns.COLUMN_ACTION_TIME));
        return record;
    }
}
