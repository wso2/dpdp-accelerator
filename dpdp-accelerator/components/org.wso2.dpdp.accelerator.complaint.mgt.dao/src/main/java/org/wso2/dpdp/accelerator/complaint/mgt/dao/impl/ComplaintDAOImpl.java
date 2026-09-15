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

package org.wso2.dpdp.accelerator.complaint.mgt.dao.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.dpdp.accelerator.common.util.LogSanitizer;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintDBColumns;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintStatus;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.exception.ComplaintDAOException;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.exception.DuplicateReferenceIdException;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintQueueStats;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.queries.ComplaintQueryBuilder;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.queries.ComplaintQueryFactory;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.queries.QueryResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ComplaintDAOImpl implements ComplaintDAO {

    private static final Log LOG = LogFactory.getLog(ComplaintDAOImpl.class);

    @Override
    public boolean addComplaint(Connection conn, Complaint complaint) {
        try (PreparedStatement ps = conn.prepareStatement(ComplaintQueryFactory.getQueryProvider(conn).getAddComplaintQuery())) {
            ps.setString(1, complaint.getComplaintId());
            ps.setString(2, complaint.getOrgId());
            ps.setString(3, complaint.getUserId());
            ps.setString(4, complaint.getUserName());
            ps.setString(5, complaint.getReferenceId());
            ps.setString(6, complaint.getCategory());
            ps.setString(7, complaint.getPriority());
            ps.setString(8, complaint.getStatus());
            ps.setString(9, complaint.getDescription());
            ps.setLong(10, complaint.getCreatedTime());
            ps.setLong(11, complaint.getUpdatedTime());
            ps.setLong(12, complaint.getStatutoryDueTime());
            return ps.executeUpdate() > 0;
        /*
         * Distinguishes an expected reference-ID collision (retry) from a genuine COMPLAINT_ID
         * collision (real bug) by checking the driver's error message text - the only portable
         * way, since neither driver exposes the violated constraint as a structured field.
         */
        } catch (SQLIntegrityConstraintViolationException e) {

            if (e.getMessage() != null && e.getMessage().toUpperCase(java.util.Locale.ROOT)
                    .contains("UQ_COMPLAINT_REFERENCE")) {
                LOG.warn("Duplicate reference ID for org: " + complaint.getOrgId(), e);
                throw new DuplicateReferenceIdException(e);
            }
            LOG.error("Error adding complaint for org: " + complaint.getOrgId(), e);
            throw new ComplaintDAOException("Error adding complaint for org: " + complaint.getOrgId(), e);
        } catch (SQLException e) {
            LOG.error("Error adding complaint for org: " + complaint.getOrgId(), e);
            throw new ComplaintDAOException("Error adding complaint for org: " + complaint.getOrgId(), e);
        }
    }

    @Override
    public Optional<Complaint> getComplaintById(Connection conn, String complaintId, String orgId) {
        try (PreparedStatement ps = conn.prepareStatement(ComplaintQueryFactory.getQueryProvider(conn).getGetComplaintByIdQuery())) {
            ps.setString(1, complaintId);
            ps.setString(2, orgId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToComplaint(rs));
                }
            }
        } catch (SQLException e) {
            LOG.error("Error getting complaint by ID: " + LogSanitizer.sanitize(complaintId), e);
            throw new ComplaintDAOException("Error getting complaint by ID: " + complaintId, e);
        }
        return Optional.empty();
    }

    @Override
    public int countByReferenceIdPrefix(Connection conn, String orgId, String referenceIdLikePattern) {
        try (PreparedStatement ps = conn.prepareStatement(ComplaintQueryFactory.getQueryProvider(conn).getCountComplaintsForYearPrefixQuery())) {
            ps.setString(1, orgId);
            ps.setString(2, referenceIdLikePattern);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOG.error("Error counting complaints by reference prefix for org: " + orgId, e);
            throw new ComplaintDAOException("Error counting complaints by reference prefix for org: " + orgId, e);
        }
        return 0;
    }

    @Override
    public boolean updateStatus(Connection conn, String complaintId, String orgId, String newStatus,
            long updatedTime) {
        try (PreparedStatement ps = conn.prepareStatement(ComplaintQueryFactory.getQueryProvider(conn).getUpdateComplaintStatusQuery())) {
            ps.setString(1, newStatus);
            ps.setLong(2, updatedTime);
            ps.setString(3, complaintId);
            ps.setString(4, orgId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.error("Error updating status for complaint: " + LogSanitizer.sanitize(complaintId), e);
            throw new ComplaintDAOException("Error updating status for complaint: " + complaintId, e);
        }
    }

    @Override
    public List<Complaint> listComplaints(Connection conn, String orgId, String status, String priority,
            String userId, int limit, int offset, String sort, int[] totalOut) {
        List<Complaint> complaints = new ArrayList<>();

        try {
            ComplaintQueryBuilder builder = new ComplaintQueryBuilder(orgId, ComplaintQueryFactory.getQueryProvider(conn))
                    .setStatus(status)
                    .setPriority(priority)
                    .setUserId(userId)
                    .setSort(sort);
            QueryResult countQuery = builder.buildCountQuery();
            QueryResult selectQuery = builder.buildSelectQuery(limit, offset);

            // countQuery and selectQuery share the same WHERE clause/params: COUNT(*) first for the
            // total (written back via the totalOut out-param), then the LIMIT/OFFSET query for
            // the actual page. Both must run against the same filters so the reported total
            // matches what's actually being paged through.
            try (PreparedStatement countPs = conn.prepareStatement(countQuery.getSql())) {
                List<Object> countParams = countQuery.getParameters();
                for (int i = 0; i < countParams.size(); i++) {
                    countPs.setObject(i + 1, countParams.get(i));
                }
                try (ResultSet countRs = countPs.executeQuery()) {
                    if (countRs.next() && totalOut != null && totalOut.length > 0) {
                        totalOut[0] = countRs.getInt(1);
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(selectQuery.getSql())) {
                List<Object> selectParams = selectQuery.getParameters();
                for (int i = 0; i < selectParams.size(); i++) {
                    ps.setObject(i + 1, selectParams.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        complaints.add(mapResultSetToComplaint(rs));
                    }
                }
            }
        } catch (SQLException e) {
            LOG.error("Error listing complaints for org: " + orgId, e);
            throw new ComplaintDAOException("Error listing complaints for org: " + orgId, e);
        }
        return complaints;
    }

    @Override
    public ComplaintQueueStats getQueueStats(Connection conn, String orgId, long now) {
        int openCount = 0;
        int awaitingInternalReviewCount = 0;
        int resolvedCount = 0;

        try {

            try (PreparedStatement statusPs = conn.prepareStatement(ComplaintQueryFactory.getQueryProvider(conn).getCountComplaintsByStatusQuery())) {
                statusPs.setString(1, orgId);
                try (ResultSet statusRs = statusPs.executeQuery()) {
                    while (statusRs.next()) {
                        String status = statusRs.getString(1);
                        int count = statusRs.getInt(2);
                        if (ComplaintStatus.OPEN.name().equals(status)
                                || ComplaintStatus.IN_PROGRESS.name().equals(status)) {
                            openCount += count;
                        } else if (ComplaintStatus.AWAITING_INTERNAL_REVIEW.name().equals(status)) {
                            awaitingInternalReviewCount += count;
                        } else if (ComplaintStatus.RESOLVED.name().equals(status)) {
                            resolvedCount += count;
                        }
                        // WAITING_ON_CLIENT has no dedicated tile - not counted in any bucket here.
                    }
                }
            }

            try (PreparedStatement breachedPs = conn.prepareStatement(ComplaintQueryFactory.getQueryProvider(conn).getCountSlaBreachedComplaintsQuery())) {
                breachedPs.setString(1, orgId);
                breachedPs.setLong(2, now);
                try (ResultSet breachedRs = breachedPs.executeQuery()) {
                    int slaBreachedCount = breachedRs.next() ? breachedRs.getInt(1) : 0;
                    return new ComplaintQueueStats(openCount, awaitingInternalReviewCount, resolvedCount,
                            slaBreachedCount);
                }
            }
        } catch (SQLException e) {
            LOG.error("Error computing queue stats for org: " + orgId, e);
            throw new ComplaintDAOException("Error computing queue stats for org: " + orgId, e);
        }
    }

    private Complaint mapResultSetToComplaint(ResultSet rs) throws SQLException {
        return new Complaint(
                rs.getString(ComplaintDBColumns.COMPLAINT_ID),
                rs.getString(ComplaintDBColumns.ORG_ID),
                rs.getString(ComplaintDBColumns.USER_ID),
                rs.getString(ComplaintDBColumns.USER_NAME),
                rs.getString(ComplaintDBColumns.REFERENCE_ID),
                rs.getString(ComplaintDBColumns.CATEGORY),
                rs.getString(ComplaintDBColumns.PRIORITY),
                rs.getString(ComplaintDBColumns.STATUS),
                rs.getString(ComplaintDBColumns.DESCRIPTION),
                rs.getLong(ComplaintDBColumns.CREATED_TIME),
                rs.getLong(ComplaintDBColumns.UPDATED_TIME),
                rs.getLong(ComplaintDBColumns.STATUTORY_DUE_TIME));
    }
}
