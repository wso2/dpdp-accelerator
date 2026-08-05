package org.wso2.dpdp.accelerator.complaint.mgt.dao.impl;

import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintEventDAO;
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
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(QueryConstants.ADD_COMPLAINT_EVENT);
            ps.setString(1, event.getEventId());
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
            LOGGER.log(Level.SEVERE, "Error adding complaint event for complaint: " + event.getComplaintId(), e);
        } finally {
            DBUtil.closeAll(conn, ps, null);
        }
        return false;
    }

    @Override
    public Optional<ComplaintEvent> getEventById(String eventId, String orgId, String complaintId) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(QueryConstants.GET_COMPLAINT_EVENT_BY_ID);
            ps.setString(1, eventId);
            ps.setString(2, orgId);
            ps.setString(3, complaintId);
            rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(mapResultSetToEvent(rs));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error getting complaint event by ID: " + eventId, e);
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

        String orderBy = "asc".equalsIgnoreCase(order) || order == null ? "ACTION_TIME ASC" : "ACTION_TIME DESC";
        sql.append("ORDER BY ").append(orderBy).append(" LIMIT ? OFFSET ?");

        try {
            conn = DBUtil.getConnection();

            PreparedStatement countPs = conn.prepareStatement(countSql.toString());
            for (int i = 0; i < params.size(); i++) {
                countPs.setObject(i + 1, params.get(i));
            }
            ResultSet countRs = countPs.executeQuery();
            if (countRs.next() && totalOut != null && totalOut.length > 0) {
                totalOut[0] = countRs.getInt(1);
            }
            DBUtil.closeAll(null, countPs, countRs);

            ps = conn.prepareStatement(sql.toString());
            int idx = 1;
            for (Object param : params) {
                ps.setObject(idx++, param);
            }
            ps.setInt(idx++, limit);
            ps.setInt(idx, offset);

            rs = ps.executeQuery();
            while (rs.next()) {
                events.add(mapResultSetToEvent(rs));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error listing complaint events for complaint: " + complaintId, e);
        } finally {
            DBUtil.closeAll(conn, ps, rs);
        }
        return events;
    }

    private ComplaintEvent mapResultSetToEvent(ResultSet rs) throws SQLException {
        return new ComplaintEvent(
                rs.getString("EVENT_ID"),
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
