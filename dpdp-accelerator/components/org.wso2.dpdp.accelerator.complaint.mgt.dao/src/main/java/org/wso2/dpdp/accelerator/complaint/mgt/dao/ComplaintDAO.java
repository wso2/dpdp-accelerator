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
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintQueueStats;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

/**
 * Complaint DAO interface. Every method takes a {@link Connection} and throws only the unchecked
 * {@link org.wso2.dpdp.accelerator.complaint.mgt.dao.exception.ComplaintDAOException}, so calls
 * compose freely inside a transaction (e.g. {@link
 * org.wso2.dpdp.accelerator.common.util.DatabaseUtils#executeInTransaction}).
 */
public interface ComplaintDAO {

    /**
     * Persists a new complaint row.
     *
     * @param conn      caller-owned connection this insert runs against
     * @param complaint complaint to persist
     * @return true if a row was inserted
     */
    boolean addComplaint(Connection conn, Complaint complaint);

    /**
     * Fetches a single complaint scoped to its org.
     *
     * @param conn        caller-owned connection this read runs against
     * @param complaintId complaint to fetch
     * @param orgId       tenant/organization the complaint must belong to
     * @return the complaint, or empty if no row matches both complaintId and orgId
     */
    Optional<Complaint> getComplaintById(Connection conn, String complaintId, String orgId);

    /**
     * Counts complaints for this org whose REFERENCE_ID already uses the given year prefix (e.g.
     * "CMP-2026-%"). Used by ComplaintServiceUtil to pick the next sequence number when minting a
     * new complaint's REFERENCE_ID.
     *
     * @param conn                  caller-owned connection this read runs against
     * @param orgId                 tenant/organization to count within
     * @param referenceIdLikePattern SQL LIKE pattern for the year prefix
     * @return the number of matching rows
     */
    int countByReferenceIdPrefix(Connection conn, String orgId, String referenceIdLikePattern);

    /**
     * Updates STATUS and UPDATED_TIME for a complaint.
     *
     * @param conn        caller-owned connection this update runs against
     * @param complaintId complaint to update
     * @param orgId       tenant/organization the complaint must belong to
     * @param newStatus   status to transition to
     * @param updatedTime new UPDATED_TIME value
     * @return true if a row was updated
     */
    boolean updateStatus(Connection conn, String complaintId, String orgId, String newStatus, long updatedTime);

    /**
     * Lists complaints for an org with optional status/priority/userId filters, sorting, and
     * limit/offset pagination.
     *
     * @param conn     caller-owned connection this read runs against
     * @param orgId    tenant/organization to list complaints for
     * @param status   optional status filter
     * @param priority optional priority filter
     * @param userId   optional Data Principal filter
     * @param limit    maximum number of rows to return
     * @param offset   number of matching rows to skip
     * @param sort     sort order
     * @param totalOut out-param - Java has no multi-return, so the caller passes {@code new
     *                 int[1]} and, after the call, {@code totalOut[0]} holds the total row count
     *                 matching the filters (ignoring limit/offset), needed by the endpoint layer to
     *                 populate pagination metadata (e.g. total pages) alongside the page of results
     *                 actually returned. Pass {@code null} or a zero-length array to skip the count
     *                 query.
     * @return the page of complaints matching the filters
     */
    List<Complaint> listComplaints(Connection conn, String orgId, String status, String priority, String userId,
            int limit, int offset, String sort, int[] totalOut);

    /**
     * Computes org-wide counts for the officer/admin queue's summary tiles. The following
     * functionality is contained in this method.
     *
     * <p>1. Counts complaints currently OPEN or IN_PROGRESS as a single "open" bucket.
     * <p>2. Counts complaints currently AWAITING_INTERNAL_REVIEW.
     * <p>3. Counts complaints currently RESOLVED.
     * <p>4. Counts still-open complaints whose STATUTORY_DUE_TIME has already passed as of
     * {@code now} as SLA-breached.
     *
     * <p>WAITING_ON_CLIENT has no dedicated tile, so it isn't counted in any bucket above. now is
     * passed in rather than read here, same as every other DAO write's timestamp, so the whole
     * call stays deterministic and testable. Always unfiltered by status/priority - the tiles
     * summarize the whole queue regardless of whatever filter is currently applied to the
     * paginated list beside them.
     *
     * @param conn  caller-owned connection this read runs against
     * @param orgId tenant/organization to compute stats for
     * @param now   timestamp SLA-breach is judged against
     * @return the queue stats
     */
    ComplaintQueueStats getQueueStats(Connection conn, String orgId, long now);
}
