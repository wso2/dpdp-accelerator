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

import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

/**
 * Complaint event (timeline) DAO interface.
 *
 * <p>Every method takes the {@link Connection} as its first parameter - this DAO never opens or
 * manages its own connection, the service layer owns the transaction. See {@link ComplaintDAO}
 * for why that applies to the read methods too, and for why nothing here declares a checked
 * {@link java.sql.SQLException}.
 */
public interface ComplaintEventDAO {

    /**
     * Persists a new timeline entry (status change, comment, or internal note).
     *
     * @param conn  caller-owned connection this insert runs against
     * @param event timeline entry to persist
     * @return true if a row was inserted
     */
    boolean addEvent(Connection conn, ComplaintEvent event);

    /**
     * Fetches a single timeline entry scoped to its complaint and org.
     *
     * <p>complaintId is taken in addition to complaintEventId/orgId even though complaintEventId is already
     * unique per orgId, for the same reason as {@link ComplaintAttachmentDAO}: it lets the DAO
     * verify the two path segments the caller has ({complaintId}, {complaintEventId}) are actually
     * consistent, rather than silently ignoring a mismatch.
     *
     * @param conn             caller-owned connection this read runs against
     * @param complaintEventId timeline entry to fetch
     * @param orgId            tenant/organization the entry must belong to
     * @param complaintId      complaint the entry must belong to
     * @return the timeline entry, or empty if no row matches all three
     */
    Optional<ComplaintEvent> getEventById(Connection conn, String complaintEventId, String orgId,
            String complaintId);

    /**
     * Lists timeline entries for a complaint with an optional since/until/isPublic filter, sort
     * order, and limit/offset pagination.
     *
     * @param conn        caller-owned connection this read runs against
     * @param orgId       tenant/organization the complaint belongs to
     * @param complaintId complaint to list timeline entries for
     * @param since       optional lower bound, exclusive (ACTION_TIME &gt; since)
     * @param until       optional upper bound, inclusive (ACTION_TIME &lt;= until), matching the
     *                    OpenAPI spec's "at or before" wording for toTime
     * @param isPublic    optional visibility filter
     * @param order       sort order
     * @param limit       maximum number of rows to return
     * @param offset      number of matching rows to skip
     * @param totalOut    out-param, same convention as {@link ComplaintDAO#listComplaints}: pass
     *                    {@code new int[1]} and, after the call, {@code totalOut[0]} holds the
     *                    total row count matching the filters (ignoring limit/offset). Pass
     *                    {@code null} or a zero-length array to skip the count query.
     * @return the page of timeline entries matching the filters
     */
    List<ComplaintEvent> listEvents(Connection conn, String orgId, String complaintId, Long since, Long until,
            Boolean isPublic, String order, int limit, int offset, int[] totalOut);
}
