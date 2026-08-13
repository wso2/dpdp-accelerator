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

package org.wso2.dpdp.accelerator.complaint.mgt.service.util;

import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintDAO;

import java.time.ZoneOffset;
import java.time.Instant;

/**
 * Generates the human-facing REFERENCE_ID (e.g. "CMP-2026-04821") described in the ER diagram.
 */
public class ReferenceIdGenerator {

    private static final String PREFIX = "CMP";

    private ReferenceIdGenerator() {
    }

    public static String generate(ComplaintDAO complaintDAO, String orgId, long createdTimeMillis) {
        int year = Instant.ofEpochMilli(createdTimeMillis).atZone(ZoneOffset.UTC).get(java.time.temporal.ChronoField.YEAR);
        String likePattern = PREFIX + "-" + year + "-%";
        int existingCount = complaintDAO.countByReferenceIdPrefix(orgId, likePattern);
        int nextSeq = existingCount + 1;
        return String.format("%s-%d-%05d", PREFIX, year, nextSeq);
    }
}
