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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintDAO;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferenceIdGeneratorTest {

    @Mock
    private ComplaintDAO complaintDAO;

    @Test
    void generatesFirstReferenceIdOfTheYearWhenNoneExistYet() {
        long createdTime = ZonedDateTime.of(2026, 3, 1, 0, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli();
        when(complaintDAO.countByReferenceIdPrefix(eq("org1"), any())).thenReturn(0);

        String referenceId = ReferenceIdGenerator.generate(complaintDAO, "org1", createdTime);

        assertEquals("CMP-2026-00001", referenceId);
    }

    @Test
    void incrementsSequenceBasedOnExistingCountForTheYear() {
        long createdTime = ZonedDateTime.of(2026, 3, 1, 0, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli();
        when(complaintDAO.countByReferenceIdPrefix(eq("org1"), any())).thenReturn(4820);

        String referenceId = ReferenceIdGenerator.generate(complaintDAO, "org1", createdTime);

        assertEquals("CMP-2026-04821", referenceId);
    }

    @Test
    void queriesUsingTheYearPrefixDerivedFromCreatedTime() {
        long createdTime = ZonedDateTime.of(2025, 12, 31, 23, 59, 59, 0, ZoneOffset.UTC).toInstant().toEpochMilli();
        when(complaintDAO.countByReferenceIdPrefix(eq("org1"), any())).thenReturn(0);

        ReferenceIdGenerator.generate(complaintDAO, "org1", createdTime);

        verify(complaintDAO).countByReferenceIdPrefix("org1", "CMP-2025-%");
    }
}
