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

package org.wso2.dpdp.accelerator.consent.extensions.service.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.dpdp.accelerator.common.util.DatabaseUtils;
import org.wso2.dpdp.accelerator.common.util.LogSanitizer;
import org.wso2.dpdp.accelerator.consent.extensions.dao.ConsentHistoryDAO;
import org.wso2.dpdp.accelerator.consent.extensions.dao.constants.ConsentHistoryDAOConstants;
import org.wso2.dpdp.accelerator.consent.extensions.dao.exceptions.ConsentHistoryDataInsertionException;
import org.wso2.dpdp.accelerator.consent.extensions.dao.exceptions.ConsentHistoryDataRetrievalException;
import org.wso2.dpdp.accelerator.consent.extensions.dao.impl.ConsentHistoryDAOImpl;
import org.wso2.dpdp.accelerator.consent.extensions.dao.models.ConsentHistoryRecord;
import org.wso2.dpdp.accelerator.consent.extensions.dao.models.ConsentStatusAuditRecord;
import org.wso2.dpdp.accelerator.consent.extensions.internal.DPDPConsentExtensionDataHolder;
import org.wso2.dpdp.accelerator.consent.extensions.service.ConsentHistoryService;
import org.wso2.dpdp.accelerator.consent.extensions.service.constants.ConsentHistoryServiceConstants.ActionType;
import org.wso2.dpdp.accelerator.consent.extensions.service.models.PagedResult;

import java.sql.Connection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ConsentHistoryServiceImpl implements ConsentHistoryService {

    private static final Log LOG = LogFactory.getLog(ConsentHistoryServiceImpl.class);

    private final ConsentHistoryDAO consentHistoryDAO;
    private final Supplier<Connection> connectionSupplier;
    private final Consumer<Connection> commitAction;
    private final Consumer<Connection> rollbackAction;

    public ConsentHistoryServiceImpl() {

        this(new ConsentHistoryDAOImpl(), DatabaseUtils::getDBConnection, DatabaseUtils::commitTransaction,
                DatabaseUtils::rollbackTransaction);
    }

    /**
     * Lets tests substitute a fake {@link Connection} and no-op commit/rollback, without going
     * through the real, JNDI-backed {@link DatabaseUtils} - the DAO is mocked in those tests
     * anyway, so the connection object itself is never actually used for I/O.
     */
    ConsentHistoryServiceImpl(ConsentHistoryDAO consentHistoryDAO, Supplier<Connection> connectionSupplier,
            Consumer<Connection> commitAction, Consumer<Connection> rollbackAction) {

        this.consentHistoryDAO = consentHistoryDAO;
        this.connectionSupplier = connectionSupplier;
        this.commitAction = commitAction;
        this.rollbackAction = rollbackAction;
    }

    @Override
    public void recordStatusAudit(String tenantDomain, String consentId, String previousStatus,
            String currentStatus, ActionType actionType, String actionBy) {

        // A status-audit row means "the status changed here" - previousStatus and currentStatus
        // being equal (e.g. an UPDATE, which never touches lifecycle status; or one authorizer's
        // approval when others are still pending) isn't a transition, so there is nothing to
        // record. DPDP_CONSENT_HISTORY already captures every action regardless, with full detail,
        // so nothing is lost by skipping a no-op row here.
        if (Objects.equals(previousStatus, currentStatus)) {
            LOG.debug("Skipping a '" + actionType + "' status-audit row for consent: "
                    + LogSanitizer.sanitize(consentId) + " - status did not change ("
                    + LogSanitizer.sanitize(currentStatus) + ").");
            return;
        }

        ConsentStatusAuditRecord record = new ConsentStatusAuditRecord();
        record.setAuditId(UUID.randomUUID().toString());
        record.setConsentId(consentId);
        record.setOrgId(resolveOrgId(tenantDomain));
        record.setPreviousStatus(previousStatus);
        record.setCurrentStatus(currentStatus);
        record.setActionType(actionType.name());
        record.setActionBy(actionBy);
        record.setActionTime(System.currentTimeMillis());

        Connection connection = connectionSupplier.get();
        try {
            try {
                consentHistoryDAO.insertStatusAudit(connection, record);
                commitAction.accept(connection);
                LOG.debug("Recorded a '" + actionType + "' status-audit row for consent: "
                        + LogSanitizer.sanitize(consentId));
            } catch (ConsentHistoryDataInsertionException e) {
                rollbackAction.accept(connection);
                throw e;
            }
        } finally {
            DatabaseUtils.closeConnection(connection);
        }
    }

    @Override
    public void recordHistorySnapshot(String tenantDomain, String consentId, ActionType actionType,
            String snapshotJson, String actionBy) {

        if (!DPDPConsentExtensionDataHolder.getInstance().getConfigurationService().isConsentHistorySnapshotEnabled()) {
            LOG.debug("Consent history snapshot recording is disabled; skipping consent: "
                    + LogSanitizer.sanitize(consentId));
            return;
        }

        ConsentHistoryRecord record = new ConsentHistoryRecord();
        record.setHistoryId(UUID.randomUUID().toString());
        record.setConsentId(consentId);
        record.setOrgId(resolveOrgId(tenantDomain));
        record.setActionType(actionType.name());
        record.setSnapshot(snapshotJson);
        record.setActionBy(actionBy);
        record.setActionTime(System.currentTimeMillis());

        Connection connection = connectionSupplier.get();
        try {
            try {
                consentHistoryDAO.insertHistorySnapshot(connection, record);
                commitAction.accept(connection);
                LOG.debug("Recorded a '" + actionType + "' history snapshot for consent: "
                        + LogSanitizer.sanitize(consentId));
            } catch (ConsentHistoryDataInsertionException e) {
                rollbackAction.accept(connection);
                throw e;
            }
        } finally {
            DatabaseUtils.closeConnection(connection);
        }
    }

    @Override
    public PagedResult<ConsentStatusAuditRecord> getStatusAuditHistory(String tenantDomain, String consentId,
            int limit, int offset) {

        String orgId = resolveOrgId(tenantDomain);
        Connection connection = connectionSupplier.get();
        try {
            try {
                List<ConsentStatusAuditRecord> records = consentHistoryDAO.getStatusAuditHistory(connection, orgId,
                        consentId, limit, offset);
                int totalCount = consentHistoryDAO.getStatusAuditHistoryCount(connection, orgId, consentId);
                commitAction.accept(connection);
                return new PagedResult<>(records, totalCount);
            } catch (ConsentHistoryDataRetrievalException e) {
                rollbackAction.accept(connection);
                throw e;
            }
        } finally {
            DatabaseUtils.closeConnection(connection);
        }
    }

    @Override
    public PagedResult<ConsentHistoryRecord> getConsentHistory(String tenantDomain, String consentId, int limit,
            int offset) {

        String orgId = resolveOrgId(tenantDomain);
        Connection connection = connectionSupplier.get();
        try {
            try {
                List<ConsentHistoryRecord> records = consentHistoryDAO.getConsentHistory(connection, orgId,
                        consentId, limit, offset);
                int totalCount = consentHistoryDAO.getConsentHistoryCount(connection, orgId, consentId);
                commitAction.accept(connection);
                return new PagedResult<>(records, totalCount);
            } catch (ConsentHistoryDataRetrievalException e) {
                rollbackAction.accept(connection);
                throw e;
            }
        } finally {
            DatabaseUtils.closeConnection(connection);
        }
    }

    private String resolveOrgId(String tenantDomain) {

        return tenantDomain == null ? ConsentHistoryDAOConstants.DEFAULT_ORG_ID : tenantDomain;
    }
}
