package org.wso2.dpdp.accelerator.complaint.mgt.service.dto;

import java.util.List;

public class ComplaintTimelineEntryDTO {

    private String id;
    private String type; // STATUS_CHANGE / COMMENT / INTERNAL_NOTE - derived, see ComplaintEvent#deriveEntryType
    private boolean isPublic;
    private String actorUserId;
    private String actorRole;
    private String message;
    private String fromStatus;
    private String toStatus;
    private List<ComplaintAttachmentDTO> attachments;
    private long createdTime;

    public ComplaintTimelineEntryDTO() {
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

    public boolean isPublic() {
        return isPublic;
    }

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

    public List<ComplaintAttachmentDTO> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<ComplaintAttachmentDTO> attachments) {
        this.attachments = attachments;
    }

    public long getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(long createdTime) {
        this.createdTime = createdTime;
    }
}
