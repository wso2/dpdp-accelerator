package org.wso2.dpdp.accelerator.complaint.mgt.service.dto;

public class ComplaintCommentDTO {

    private String id;
    private String actorUserId;
    private String actorRole;
    private String message;
    private boolean isPublic;
    private String fromStatus;
    private String toStatus;
    private long createdTime;

    public ComplaintCommentDTO() {
    }

    public ComplaintCommentDTO(String id, String actorUserId, String actorRole, String message, boolean isPublic,
            String fromStatus, String toStatus, long createdTime) {
        this.id = id;
        this.actorUserId = actorUserId;
        this.actorRole = actorRole;
        this.message = message;
        this.isPublic = isPublic;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.createdTime = createdTime;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }

    public long getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(long createdTime) {
        this.createdTime = createdTime;
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
}
