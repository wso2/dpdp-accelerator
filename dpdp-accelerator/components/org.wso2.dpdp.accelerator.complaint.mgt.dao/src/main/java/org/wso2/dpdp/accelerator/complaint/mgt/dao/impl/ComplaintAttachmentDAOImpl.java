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
import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintAttachmentDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintDBColumns;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.exception.ComplaintDAOException;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintAttachment;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.queries.ComplaintQueryFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ComplaintAttachmentDAOImpl implements ComplaintAttachmentDAO {

    private static final Log LOG = LogFactory.getLog(ComplaintAttachmentDAOImpl.class);

    @Override
    public boolean addAttachment(Connection conn, ComplaintAttachment attachment) {
        try (PreparedStatement ps = conn.prepareStatement(ComplaintQueryFactory.getQueryProvider(conn).getAddComplaintAttachmentQuery())) {
            ps.setString(1, attachment.getAttachmentId());
            ps.setString(2, attachment.getOrgId());
            ps.setString(3, attachment.getComplaintId());
            if (attachment.getComplaintEventId() != null) {
                ps.setString(4, attachment.getComplaintEventId());
            } else {
                ps.setNull(4, Types.VARCHAR);
            }
            ps.setString(5, attachment.getFileName());
            ps.setString(6, attachment.getContentType());
            ps.setBytes(7, attachment.getFileData());
            ps.setBoolean(8, attachment.isPublic());
            ps.setLong(9, attachment.getCreatedTime());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.error("Error adding attachment for complaint: "
                    + LogSanitizer.sanitize(attachment.getComplaintId()), e);
            throw new ComplaintDAOException("Error adding attachment for complaint: " + attachment.getComplaintId(),
                    e);
        }
    }

    @Override
    public Optional<ComplaintAttachment> getAttachmentMetadataById(Connection conn, String attachmentId,
            String orgId, String complaintId) {
        try (PreparedStatement ps = conn.prepareStatement(ComplaintQueryFactory.getQueryProvider(conn).getGetAttachmentMetadataByIdQuery())) {
            ps.setString(1, attachmentId);
            ps.setString(2, orgId);
            ps.setString(3, complaintId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ComplaintAttachment a = new ComplaintAttachment();
                    a.setAttachmentId(rs.getString(ComplaintDBColumns.ATTACHMENT_ID));
                    a.setOrgId(rs.getString(ComplaintDBColumns.ORG_ID));
                    a.setComplaintId(rs.getString(ComplaintDBColumns.COMPLAINT_ID));
                    a.setComplaintEventId(rs.getString(ComplaintDBColumns.COMPLAINT_EVENT_ID));
                    a.setFileName(rs.getString(ComplaintDBColumns.FILE_NAME));
                    a.setContentType(rs.getString(ComplaintDBColumns.FILE_CONTENT_TYPE));
                    a.setSizeBytesOverride(rs.getLong(ComplaintDBColumns.SIZE_BYTES)); // fileData left null - blob not loaded
                    a.setPublic(rs.getBoolean(ComplaintDBColumns.IS_PUBLIC));
                    a.setCreatedTime(rs.getLong(ComplaintDBColumns.CREATED_TIME));
                    return Optional.of(a);
                }
            }
        } catch (SQLException e) {
            LOG.error("Error getting attachment metadata by ID: " + LogSanitizer.sanitize(attachmentId), e);
            throw new ComplaintDAOException("Error getting attachment metadata by ID: " + attachmentId, e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<ComplaintAttachment> getAttachmentWithDataById(Connection conn, String attachmentId,
            String orgId, String complaintId) {
        try (PreparedStatement ps = conn.prepareStatement(ComplaintQueryFactory.getQueryProvider(conn).getGetAttachmentWithDataByIdQuery())) {
            ps.setString(1, attachmentId);
            ps.setString(2, orgId);
            ps.setString(3, complaintId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToAttachment(rs));
                }
            }
        } catch (SQLException e) {
            LOG.error("Error getting attachment with data by ID: " + LogSanitizer.sanitize(attachmentId), e);
            throw new ComplaintDAOException("Error getting attachment with data by ID: " + attachmentId, e);
        }
        return Optional.empty();
    }

    @Override
    public List<ComplaintAttachment> listAttachmentsForComplaint(Connection conn, String orgId, String complaintId) {
        List<ComplaintAttachment> attachments = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(ComplaintQueryFactory.getQueryProvider(conn).getListAttachmentMetadataByComplaintQuery())) {
            ps.setString(1, orgId);
            ps.setString(2, complaintId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ComplaintAttachment a = new ComplaintAttachment();
                    a.setAttachmentId(rs.getString(ComplaintDBColumns.ATTACHMENT_ID));
                    a.setOrgId(rs.getString(ComplaintDBColumns.ORG_ID));
                    a.setComplaintId(rs.getString(ComplaintDBColumns.COMPLAINT_ID));
                    a.setComplaintEventId(rs.getString(ComplaintDBColumns.COMPLAINT_EVENT_ID));
                    a.setFileName(rs.getString(ComplaintDBColumns.FILE_NAME));
                    a.setContentType(rs.getString(ComplaintDBColumns.FILE_CONTENT_TYPE));
                    a.setSizeBytesOverride(rs.getLong(ComplaintDBColumns.SIZE_BYTES)); // size only, no real bytes loaded
                    a.setPublic(rs.getBoolean(ComplaintDBColumns.IS_PUBLIC));
                    a.setCreatedTime(rs.getLong(ComplaintDBColumns.CREATED_TIME));
                    attachments.add(a);
                }
            }
        } catch (SQLException e) {
            LOG.error("Error listing attachments for complaint: " + LogSanitizer.sanitize(complaintId), e);
            throw new ComplaintDAOException("Error listing attachments for complaint: " + complaintId, e);
        }
        return attachments;
    }

    private ComplaintAttachment mapResultSetToAttachment(ResultSet rs) throws SQLException {
        ComplaintAttachment a = new ComplaintAttachment();
        a.setAttachmentId(rs.getString(ComplaintDBColumns.ATTACHMENT_ID));
        a.setOrgId(rs.getString(ComplaintDBColumns.ORG_ID));
        a.setComplaintId(rs.getString(ComplaintDBColumns.COMPLAINT_ID));
        a.setComplaintEventId(rs.getString(ComplaintDBColumns.COMPLAINT_EVENT_ID));
        a.setFileName(rs.getString(ComplaintDBColumns.FILE_NAME));
        a.setContentType(rs.getString(ComplaintDBColumns.FILE_CONTENT_TYPE));
        a.setFileData(rs.getBytes(ComplaintDBColumns.FILE_DATA));
        a.setPublic(rs.getBoolean(ComplaintDBColumns.IS_PUBLIC));
        a.setCreatedTime(rs.getLong(ComplaintDBColumns.CREATED_TIME));
        return a;
    }
}
