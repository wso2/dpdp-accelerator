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

import java.util.List;


public interface ComplaintService {

    /** Lean result for POST /complaints - no attachments field (there can't be any yet). */
    Complaint createComplaint(String orgId, String userId, String subjectCategory, String description);

    /** Core complaint fields for GET /complaints/{complaintId} (attachments are composed in by the handler). */
    Complaint getComplaint(String orgId, String complaintId);

    /**
     * Fetches core complaint fields, throwing a 404 ComplaintException if it doesn't exist for this org.
     * Used by other services (events, attachments) that need to confirm a complaint exists/belongs to the
     * org before acting on it, without duplicating that existence check in every DAO.
     */
    Complaint requireComplaint(String orgId, String complaintId);

    /**
     * Lists complaints for an org with optional status/priority/userId filters, sorting, and
     * limit/offset pagination. totalOut is an out-param - see ComplaintDAO#listComplaints.
     */
    List<Complaint> listComplaints(String orgId, String status, String priority, String userId, int limit,
            int offset, String sort, int[] totalOut);
}
