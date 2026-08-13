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

import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintCommentCreateResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintMessageRequestBean;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintEventService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.impl.ComplaintEventServiceImpl;

public class ComplaintCommentHandler {

    private final ComplaintEventService complaintEventService;

    public ComplaintCommentHandler() {
        this.complaintEventService = new ComplaintEventServiceImpl();
    }

    public ComplaintCommentHandler(ComplaintEventService complaintEventService) {
        this.complaintEventService = complaintEventService;
    }

    public ComplaintCommentCreateResponseBean addComment(String orgId, String complaintId,
            ComplaintMessageRequestBean request) {
        String actorUserId = request != null ? request.getActorUserId() : null;
        String actorRole = request != null ? request.getActorRole() : null;
        String message = request != null ? request.getMessage() : null;
        boolean isPublic = request != null && request.isPublic();
        String toStatus = request != null ? request.getToStatus() : null;

        ComplaintEvent event = complaintEventService.addComment(orgId, complaintId, actorUserId, actorRole,
                message, isPublic, toStatus);
        return ComplaintCommentCreateResponseBean.from(event);
    }
}
