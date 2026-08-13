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

import static org.junit.jupiter.api.Assertions.assertEquals;

class ComplaintAttachmentResponseBeanTest {

    @Test
    void allArgsConstructorPopulatesEveryField() {
        ComplaintAttachmentResponseBean bean =
                new ComplaintAttachmentResponseBean("a1", "a.pdf", "application/pdf", 100L);

        assertEquals("a1", bean.getAttachmentId());
        assertEquals("a.pdf", bean.getFileName());
        assertEquals("application/pdf", bean.getContentType());
        assertEquals(100L, bean.getSizeBytes());
    }

    @Test
    void noArgsConstructorAndSettersRoundTrip() {
        ComplaintAttachmentResponseBean bean = new ComplaintAttachmentResponseBean();
        bean.setAttachmentId("a2");
        bean.setFileName("b.png");
        bean.setContentType("image/png");
        bean.setSizeBytes(200L);

        assertEquals("a2", bean.getAttachmentId());
        assertEquals("b.png", bean.getFileName());
        assertEquals("image/png", bean.getContentType());
        assertEquals(200L, bean.getSizeBytes());
    }
}
