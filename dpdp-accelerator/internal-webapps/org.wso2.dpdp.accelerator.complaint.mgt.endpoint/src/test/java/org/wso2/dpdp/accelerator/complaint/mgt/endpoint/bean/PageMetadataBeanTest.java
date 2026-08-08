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

class PageMetadataBeanTest {

    @Test
    void allArgsConstructorPopulatesEveryField() {
        PageMetadataBean bean = new PageMetadataBean(42, 10, 5, 20);

        assertEquals(42, bean.getTotal());
        assertEquals(10, bean.getOffset());
        assertEquals(5, bean.getCount());
        assertEquals(20, bean.getLimit());
    }

    @Test
    void noArgsConstructorAndSettersRoundTrip() {
        PageMetadataBean bean = new PageMetadataBean();
        bean.setTotal(1);
        bean.setOffset(2);
        bean.setCount(3);
        bean.setLimit(4);

        assertEquals(1, bean.getTotal());
        assertEquals(2, bean.getOffset());
        assertEquals(3, bean.getCount());
        assertEquals(4, bean.getLimit());
    }
}
