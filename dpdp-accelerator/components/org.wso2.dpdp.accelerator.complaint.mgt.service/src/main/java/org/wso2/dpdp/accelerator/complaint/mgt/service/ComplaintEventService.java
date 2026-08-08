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

package org.wso2.dpdp.accelerator.complaint.mgt.service;

import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;

import java.util.List;

public interface ComplaintEventService {

    /**
     * Lists timeline entries (status changes, comments, internal notes) for a complaint, optionally
     * filtered to entries after "since" and/or by isPublic, ordered and paginated. totalOut is an
     * out-param - see ComplaintDAO#listComplaints for the convention.
     */
    List<ComplaintEvent> getTimeline(String orgId, String complaintId, Long since, Boolean isPublic,
            String order, int limit, int offset, int[] totalOut);

    /**
     * Adds a COMMENT (isPublic=true) or an officer-internal note (isPublic=false) to the complaint's
     * timeline. Only a COMPLAINT_OFFICER actor may set isPublic=false. If toStatus is non-null, the
     * complaint is transitioned to that status in the same call, subject to the same state-machine
     * rules as updateStatus (an invalid transition throws CO-4090).
     */
    ComplaintEvent addComment(String orgId, String complaintId, String actorUserId, String actorRole,
            String message, boolean isPublic, String toStatus);

    /** Returns the underlying timeline entry, used by the comment-attachment endpoint to verify ownership. */
    ComplaintEvent getTimelineEntry(String orgId, String complaintId, String eventId);

    /**
     * Transitions a complaint to toStatus, recording a timeline entry for the change. note is
     * required when toStatus is RESOLVED. Throws CO-4090 if the transition isn't valid from the
     * complaint's current status - see StatusTransitionValidator.
     */
    Complaint updateStatus(String orgId, String complaintId, String actorUserId, String actorRole,
            String toStatus, String note);
}
