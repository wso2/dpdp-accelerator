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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class TimelineListResponseBeanTest {

    @Test
    void allArgsConstructorPopulatesEveryField() {
        List<ComplaintTimelineEntryResponseBean> data = List.of(new ComplaintTimelineEntryResponseBean());
        PageMetadataBean metadata = new PageMetadataBean(1, 0, 1, 20);

        TimelineListResponseBean bean = new TimelineListResponseBean(data, metadata);

        assertSame(data, bean.getData());
        assertSame(metadata, bean.getMetadata());
    }

    @Test
    void noArgsConstructorAndSettersRoundTrip() {
        TimelineListResponseBean bean = new TimelineListResponseBean();
        List<ComplaintTimelineEntryResponseBean> data = List.of();
        PageMetadataBean metadata = new PageMetadataBean();
        bean.setData(data);
        bean.setMetadata(metadata);

        assertEquals(data, bean.getData());
        assertSame(metadata, bean.getMetadata());
    }
}
