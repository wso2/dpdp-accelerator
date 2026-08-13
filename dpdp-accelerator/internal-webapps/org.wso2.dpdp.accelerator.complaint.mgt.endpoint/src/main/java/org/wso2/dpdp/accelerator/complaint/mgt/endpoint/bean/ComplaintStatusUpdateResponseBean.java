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
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.util.DateTimeUtil;

public class ComplaintStatusUpdateResponseBean {

    // Note: the API spec lists "message" as required on this schema but never defines it under
    // properties. Included here as a short human-readable confirmation, consistent with the
    // "Status transition confirmed" wording used in the 200 description for this endpoint.
    private String message;
    private String toStatus;
    private String updatedAt;

    public ComplaintStatusUpdateResponseBean() {
    }

    public static ComplaintStatusUpdateResponseBean from(Complaint complaint) {
        ComplaintStatusUpdateResponseBean bean = new ComplaintStatusUpdateResponseBean();
        bean.message = "Status transition confirmed";
        bean.toStatus = complaint.getStatus();
        bean.updatedAt = DateTimeUtil.toIso(complaint.getUpdatedTime());
        return bean;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getToStatus() {
        return toStatus;
    }

    public void setToStatus(String toStatus) {
        this.toStatus = toStatus;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
