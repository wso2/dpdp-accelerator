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

package org.wso2.dpdp.accelerator.complaint.mgt.dao;

import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;

import java.util.List;
import java.util.Optional;

public interface ComplaintDAO {

    /** Persists a new complaint row. Returns true if a row was inserted. */
    boolean addComplaint(Complaint complaint);

    /** Fetches a single complaint scoped to its org. */
    Optional<Complaint> getComplaintById(String complaintId, String orgId);

    /**
     * Count of complaints for this org whose REFERENCE_ID already uses the given year prefix
     * (e.g. "CMP-2026-%"). Used by ReferenceIdGenerator to pick the next sequence number when
     * minting a new complaint's REFERENCE_ID.
     */
    int countByReferenceIdPrefix(String orgId, String referenceIdLikePattern);

    /** Updates STATUS and UPDATED_TIME for a complaint. Returns true if a row was updated. */
    boolean updateStatus(String complaintId, String orgId, String newStatus, long updatedTime);

    /**
     * Lists complaints for an org with optional status/priority/userId filters, sorting, and
     * limit/offset pagination.
     *
     * <p>totalOut is an out-param: Java has no multi-return, so the caller passes {@code new
     * int[1]} and, after the call, {@code totalOut[0]} holds the total row count matching the
     * filters (ignoring limit/offset) - needed by the endpoint layer to populate pagination
     * metadata (e.g. total pages) alongside the page of results actually returned. Pass
     * {@code null} or a zero-length array to skip the count query.
     */
    List<Complaint> listComplaints(String orgId, String status, String priority, String userId, int limit,
            int offset, String sort, int[] totalOut);
}
