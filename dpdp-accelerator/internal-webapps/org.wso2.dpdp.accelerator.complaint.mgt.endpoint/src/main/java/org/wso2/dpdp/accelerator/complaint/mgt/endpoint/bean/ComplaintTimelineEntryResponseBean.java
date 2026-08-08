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

import com.fasterxml.jackson.annotation.JsonProperty;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintAttachment;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.util.DateTimeUtil;

import java.util.ArrayList;
import java.util.List;

public class ComplaintTimelineEntryResponseBean {

    private String id;
    private String type;
    private boolean isPublic;
    private String actorUserId;
    private String actorRole;
    private String message;
    private String fromStatus;
    private String toStatus;
    private List<ComplaintAttachmentResponseBean> attachments;
    private String createdTime;

    public ComplaintTimelineEntryResponseBean() {
    }

    public static ComplaintTimelineEntryResponseBean from(ComplaintEvent event,
            List<ComplaintAttachment> attachments) {
        ComplaintTimelineEntryResponseBean bean = new ComplaintTimelineEntryResponseBean();
        bean.id = event.getEventId();
        bean.type = event.deriveEntryType();
        bean.isPublic = event.isPublic();
        bean.actorUserId = event.getActorUserId();
        bean.actorRole = event.getActorRole();
        bean.message = event.getComment();
        bean.fromStatus = event.getFromStatus();
        bean.toStatus = event.getToStatus();
        bean.createdTime = DateTimeUtil.toIso(event.getActionTime());

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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @JsonProperty("isPublic")
    public boolean isPublic() {
        return isPublic;
    }

    @JsonProperty("isPublic")
    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }

    public String getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(String actorUserId) {
        this.actorUserId = actorUserId;
    }

    public String getActorRole() {
        return actorRole;
    }

    public void setActorRole(String actorRole) {
        this.actorRole = actorRole;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(String fromStatus) {
        this.fromStatus = fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public void setToStatus(String toStatus) {
        this.toStatus = toStatus;
    }

    public List<ComplaintAttachmentResponseBean> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<ComplaintAttachmentResponseBean> attachments) {
        this.attachments = attachments;
    }

    public String getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(String createdTime) {
        this.createdTime = createdTime;
    }
}
