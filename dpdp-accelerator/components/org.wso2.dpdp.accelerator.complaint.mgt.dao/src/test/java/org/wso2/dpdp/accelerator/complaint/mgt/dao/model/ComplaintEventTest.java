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

package org.wso2.dpdp.accelerator.complaint.mgt.dao.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ComplaintEventTest {

    @Test
    void deriveEntryTypeReturnsStatusChangeWhenToStatusIsPresent() {
        ComplaintEvent event = new ComplaintEvent("e1", "org1", "c1", "u1", "COMPLAINT_OFFICER", true, "note",
                "OPEN", "IN_PROGRESS", 100L);

        assertEquals("STATUS_CHANGE", event.deriveEntryType());
    }

    @Test
    void deriveEntryTypeReturnsCommentWhenPublicAndNoStatusChange() {
        ComplaintEvent event = new ComplaintEvent("e1", "org1", "c1", "u1", "USER", true, "hello", null, null, 100L);

        assertEquals("COMMENT", event.deriveEntryType());
    }

    @Test
    void deriveEntryTypeReturnsInternalNoteWhenNotPublicAndNoStatusChange() {
        ComplaintEvent event = new ComplaintEvent("e1", "org1", "c1", "u1", "COMPLAINT_OFFICER", false,
                "internal note", null, null, 100L);

        assertEquals("INTERNAL_NOTE", event.deriveEntryType());
    }

    @Test
    void noArgsConstructorAndSettersRoundTrip() {
        ComplaintEvent event = new ComplaintEvent();
        event.setEventId("e2");
        event.setOrgId("org2");
        event.setComplaintId("c2");
        event.setActorUserId("u2");
        event.setActorRole("USER");
        event.setPublic(true);
        event.setComment("a comment");
        event.setFromStatus("OPEN");
        event.setToStatus("IN_PROGRESS");
        event.setActionTime(123L);

        assertEquals("e2", event.getEventId());
        assertEquals("org2", event.getOrgId());
        assertEquals("c2", event.getComplaintId());
        assertEquals("u2", event.getActorUserId());
        assertEquals("USER", event.getActorRole());
        assertEquals(true, event.isPublic());
        assertEquals("a comment", event.getComment());
        assertEquals("OPEN", event.getFromStatus());
        assertEquals("IN_PROGRESS", event.getToStatus());
        assertEquals(123L, event.getActionTime());
    }
}
