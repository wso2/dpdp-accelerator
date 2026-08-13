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

package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean;

import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintAttachment;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.util.DateTimeUtil;

import java.util.ArrayList;
import java.util.List;

public class ComplaintRecordBean {

    private String id;
    private String referenceId;
    private String subjectCategory;
    private String priority;
    private String status;
    private String userId;
    private String description;
    private List<ComplaintAttachmentResponseBean> attachments;
    private String submittedAt;
    private String updatedAt;
    private String statutoryDueDate;

    public ComplaintRecordBean() {
    }

    public static ComplaintRecordBean from(Complaint complaint, List<ComplaintAttachment> attachments) {
        ComplaintRecordBean bean = new ComplaintRecordBean();
        bean.id = complaint.getComplaintId();
        bean.referenceId = complaint.getReferenceId();
        bean.subjectCategory = complaint.getCategory();
        bean.priority = complaint.getPriority();
        bean.status = complaint.getStatus();
        bean.userId = complaint.getUserId();
        bean.description = complaint.getDescription();
        bean.submittedAt = DateTimeUtil.toIso(complaint.getCreatedTime());
        bean.updatedAt = DateTimeUtil.toIso(complaint.getUpdatedTime());
        bean.statutoryDueDate = DateTimeUtil.toIso(complaint.getStatutoryDueTime());

        List<ComplaintAttachmentResponseBean> attachmentBeans = new ArrayList<>();
        if (attachments != null) {
            for (ComplaintAttachment a : attachments) {
                attachmentBeans.add(new ComplaintAttachmentResponseBean(a.getAttachmentId(), a.getFileName(),
                        a.getContentType(), a.getSizeBytes()));
            }
        }
        bean.attachments = attachmentBeans;
        return bean;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getSubjectCategory() {
        return subjectCategory;
    }

    public void setSubjectCategory(String subjectCategory) {
        this.subjectCategory = subjectCategory;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<ComplaintAttachmentResponseBean> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<ComplaintAttachmentResponseBean> attachments) {
        this.attachments = attachments;
    }

    public String getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(String submittedAt) {
        this.submittedAt = submittedAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getStatutoryDueDate() {
        return statutoryDueDate;
    }

    public void setStatutoryDueDate(String statutoryDueDate) {
        this.statutoryDueDate = statutoryDueDate;
    }
}
