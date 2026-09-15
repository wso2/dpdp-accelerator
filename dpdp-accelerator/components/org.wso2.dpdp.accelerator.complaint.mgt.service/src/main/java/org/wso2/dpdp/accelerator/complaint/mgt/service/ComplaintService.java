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
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintCreateResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintQueueStatsResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintException;

import java.util.List;


/**
 * Complaint core service interface.
 */
public interface ComplaintService {

    /**
     * Creates a new complaint for POST /complaints. Returns a lean result - no attachments field,
     * since there can't be any yet.
     *
     * @param orgId           tenant/organization the complaint belongs to
     * @param userId          Data Principal the complaint is lodged for
     * @param userName        best-effort display name for userId, resolved by the caller from the
     *                        token that identified them when available - null when the complaint
     *                        is lodged on the Data Principal's behalf by an officer who only
     *                        supplied a userId
     * @param subjectCategory category of the complaint subject
     * @param description     free-text description of the complaint
     * @return the newly created complaint
     * @throws ComplaintException thrown if the request fails validation
     */
    ComplaintCreateResponseDTO createComplaint(String orgId, String userId, String userName, String subjectCategory,
            String description);

    /**
     * Same as {@link #createComplaint(String, String, String, String, String)}, for the
     * officer-assisted intake path (POST /complaints). The following functionality is contained in
     * this method.
     *
     * <p>1. Creates the complaint, same as the citizen self-service path.
     * <p>2. Records a CREATE audit event on the new complaint's timeline, atomically with the
     * insert, identifying which officer (or SYSTEM process) performed the intake.
     *
     * @param orgId           tenant/organization the complaint belongs to
     * @param userId          Data Principal the complaint is lodged for
     * @param userName        best-effort display name for userId, as above
     * @param subjectCategory category of the complaint subject
     * @param description     free-text description of the complaint
     * @param actorUserId     resolved, authenticated caller performing the intake - never
     *                        client-supplied, the same rule every other actor-identity field in
     *                        this codebase follows
     * @param actorRole       resolved, authenticated caller's role
     * @return the newly created complaint
     * @throws ComplaintException thrown if the request fails validation
     */
    ComplaintCreateResponseDTO createComplaint(String orgId, String userId, String userName, String subjectCategory,
            String description, String actorUserId, String actorRole);

    /**
     * Fetches core complaint fields for GET /complaints/{complaintId} - attachments are composed
     * in by the handler. Also used by other services (events, attachments) that need to confirm a
     * complaint exists/belongs to the org before acting on it, without duplicating that existence
     * check in every DAO.
     *
     * @param orgId       tenant/organization the complaint belongs to
     * @param complaintId complaint to fetch
     * @return the complaint
     * @throws ComplaintException thrown with a 404 status if the complaint doesn't exist for this
     *                            org
     */
    Complaint getComplaint(String orgId, String complaintId);

    /**
     * Same as {@link #getComplaint(String, String)}, but additionally raises a 404
     * ComplaintException (not a 403 - see complaint-server-API.yaml, which is explicit that
     * /me/* must not confirm a complaint's existence to a caller who doesn't own it) if the
     * complaint's userId does not match ownerUserId. Used by every /me/* handler method that acts
     * on a single complaintId.
     *
     * @param orgId       tenant/organization the complaint belongs to
     * @param complaintId complaint to fetch
     * @param ownerUserId Data Principal expected to own the complaint
     * @return the complaint
     * @throws ComplaintException thrown with a 404 status if the complaint doesn't exist for this
     *                            org or does not belong to ownerUserId
     */
    Complaint getOwnedComplaint(String orgId, String complaintId, String ownerUserId);

    /**
     * Lists complaints for an org with optional status/priority/userId filters, sorting, and
     * limit/offset pagination.
     *
     * @param orgId    tenant/organization to list complaints for
     * @param status   optional status filter
     * @param priority optional priority filter
     * @param userId   optional Data Principal filter
     * @param limit    maximum number of rows to return
     * @param offset   number of matching rows to skip
     * @param sort     sort order
     * @param totalOut out-param - see ComplaintDAO#listComplaints; after the call,
     *                 {@code totalOut[0]} holds the total row count matching the filters,
     *                 ignoring limit/offset
     * @return the page of complaints matching the filters
     */
    List<Complaint> listComplaints(String orgId, String status, String priority, String userId, int limit,
            int offset, String sort, int[] totalOut);

    /**
     * Computes org-wide counts for the officer/admin queue's summary tiles.
     *
     * @param orgId tenant/organization to compute stats for
     * @return the queue stats - see ComplaintDAO#getQueueStats
     */
    ComplaintQueueStatsResponseDTO getQueueStats(String orgId);
}
