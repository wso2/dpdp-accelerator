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

package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean;

import org.junit.jupiter.api.Test;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.util.DateTimeUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComplaintCommentCreateResponseBeanTest {

    @Test
    void fromMapsEveryField() {
        ComplaintEvent event = new ComplaintEvent("e1", "org1", "c1", "user1", "USER", true, "hello", "OPEN",
                "IN_PROGRESS", 100L);

        ComplaintCommentCreateResponseBean bean = ComplaintCommentCreateResponseBean.from(event);

        assertEquals("e1", bean.getId());
        assertEquals("user1", bean.getActorUserId());
        assertEquals("USER", bean.getActorRole());
        assertEquals("hello", bean.getMessage());
        assertTrue(bean.isPublic());
        assertEquals("OPEN", bean.getFromStatus());
        assertEquals("IN_PROGRESS", bean.getToStatus());
        assertEquals(DateTimeUtil.toIso(100L), bean.getCreatedTime());
    }

    @Test
    void noArgsConstructorAndSettersRoundTrip() {
        ComplaintCommentCreateResponseBean bean = new ComplaintCommentCreateResponseBean();
        bean.setId("e2");
        bean.setActorUserId("officer1");
        bean.setActorRole("COMPLAINT_OFFICER");
        bean.setMessage("note");
        bean.setPublic(false);
        bean.setFromStatus("IN_PROGRESS");
        bean.setToStatus("RESOLVED");
        bean.setCreatedTime("2026-01-01T00:00:00Z");

        assertEquals("e2", bean.getId());
        assertEquals("officer1", bean.getActorUserId());
        assertEquals("COMPLAINT_OFFICER", bean.getActorRole());
        assertEquals("note", bean.getMessage());
        assertEquals(false, bean.isPublic());
        assertEquals("IN_PROGRESS", bean.getFromStatus());
        assertEquals("RESOLVED", bean.getToStatus());
        assertEquals("2026-01-01T00:00:00Z", bean.getCreatedTime());
    }
}
