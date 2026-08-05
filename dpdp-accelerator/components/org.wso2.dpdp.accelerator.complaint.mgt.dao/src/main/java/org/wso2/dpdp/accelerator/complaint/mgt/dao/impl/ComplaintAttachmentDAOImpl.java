package org.wso2.dpdp.accelerator.complaint.mgt.dao.impl;

import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintAttachmentDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintAttachment;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.queries.QueryConstants;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ComplaintAttachmentDAOImpl implements ComplaintAttachmentDAO {

    private static final Logger LOGGER = Logger.getLogger(ComplaintAttachmentDAOImpl.class.getName());

    @Override
    public boolean addAttachment(ComplaintAttachment attachment) {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(QueryConstants.ADD_COMPLAINT_ATTACHMENT);
            ps.setString(1, attachment.getAttachmentId());
            ps.setString(2, attachment.getOrgId());
            ps.setString(3, attachment.getComplaintId());
            if (attachment.getEventId() != null) {
                ps.setString(4, attachment.getEventId());
            } else {
                ps.setNull(4, Types.VARCHAR);
            }
            ps.setString(5, attachment.getFileName());
            ps.setString(6, attachment.getContentType());
            ps.setBytes(7, attachment.getFileData());
            ps.setLong(8, attachment.getCreatedTime());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error adding attachment for complaint: " + attachment.getComplaintId(), e);
        } finally {
            DBUtil.closeAll(conn, ps, null);
        }
        return false;
    }

    @Override
    public Optional<ComplaintAttachment> getAttachmentMetadataById(String attachmentId, String orgId,
            String complaintId) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(QueryConstants.GET_ATTACHMENT_METADATA_BY_ID);
            ps.setString(1, attachmentId);
            ps.setString(2, orgId);
            ps.setString(3, complaintId);
            rs = ps.executeQuery();
            if (rs.next()) {
                ComplaintAttachment a = new ComplaintAttachment();
                a.setAttachmentId(rs.getString("ATTACHMENT_ID"));
                a.setOrgId(rs.getString("ORG_ID"));
                a.setComplaintId(rs.getString("COMPLAINT_ID"));
                a.setEventId(rs.getString("EVENT_ID"));
                a.setFileName(rs.getString("FILE_NAME"));
                a.setContentType(rs.getString("FILE_CONTENT_TYPE"));
                a.setSizeBytesOverride(rs.getLong("SIZE_BYTES")); // fileData left null - blob not loaded
                a.setCreatedTime(rs.getLong("CREATED_TIME"));
                return Optional.of(a);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error getting attachment metadata by ID: " + attachmentId, e);
        } finally {
            DBUtil.closeAll(conn, ps, rs);
        }
        return Optional.empty();
    }

    @Override
    public Optional<ComplaintAttachment> getAttachmentWithDataById(String attachmentId, String orgId,
            String complaintId) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(QueryConstants.GET_ATTACHMENT_WITH_DATA_BY_ID);
            ps.setString(1, attachmentId);
            ps.setString(2, orgId);
            ps.setString(3, complaintId);
            rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(mapResultSetToAttachment(rs));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error getting attachment with data by ID: " + attachmentId, e);
        } finally {
            DBUtil.closeAll(conn, ps, rs);
        }
        return Optional.empty();
    }

    @Override
    public List<ComplaintAttachment> listAttachmentsForComplaint(String orgId, String complaintId) {
        return listByQuery(QueryConstants.LIST_ATTACHMENT_METADATA_BY_COMPLAINT, orgId, complaintId, null);
    }

    @Override
    public List<ComplaintAttachment> listAttachmentsForEvent(String orgId, String complaintId, String eventId) {
        return listByQuery(QueryConstants.LIST_ATTACHMENT_METADATA_BY_EVENT, orgId, complaintId, eventId);
    }

    private List<ComplaintAttachment> listByQuery(String query, String orgId, String complaintId, String eventId) {
        List<ComplaintAttachment> attachments = new ArrayList<>();
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(query);
            ps.setString(1, orgId);
            ps.setString(2, complaintId);
            if (eventId != null) {
                ps.setString(3, eventId);
            }
            rs = ps.executeQuery();
            while (rs.next()) {
                ComplaintAttachment a = new ComplaintAttachment();
                a.setAttachmentId(rs.getString("ATTACHMENT_ID"));
                a.setOrgId(rs.getString("ORG_ID"));
                a.setComplaintId(rs.getString("COMPLAINT_ID"));
                a.setEventId(rs.getString("EVENT_ID"));
                a.setFileName(rs.getString("FILE_NAME"));
                a.setContentType(rs.getString("FILE_CONTENT_TYPE"));
                a.setSizeBytesOverride(rs.getLong("SIZE_BYTES")); // size only, no real bytes loaded
                a.setCreatedTime(rs.getLong("CREATED_TIME"));
                attachments.add(a);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error listing attachments for complaint: " + complaintId, e);
        } finally {
            DBUtil.closeAll(conn, ps, rs);
        }
        return attachments;
    }

    private ComplaintAttachment mapResultSetToAttachment(ResultSet rs) throws SQLException {
        ComplaintAttachment a = new ComplaintAttachment();
        a.setAttachmentId(rs.getString("ATTACHMENT_ID"));
        a.setOrgId(rs.getString("ORG_ID"));
        a.setComplaintId(rs.getString("COMPLAINT_ID"));
        a.setEventId(rs.getString("EVENT_ID"));
        a.setFileName(rs.getString("FILE_NAME"));
        a.setContentType(rs.getString("FILE_CONTENT_TYPE"));
        a.setFileData(rs.getBytes("FILE_DATA"));
        a.setCreatedTime(rs.getLong("CREATED_TIME"));
        return a;
    }
}
