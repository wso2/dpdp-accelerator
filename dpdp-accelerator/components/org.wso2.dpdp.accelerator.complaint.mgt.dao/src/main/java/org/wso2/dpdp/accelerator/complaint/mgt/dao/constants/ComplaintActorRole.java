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

package org.wso2.dpdp.accelerator.complaint.mgt.dao.constants;

/**
 * The valid values of the COMPLAINT_EVENT.ACTOR_ROLE column. SYSTEM is deliberately excluded from
 * ComplaintEventServiceImpl's client-input check - it is only ever written by the server itself,
 * never accepted from an API caller. CHK_CE_ACTOR_ROLE in mysql.sql remains a DB-level backstop.
 */
public enum ComplaintActorRole {

    USER,
    COMPLAINT_OFFICER,
    SYSTEM;

    public static boolean isValid(String value) {
        if (value == null) {
            return false;
        }
        for (ComplaintActorRole role : values()) {
            if (role.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
