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
 * The valid values of the COMPLAINT.STATUS column. Enforced here at the application layer (see
 * isValid/StatusTransitionValidator) as well as by CHK_COMPLAINT_STATUS in mysql.sql - the DB
 * CHECK is a backstop against direct writes that bypass this layer, not the primary enforcement,
 * since adding a value here needs only a code change, not a DB migration.
 */
public enum ComplaintStatus {

    OPEN,
    IN_PROGRESS,
    WAITING_ON_CLIENT,
    AWAITING_INTERNAL_REVIEW,
    RESOLVED;

    public static boolean isValid(String value) {
        if (value == null) {
            return false;
        }
        for (ComplaintStatus status : values()) {
            if (status.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
