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

import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.DAOConstants;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.exception.ComplaintDAOException;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.exception.DuplicateReferenceIdException;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.queries.QueryConstants;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ComplaintDAOImpl implements ComplaintDAO {

    private static final Logger LOGGER = Logger.getLogger(ComplaintDAOImpl.class.getName());

    @Override
    public boolean addComplaint(Complaint complaint) {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(QueryConstants.ADD_COMPLAINT);
            ps.setString(1, complaint.getComplaintId());
            ps.setString(2, complaint.getOrgId());
            ps.setString(3, complaint.getUserId());
            ps.setString(4, complaint.getReferenceId());
            ps.setString(5, complaint.getCategory());
            ps.setString(6, complaint.getPriority());
            ps.setString(7, complaint.getStatus());
            ps.setString(8, complaint.getDescription());
            ps.setLong(9, complaint.getCreatedTime());
            ps.setLong(10, complaint.getUpdatedTime());
            ps.setLong(11, complaint.getStatutoryDueTime());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            if (isReferenceIdConflict(e)) {
                throw new DuplicateReferenceIdException(e);
            }
            LOGGER.log(Level.SEVERE, "Error adding complaint for org: " + complaint.getOrgId(), e);
            throw new ComplaintDAOException("Error adding complaint for org: " + complaint.getOrgId(), e);
        } finally {
            DBUtil.closeAll(conn, ps, null);
        }
    }

    /**
     * Distinguishes the (ORG_ID, REFERENCE_ID) unique-constraint violation - which the caller is
     * expected to retry with a freshly generated reference ID - from any other integrity violation
     * (e.g. a COMPLAINT_ID primary-key clash), which should keep surfacing as a generic DAO error.
     */
    private static boolean isReferenceIdConflict(SQLException e) {
        return e instanceof SQLIntegrityConstraintViolationException
                && e.getMessage() != null
                && e.getMessage().contains(DAOConstants.CONSTRAINT_UQ_COMPLAINT_REFERENCE);
    }

    @Override
    public Optional<Complaint> getComplaintById(String complaintId, String orgId) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(QueryConstants.GET_COMPLAINT_BY_ID);
            ps.setString(1, complaintId);
            ps.setString(2, orgId);
            rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(mapResultSetToComplaint(rs));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error getting complaint by ID: " + complaintId, e);
            throw new ComplaintDAOException("Error getting complaint by ID: " + complaintId, e);
        } finally {
            DBUtil.closeAll(conn, ps, rs);
        }
        return Optional.empty();
    }

    @Override
    public int countByReferenceIdPrefix(String orgId, String referenceIdLikePattern) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(QueryConstants.COUNT_COMPLAINTS_FOR_YEAR_PREFIX);
            ps.setString(1, orgId);
            ps.setString(2, referenceIdLikePattern);
            rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error counting complaints by reference prefix for org: " + orgId, e);
            throw new ComplaintDAOException("Error counting complaints by reference prefix for org: " + orgId, e);
        } finally {
            DBUtil.closeAll(conn, ps, rs);
        }
        return 0;
    }

    @Override
    public boolean updateStatus(String complaintId, String orgId, String newStatus, long updatedTime) {
        try (Connection conn = DBUtil.getConnection()) {
            return updateStatus(conn, complaintId, orgId, newStatus, updatedTime);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error updating status for complaint: " + complaintId, e);
        }
        return false;
    }

    /** Overload for callers composing this write into a caller-owned {@link DBUtil#executeInTransaction}. */
    public boolean updateStatus(Connection conn, String complaintId, String orgId, String newStatus,
            long updatedTime) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(QueryConstants.UPDATE_COMPLAINT_STATUS)) {
            ps.setString(1, newStatus);
            ps.setLong(2, updatedTime);
            ps.setString(3, complaintId);
            ps.setString(4, orgId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error updating status for complaint: " + complaintId, e);
            throw new ComplaintDAOException("Error updating status for complaint: " + complaintId, e);
        }
    }

    @Override
    public List<Complaint> listComplaints(String orgId, String status, String priority, String userId, int limit,
            int offset, String sort, int[] totalOut) {
        List<Complaint> complaints = new ArrayList<>();
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        StringBuilder sql = new StringBuilder(QueryConstants.LIST_COMPLAINTS_BASE);
        StringBuilder countSql = new StringBuilder(QueryConstants.COUNT_COMPLAINTS_BASE);
        List<Object> params = new ArrayList<>();
        params.add(orgId);

        if (status != null && !status.trim().isEmpty()) {
            sql.append("AND STATUS = ? ");
            countSql.append("AND STATUS = ? ");
            params.add(status.trim());
        }
        if (priority != null && !priority.trim().isEmpty()) {
            sql.append("AND PRIORITY = ? ");
            countSql.append("AND PRIORITY = ? ");
            params.add(priority.trim());
        }
        if (userId != null && !userId.trim().isEmpty()) {
            sql.append("AND USER_ID = ? ");
            countSql.append("AND USER_ID = ? ");
            params.add(userId.trim());
        }

        // Only updatedTime / submittedTime(createdTime) / statutoryDueTime are sortable, "-" prefix = descending.
        String orderBy = "UPDATED_TIME DESC";
        if (sort != null && !sort.trim().isEmpty()) {
            String s = sort.trim();
            boolean desc = s.startsWith("-");
            String field = desc ? s.substring(1) : s;
            String column;
            switch (field) {
                case "updatedTime":
                    column = "UPDATED_TIME";
                    break;
                case "submittedTime":
                    column = "CREATED_TIME";
                    break;
                case "statutoryDueTime":
                    column = "STATUTORY_DUE_TIME";
                    break;
                default:
                    column = "UPDATED_TIME";
            }
            orderBy = column + (desc ? " DESC" : " ASC");
        }
        sql.append("ORDER BY ").append(orderBy).append(" LIMIT ? OFFSET ?");

        try {
            conn = DBUtil.getConnection();

            // sql and countSql share the same WHERE clause/params built above: COUNT(*) first for the
            // total (written back via the totalOut out-param), then the LIMIT/OFFSET query for
            // the actual page. Both must run against the same filters so the reported total
            // matches what's actually being paged through.
            PreparedStatement countPs = null;
            ResultSet countRs = null;
            try {
                countPs = conn.prepareStatement(countSql.toString());
                for (int i = 0; i < params.size(); i++) {
                    countPs.setObject(i + 1, params.get(i));
                }
                countRs = countPs.executeQuery();
                if (countRs.next() && totalOut != null && totalOut.length > 0) {
                    totalOut[0] = countRs.getInt(1);
                }
            } finally {
                DBUtil.closeAll(null, countPs, countRs);
            }

            ps = conn.prepareStatement(sql.toString());
            int idx = 1;
            for (Object param : params) {
                ps.setObject(idx++, param);
            }
            // Order must match the "LIMIT ? OFFSET ?" placeholders appended to sql above.
            ps.setInt(idx++, limit);
            ps.setInt(idx, offset);

            rs = ps.executeQuery();
            while (rs.next()) {
                complaints.add(mapResultSetToComplaint(rs));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error listing complaints for org: " + orgId, e);
            throw new ComplaintDAOException("Error listing complaints for org: " + orgId, e);
        } finally {
            DBUtil.closeAll(conn, ps, rs);
        }
        return complaints;
    }

    private Complaint mapResultSetToComplaint(ResultSet rs) throws SQLException {
        return new Complaint(
                rs.getString("COMPLAINT_ID"),
                rs.getString("ORG_ID"),
                rs.getString("USER_ID"),
                rs.getString("REFERENCE_ID"),
                rs.getString("CATEGORY"),
                rs.getString("PRIORITY"),
                rs.getString("STATUS"),
                rs.getString("DESCRIPTION"),
                rs.getLong("CREATED_TIME"),
                rs.getLong("UPDATED_TIME"),
                rs.getLong("STATUTORY_DUE_TIME"));
    }
}
