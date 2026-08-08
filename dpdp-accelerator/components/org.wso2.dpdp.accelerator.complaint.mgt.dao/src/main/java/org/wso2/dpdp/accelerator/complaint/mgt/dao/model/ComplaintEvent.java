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

package org.wso2.dpdp.accelerator.complaint.mgt.dao.model;

public class ComplaintEvent {

    private String eventId;
    private String orgId;
    private String complaintId;
    private String actorUserId;
    private String actorRole;
    private boolean isPublic;
    private String comment;
    private String fromStatus;
    private String toStatus;
    private long actionTime;

    public ComplaintEvent() {
    }

    public ComplaintEvent(String eventId, String orgId, String complaintId, String actorUserId, String actorRole,
            boolean isPublic, String comment, String fromStatus, String toStatus, long actionTime) {
        this.eventId = eventId;
        this.orgId = orgId;
        this.complaintId = complaintId;
        this.actorUserId = actorUserId;
        this.actorRole = actorRole;
        this.isPublic = isPublic;
        this.comment = comment;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actionTime = actionTime;
    }

    /**
     * The API exposes a "type" (STATUS_CHANGE / COMMENT / INTERNAL_NOTE) for each timeline entry.
     * The ER diagram has no TYPE column, so it is derived here rather than stored:
     * - a row with non-null fromStatus/toStatus is a STATUS_CHANGE
     * - otherwise it is a COMMENT (isPublic = true) or an INTERNAL_NOTE (isPublic = false)
     */
    public String deriveEntryType() {
        if (toStatus != null) {
            return "STATUS_CHANGE";
        }
        return isPublic ? "COMMENT" : "INTERNAL_NOTE";
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public String getComplaintId() {
        return complaintId;
    }

    public void setComplaintId(String complaintId) {
        this.complaintId = complaintId;
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

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
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

    public long getActionTime() {
        return actionTime;
    }

    public void setActionTime(long actionTime) {
        this.actionTime = actionTime;
    }
}
