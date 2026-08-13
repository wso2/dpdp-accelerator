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

package org.wso2.dpdp.accelerator.complaint.mgt.dao.exception;

/**
 * Wraps a {@link java.sql.SQLException} raised by the persistence layer. Unchecked so DAO
 * interfaces stay free of throws clauses; it propagates through the service layer to the
 * endpoint's generic exception mapper rather than being mistaken for a "not found" result.
 */
public class ComplaintDAOException extends RuntimeException {

    public ComplaintDAOException(String message, Throwable cause) {
        super(message, cause);
    }
}
