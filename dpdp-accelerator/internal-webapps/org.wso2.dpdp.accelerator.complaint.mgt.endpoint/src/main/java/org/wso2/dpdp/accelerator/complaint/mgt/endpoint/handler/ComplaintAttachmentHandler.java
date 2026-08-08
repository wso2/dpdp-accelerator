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

package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.handler;

import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintAttachment;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintAttachmentDownloadResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintAttachmentResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintAttachmentService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintAttachmentService.UploadedFile;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintEventService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintErrorCode;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintException;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintServiceConstants;
import org.wso2.dpdp.accelerator.complaint.mgt.service.impl.ComplaintAttachmentServiceImpl;
import org.wso2.dpdp.accelerator.complaint.mgt.service.impl.ComplaintEventServiceImpl;
import org.wso2.dpdp.accelerator.complaint.mgt.service.impl.ComplaintServiceImpl;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ComplaintAttachmentHandler {

    private final ComplaintAttachmentService complaintAttachmentService;

    public ComplaintAttachmentHandler() {
        ComplaintService complaintService = new ComplaintServiceImpl();
        ComplaintEventService complaintEventService = new ComplaintEventServiceImpl();
        this.complaintAttachmentService = new ComplaintAttachmentServiceImpl(complaintService,
                complaintEventService);
    }

    public ComplaintAttachmentHandler(ComplaintAttachmentService complaintAttachmentService) {
        this.complaintAttachmentService = complaintAttachmentService;
    }

    public List<ComplaintAttachmentResponseBean> uploadComplaintAttachments(String orgId, String complaintId,
            List<FormDataBodyPart> fileParts) {
        List<UploadedFile> files = toUploadedFiles(fileParts);
        List<ComplaintAttachment> result =
                complaintAttachmentService.uploadComplaintAttachments(orgId, complaintId, files);
        return toBeans(result);
    }

    public List<ComplaintAttachmentResponseBean> uploadCommentAttachments(String orgId, String complaintId,
            String commentId, String actorUserId, List<FormDataBodyPart> fileParts) {
        List<UploadedFile> files = toUploadedFiles(fileParts);
        List<ComplaintAttachment> result = complaintAttachmentService.uploadCommentAttachments(orgId, complaintId,
                commentId, actorUserId, files);
        return toBeans(result);
    }

    public ComplaintAttachmentDownloadResponseBean downloadAttachment(String orgId, String complaintId,
            String attachmentId, String actorRole) {
        ComplaintAttachment attachment =
                complaintAttachmentService.downloadAttachment(orgId, complaintId, attachmentId, actorRole);
        return new ComplaintAttachmentDownloadResponseBean(attachment.getAttachmentId(), attachment.getFileName(),
                attachment.getContentType(), attachment.getFileData());
    }

    private List<UploadedFile> toUploadedFiles(List<FormDataBodyPart> fileParts) {
        List<UploadedFile> files = new ArrayList<>();
        if (fileParts == null) {
            return files;
        }
        for (FormDataBodyPart part : fileParts) {
            try (InputStream in = part.getValueAs(InputStream.class)) {
                byte[] data = readAllBytes(in);
                String contentType = part.getMediaType() != null
                        ? part.getMediaType().toString()
                        : MediaType.APPLICATION_OCTET_STREAM;
                String fileName = part.getContentDisposition() != null
                        ? part.getContentDisposition().getFileName()
                        : null;
                files.add(new UploadedFile(fileName, contentType, data));
            } catch (IOException e) {
                throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                        ComplaintServiceConstants.FILE_READ_FAILED_ERROR);
            }
        }
        return files;
    }

    private byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    private List<ComplaintAttachmentResponseBean> toBeans(List<ComplaintAttachment> attachments) {
        List<ComplaintAttachmentResponseBean> beans = new ArrayList<>();
        for (ComplaintAttachment attachment : attachments) {
            beans.add(new ComplaintAttachmentResponseBean(attachment.getAttachmentId(), attachment.getFileName(),
                    attachment.getContentType(), attachment.getSizeBytes()));
        }
        return beans;
    }
}
