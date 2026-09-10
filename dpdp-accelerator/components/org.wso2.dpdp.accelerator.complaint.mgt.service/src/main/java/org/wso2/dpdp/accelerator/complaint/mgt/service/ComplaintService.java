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

import java.sql.Connection;
import java.util.List;


public interface ComplaintService {

    /**
     * Lean result for POST /complaints - no attachments field (there can't be any yet). userName is
     * a best-effort display name for userId (resolved by the caller from the token that identified
     * them, when available) - null when the complaint is lodged on the Data Principal's behalf by
     * an officer who only supplied a userId.
     */
    ComplaintCreateResponseDTO createComplaint(String orgId, String userId, String userName, String subjectCategory,
            String description);

    /**
     * Same as {@link #createComplaint(String, String, String, String, String)}, for the
     * officer-assisted intake path (POST /complaints) - additionally records a CREATE audit event
     * on the new complaint's timeline, atomically with the insert, identifying which officer (or
     * SYSTEM process) performed the intake. actorUserId/actorRole must be the resolved, authenticated
     * caller - never anything client-supplied - the same rule every other actor-identity field in
     * this codebase follows.
     */
    ComplaintCreateResponseDTO createComplaint(String orgId, String userId, String userName, String subjectCategory,
            String description, String actorUserId, String actorRole);

    /** Core complaint fields for GET /complaints/{complaintId} (attachments are composed in by the handler). */
    Complaint getComplaint(String orgId, String complaintId);

    /**
     * Fetches core complaint fields, throwing a 404 ComplaintException if it doesn't exist for this org.
     * Used by other services (events, attachments) that need to confirm a complaint exists/belongs to the
     * org before acting on it, without duplicating that existence check in every DAO.
     */
    Complaint requireComplaint(String orgId, String complaintId);

    /**
     * Same as {@link #requireComplaint(String, String)}, run against a caller-owned connection so
     * the existence check and whatever the caller does next (a read or a write) land in one
     * transaction, rather than being able to disagree if the complaint is deleted in between.
     */
    Complaint requireComplaint(Connection conn, String orgId, String complaintId);

    /**
     * Same as {@link #requireComplaint(String, String)}, but additionally raises a 404 ComplaintException (not a
     * 403 - see complaint-server-API.yaml, which is explicit that /me/* must not confirm a
     * complaint's existence to a caller who doesn't own it) if the complaint's userId does not
     * match ownerUserId. Used by every /me/* handler method that acts on a single complaintId.
     */
    Complaint requireOwnedComplaint(String orgId, String complaintId, String ownerUserId);

    /**
     * Same as {@link #requireOwnedComplaint(String, String, String)}, run against a caller-owned
     * connection so the ownership check and whatever the caller does next share one transaction -
     * same reasoning as {@link #requireComplaint(Connection, String, String)}.
     */
    Complaint requireOwnedComplaint(Connection conn, String orgId, String complaintId, String ownerUserId);

    /**
     * Lists complaints for an org with optional status/priority/userId filters, sorting, and
     * limit/offset pagination. totalOut is an out-param - see ComplaintDAO#listComplaints.
     */
    List<Complaint> listComplaints(String orgId, String status, String priority, String userId, int limit,
            int offset, String sort, int[] totalOut);

    /** Org-wide counts for the officer/admin queue's summary tiles - see ComplaintDAO#getQueueStats. */
    ComplaintQueueStatsResponseDTO getQueueStats(String orgId);
}
