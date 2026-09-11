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
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package org.wso2.dpdp.accelerator.complaint.mgt.service.notification;

import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;
import org.testng.annotations.Test;

/**
 * Both methods must be true no-ops - no exception, no side effect - since this is what
 * {@code ComplaintServiceComponent} wires in for every complaint create/comment call when
 * {@code Complaints.EmailNotificationsEnabled} is {@code false}.
 */
public class NoOpNotificationClientTest {

    private final NoOpNotificationClient client = new NoOpNotificationClient();

    private Complaint complaint() {
        return new Complaint(
                "c1",
                "org1",
                "user1",
                "User One",
                "CMP-2026-00001",
                "DATA_BREACH",
                "CRITICAL",
                "OPEN",
                "desc",
                1L,
                2L,
                3L
        );
    }

    @Test
    public void notifyComplaintCreatedDoesNothing() {

        client.notifyComplaintCreated(complaint());
    }

    @Test
    public void notifyCommentAddedDoesNothing() {

        client.notifyCommentAdded(complaint(), new ComplaintEvent(
                "e1", "org1", "c1", "u1", "officer1", "COMPLAINT_OFFICER", true, "a comment", null, null, 1L));
    }
}
