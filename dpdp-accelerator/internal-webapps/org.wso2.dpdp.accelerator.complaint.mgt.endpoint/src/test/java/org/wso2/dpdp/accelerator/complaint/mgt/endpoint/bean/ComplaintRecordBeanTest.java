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
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintAttachment;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.util.DateTimeUtil;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComplaintRecordBeanTest {

    private Complaint sampleComplaint() {
        return new Complaint("c1", "org1", "user1", "CMP-2026-00001", "DATA_BREACH", "CRITICAL", "OPEN", "desc", 1L,
                2L, 3L);
    }

    @Test
    void fromMapsEveryFieldAndEachAttachment() {
        ComplaintAttachment attachment = new ComplaintAttachment();
        attachment.setAttachmentId("a1");
        attachment.setFileName("a.pdf");
        attachment.setContentType("application/pdf");
        attachment.setSizeBytesOverride(100L);

        ComplaintRecordBean bean = ComplaintRecordBean.from(sampleComplaint(), List.of(attachment));

        assertEquals("c1", bean.getId());
        assertEquals("CMP-2026-00001", bean.getReferenceId());
        assertEquals("DATA_BREACH", bean.getSubjectCategory());
        assertEquals("CRITICAL", bean.getPriority());
        assertEquals("OPEN", bean.getStatus());
        assertEquals("user1", bean.getUserId());
        assertEquals("desc", bean.getDescription());
        assertEquals(DateTimeUtil.toIso(1L), bean.getSubmittedAt());
        assertEquals(DateTimeUtil.toIso(2L), bean.getUpdatedAt());
        assertEquals(DateTimeUtil.toIso(3L), bean.getStatutoryDueDate());
        assertEquals(1, bean.getAttachments().size());
        assertEquals("a1", bean.getAttachments().get(0).getAttachmentId());
    }

    @Test
    void fromProducesAnEmptyAttachmentListWhenGivenNull() {
        ComplaintRecordBean bean = ComplaintRecordBean.from(sampleComplaint(), null);

        assertTrue(bean.getAttachments().isEmpty());
    }

    @Test
    void noArgsConstructorAndSettersRoundTrip() {
        ComplaintRecordBean bean = new ComplaintRecordBean();
        bean.setId("c2");
        bean.setReferenceId("CMP-2026-00002");
        bean.setSubjectCategory("OTHER");
        bean.setPriority("LOW");
        bean.setStatus("RESOLVED");
        bean.setUserId("user2");
        bean.setDescription("desc2");
        bean.setAttachments(List.of());
        bean.setSubmittedAt("2026-01-01T00:00:00Z");
        bean.setUpdatedAt("2026-01-02T00:00:00Z");
        bean.setStatutoryDueDate("2026-04-01T00:00:00Z");

        assertEquals("c2", bean.getId());
        assertEquals("CMP-2026-00002", bean.getReferenceId());
        assertEquals("OTHER", bean.getSubjectCategory());
        assertEquals("LOW", bean.getPriority());
        assertEquals("RESOLVED", bean.getStatus());
        assertEquals("user2", bean.getUserId());
        assertEquals("desc2", bean.getDescription());
        assertTrue(bean.getAttachments().isEmpty());
        assertEquals("2026-01-01T00:00:00Z", bean.getSubmittedAt());
        assertEquals("2026-01-02T00:00:00Z", bean.getUpdatedAt());
        assertEquals("2026-04-01T00:00:00Z", bean.getStatutoryDueDate());
    }
}
