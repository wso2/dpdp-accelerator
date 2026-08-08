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

import java.util.List;
import java.util.Optional;

public interface ComplaintEventDAO {

    /** Persists a new timeline entry (status change, comment, or internal note). Returns true if a row was inserted. */
    boolean addEvent(ComplaintEvent event);

    /**
     * Fetches a single timeline entry scoped to its complaint and org.
     *
     * <p>complaintId is taken in addition to eventId/orgId even though eventId is already
     * unique per orgId, for the same reason as {@link ComplaintAttachmentDAO}: it lets the DAO
     * verify the two path segments the caller has ({complaintId}, {eventId}) are actually
     * consistent, rather than silently ignoring a mismatch.
     */
    Optional<ComplaintEvent> getEventById(String eventId, String orgId, String complaintId);

    /**
     * Lists timeline entries for a complaint with an optional since/isPublic filter, sort order,
     * and limit/offset pagination.
     *
     * <p>totalOut is an out-param, same convention as {@link ComplaintDAO#listComplaints}: pass
     * {@code new int[1]} and, after the call, {@code totalOut[0]} holds the total row count
     * matching the filters (ignoring limit/offset). Pass {@code null} or a zero-length array to
     * skip the count query.
     */
    List<ComplaintEvent> listEvents(String orgId, String complaintId, Long since, Boolean isPublic, String order,
            int limit, int offset, int[] totalOut);
}
