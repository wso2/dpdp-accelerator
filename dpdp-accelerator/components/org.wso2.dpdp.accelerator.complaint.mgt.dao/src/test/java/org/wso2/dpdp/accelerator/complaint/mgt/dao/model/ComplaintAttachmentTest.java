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

class ComplaintAttachmentTest {

    @Test
    void getSizeBytesPrefersLoadedFileDataOverOverride() {
        ComplaintAttachment attachment = new ComplaintAttachment("a1", "org1", "c1", null, "file.pdf",
                "application/pdf", new byte[]{1, 2, 3, 4, 5}, 100L);
        attachment.setSizeBytesOverride(999L);

        assertEquals(5L, attachment.getSizeBytes());
    }

    @Test
    void getSizeBytesFallsBackToOverrideWhenFileDataNotLoaded() {
        ComplaintAttachment attachment = new ComplaintAttachment();
        attachment.setSizeBytesOverride(42L);

        assertEquals(42L, attachment.getSizeBytes());
    }

    @Test
    void getSizeBytesReturnsZeroWhenNeitherFileDataNorOverrideIsSet() {
        ComplaintAttachment attachment = new ComplaintAttachment();

        assertEquals(0L, attachment.getSizeBytes());
    }

    @Test
    void allArgsConstructorPopulatesEveryField() {
        byte[] data = {9, 8, 7};
        ComplaintAttachment attachment = new ComplaintAttachment("a1", "org1", "c1", "e1", "file.png", "image/png",
                data, 500L);

        assertEquals("a1", attachment.getAttachmentId());
        assertEquals("org1", attachment.getOrgId());
        assertEquals("c1", attachment.getComplaintId());
        assertEquals("e1", attachment.getComplaintEventId());
        assertEquals("file.png", attachment.getFileName());
        assertEquals("image/png", attachment.getContentType());
        assertEquals(data, attachment.getFileData());
        assertEquals(500L, attachment.getCreatedTime());
    }
}
