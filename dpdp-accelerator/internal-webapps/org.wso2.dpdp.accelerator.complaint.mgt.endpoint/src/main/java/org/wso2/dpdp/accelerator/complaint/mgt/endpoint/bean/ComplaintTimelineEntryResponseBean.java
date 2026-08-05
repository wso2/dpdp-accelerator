package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.util.DateTimeUtil;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintAttachmentDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintTimelineEntryDTO;

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

    public static ComplaintTimelineEntryResponseBean from(ComplaintTimelineEntryDTO dto,
            List<ComplaintAttachmentDTO> attachmentDTOs) {
        ComplaintTimelineEntryResponseBean bean = new ComplaintTimelineEntryResponseBean();
        bean.id = dto.getId();
        bean.type = dto.getType();
        bean.isPublic = dto.isPublic();
        bean.actorUserId = dto.getActorUserId();
        bean.actorRole = dto.getActorRole();
        bean.message = dto.getMessage();
        bean.fromStatus = dto.getFromStatus();
        bean.toStatus = dto.getToStatus();
        bean.createdTime = DateTimeUtil.toIso(dto.getCreatedTime());

        List<ComplaintAttachmentResponseBean> attachmentBeans = new ArrayList<>();
        if (attachmentDTOs != null) {
            for (ComplaintAttachmentDTO a : attachmentDTOs) {
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
