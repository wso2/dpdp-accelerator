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

package org.wso2.dpdp.accelerator.consent.extensions.dao.models;

/**
 * One consent status-audit row.
 */
public class ConsentStatusAuditRecord {

    private String auditId;
    private String consentId;
    private String orgId;
    private String previousStatus;
    private String currentStatus;
    private String actionType;
    private String actionBy;
    private long actionTime;

    public String getAuditId() {

        return auditId;
    }

    public void setAuditId(String auditId) {

        this.auditId = auditId;
    }

    public String getConsentId() {

        return consentId;
    }

    public void setConsentId(String consentId) {

        this.consentId = consentId;
    }

    public String getOrgId() {

        return orgId;
    }

    public void setOrgId(String orgId) {

        this.orgId = orgId;
    }

    public String getPreviousStatus() {

        return previousStatus;
    }

    public void setPreviousStatus(String previousStatus) {

        this.previousStatus = previousStatus;
    }

    public String getCurrentStatus() {

        return currentStatus;
    }

    public void setCurrentStatus(String currentStatus) {

        this.currentStatus = currentStatus;
    }

    public String getActionType() {

        return actionType;
    }

    public void setActionType(String actionType) {

        this.actionType = actionType;
    }

    public String getActionBy() {

        return actionBy;
    }

    public void setActionBy(String actionBy) {

        this.actionBy = actionBy;
    }

    public long getActionTime() {

        return actionTime;
    }

    public void setActionTime(long actionTime) {

        this.actionTime = actionTime;
    }
}
