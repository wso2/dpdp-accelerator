package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.util.DateTimeUtil;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintCommentDTO;

public class ComplaintCommentCreateResponseBean {

    private String id;
    private String actorUserId;
    private String actorRole;
    private String message;
    private boolean isPublic;
    private String fromStatus;
    private String toStatus;
    private String createdTime;

    public ComplaintCommentCreateResponseBean() {
    }

    public static ComplaintCommentCreateResponseBean from(ComplaintCommentDTO dto) {
        ComplaintCommentCreateResponseBean bean = new ComplaintCommentCreateResponseBean();
        bean.id = dto.getId();
        bean.actorUserId = dto.getActorUserId();
        bean.actorRole = dto.getActorRole();
        bean.message = dto.getMessage();
        bean.isPublic = dto.isPublic();
        bean.fromStatus = dto.getFromStatus();
        bean.toStatus = dto.getToStatus();
        bean.createdTime = DateTimeUtil.toIso(dto.getCreatedTime());
        return bean;
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

    @JsonProperty("isPublic")
    public boolean isPublic() {
        return isPublic;
    }

    @JsonProperty("isPublic")
    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }

    public String getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(String createdTime) {
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
