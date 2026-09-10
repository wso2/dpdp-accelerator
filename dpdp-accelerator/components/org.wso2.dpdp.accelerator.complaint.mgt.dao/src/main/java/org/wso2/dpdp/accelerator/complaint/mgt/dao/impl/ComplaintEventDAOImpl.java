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
import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintEventDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintDBColumns;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.exception.ComplaintDAOException;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.queries.ComplaintCommonDBQueries;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.queries.ComplaintEventQueryBuilder;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.queries.ComplaintQueryFactory;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.queries.QueryResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ComplaintEventDAOImpl implements ComplaintEventDAO {

    private static final Log LOG = LogFactory.getLog(ComplaintEventDAOImpl.class);

    private ComplaintCommonDBQueries getQueries(Connection conn) {
        return ComplaintQueryFactory.getQueryProvider(conn);
    }

    @Override
    public boolean addEvent(Connection conn, ComplaintEvent event) {
        try (PreparedStatement ps = conn.prepareStatement(getQueries(conn).getAddComplaintEventQuery())) {
            ps.setString(1, event.getComplaintEventId());
            ps.setString(2, event.getOrgId());
            ps.setString(3, event.getComplaintId());
            ps.setString(4, event.getActorUserId());
            ps.setString(5, event.getActorUserName());
            ps.setString(6, event.getActorRole());
            ps.setBoolean(7, event.isPublic());
            ps.setString(8, event.getComment());
            ps.setString(9, event.getFromStatus());
            ps.setString(10, event.getToStatus());
            ps.setLong(11, event.getActionTime());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.error("Error adding event for complaint: " + LogSanitizer.sanitize(event.getComplaintId()), e);
            throw new ComplaintDAOException("Error adding event for complaint: " + event.getComplaintId(), e);
        }
    }

    @Override
    public Optional<ComplaintEvent> getEventById(Connection conn, String complaintEventId, String orgId,
            String complaintId) {
        try (PreparedStatement ps = conn.prepareStatement(getQueries(conn).getGetComplaintEventByIdQuery())) {
            ps.setString(1, complaintEventId);
            ps.setString(2, orgId);
            ps.setString(3, complaintId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToEvent(rs));
                }
            }
        } catch (SQLException e) {
            LOG.error("Error getting event by ID: " + LogSanitizer.sanitize(complaintEventId), e);
            throw new ComplaintDAOException("Error getting event by ID: " + complaintEventId, e);
        }
        return Optional.empty();
    }

    @Override
    public List<ComplaintEvent> listEvents(Connection conn, String orgId, String complaintId, Long since,
            Long until, Boolean isPublic, String order, int limit, int offset, int[] totalOut) {
        List<ComplaintEvent> events = new ArrayList<>();

        try {
            ComplaintEventQueryBuilder builder = new ComplaintEventQueryBuilder(orgId, complaintId, getQueries(conn))
                    .setSince(since)
                    .setUntil(until)
                    .setIsPublic(isPublic)
                    .setOrder(order);
            QueryResult countQuery = builder.buildCountQuery();
            QueryResult selectQuery = builder.buildSelectQuery(limit, offset);

            // countQuery and selectQuery share the same WHERE clause/params: run the count first for
            // the total (written back via the totalOut out-param), then the LIMIT/OFFSET query for the page.
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
                        events.add(mapResultSetToEvent(rs));
                    }
                }
            }
        } catch (SQLException e) {
            LOG.error("Error listing events for complaint: " + LogSanitizer.sanitize(complaintId), e);
            throw new ComplaintDAOException("Error listing events for complaint: " + complaintId, e);
        }
        return events;
    }

    private ComplaintEvent mapResultSetToEvent(ResultSet rs) throws SQLException {
        return new ComplaintEvent(
                rs.getString(ComplaintDBColumns.COMPLAINT_EVENT_ID),
                rs.getString(ComplaintDBColumns.ORG_ID),
                rs.getString(ComplaintDBColumns.COMPLAINT_ID),
                rs.getString(ComplaintDBColumns.ACTOR_USER_ID),
                rs.getString(ComplaintDBColumns.ACTOR_USER_NAME),
                rs.getString(ComplaintDBColumns.ACTOR_ROLE),
                rs.getBoolean(ComplaintDBColumns.IS_PUBLIC),
                rs.getString(ComplaintDBColumns.COMMENT),
                rs.getString(ComplaintDBColumns.FROM_STATUS),
                rs.getString(ComplaintDBColumns.TO_STATUS),
                rs.getLong(ComplaintDBColumns.ACTION_TIME));
    }
}
