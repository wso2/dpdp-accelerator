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

import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintEventDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.DAOConstants;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.exception.ComplaintDAOException;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.queries.QueryConstants;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ComplaintEventDAOImpl implements ComplaintEventDAO {

    private static final Logger LOGGER = Logger.getLogger(ComplaintEventDAOImpl.class.getName());

    @Override
    public boolean addEvent(ComplaintEvent event) {
        try (Connection conn = DBUtil.getConnection()) {
            return addEvent(conn, event);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error adding event for complaint: " + event.getComplaintId(), e);
            throw new ComplaintDAOException("Error adding event for complaint: " + event.getComplaintId(), e);
        }
    }

    /** Overload for callers composing this write into a caller-owned {@link DBUtil#executeInTransaction}. */
    public boolean addEvent(Connection conn, ComplaintEvent event) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(QueryConstants.ADD_COMPLAINT_EVENT)) {
            ps.setString(1, event.getComplaintEventId());
            ps.setString(2, event.getOrgId());
            ps.setString(3, event.getComplaintId());
            ps.setString(4, event.getActorUserId());
            ps.setString(5, event.getActorRole());
            ps.setBoolean(6, event.isPublic());
            ps.setString(7, event.getComment());
            ps.setString(8, event.getFromStatus());
            ps.setString(9, event.getToStatus());
            ps.setLong(10, event.getActionTime());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error adding event for complaint: " + event.getComplaintId(), e);
            throw new ComplaintDAOException("Error adding event for complaint: " + event.getComplaintId(), e);
        }
    }

    @Override
    public Optional<ComplaintEvent> getEventById(String complaintEventId, String orgId, String complaintId) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(QueryConstants.GET_COMPLAINT_EVENT_BY_ID);
            ps.setString(1, complaintEventId);
            ps.setString(2, orgId);
            ps.setString(3, complaintId);
            rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(mapResultSetToEvent(rs));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error getting event by ID: " + complaintEventId, e);
            throw new ComplaintDAOException("Error getting event by ID: " + complaintEventId, e);
        } finally {
            DBUtil.closeAll(conn, ps, rs);
        }
        return Optional.empty();
    }

    @Override
    public List<ComplaintEvent> listEvents(String orgId, String complaintId, Long since, Boolean isPublic,
            String order, int limit, int offset, int[] totalOut) {
        List<ComplaintEvent> events = new ArrayList<>();
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        StringBuilder sql = new StringBuilder(QueryConstants.LIST_COMPLAINT_EVENTS_BASE);
        StringBuilder countSql = new StringBuilder(QueryConstants.COUNT_COMPLAINT_EVENTS_BASE);
        List<Object> params = new ArrayList<>();
        params.add(orgId);
        params.add(complaintId);

        if (since != null) {
            sql.append("AND ACTION_TIME > ? ");
            countSql.append("AND ACTION_TIME > ? ");
            params.add(since);
        }
        if (isPublic != null) {
            sql.append("AND IS_PUBLIC = ? ");
            countSql.append("AND IS_PUBLIC = ? ");
            params.add(isPublic);
        }

        boolean desc = "desc".equalsIgnoreCase(order);
        sql.append("ORDER BY ACTION_TIME ").append(desc ? "DESC" : "ASC").append(" LIMIT ? OFFSET ?");

        try {
            conn = DBUtil.getConnection();

            // countSql shares the same WHERE clause/params built above as sql: run it first for the
            // total (written back via the totalOut out-param), then the LIMIT/OFFSET query for the page.
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
                events.add(mapResultSetToEvent(rs));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error listing events for complaint: " + complaintId, e);
            throw new ComplaintDAOException("Error listing events for complaint: " + complaintId, e);
        } finally {
            DBUtil.closeAll(conn, ps, rs);
        }
        return events;
    }

    private ComplaintEvent mapResultSetToEvent(ResultSet rs) throws SQLException {
        return new ComplaintEvent(
                rs.getString(DAOConstants.COLUMN_COMPLAINT_EVENT_ID),
                rs.getString("ORG_ID"),
                rs.getString("COMPLAINT_ID"),
                rs.getString("ACTOR_USER_ID"),
                rs.getString("ACTOR_ROLE"),
                rs.getBoolean("IS_PUBLIC"),
                rs.getString("COMMENT"),
                rs.getString("FROM_STATUS"),
                rs.getString("TO_STATUS"),
                rs.getLong("ACTION_TIME"));
    }
}
