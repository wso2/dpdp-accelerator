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

package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DateTimeUtilTest {

    @Test
    void toIsoConvertsEpochMillisToIso8601() {
        assertEquals("1970-01-01T00:00:00Z", DateTimeUtil.toIso(0L));
    }

    @Test
    void fromIsoConvertsIso8601ToEpochMillis() {
        assertEquals(0L, DateTimeUtil.fromIso("1970-01-01T00:00:00Z"));
    }

    @Test
    void toIsoAndFromIsoRoundTrip() {
        long epochMillis = 1_775_000_000_123L;

        String iso = DateTimeUtil.toIso(epochMillis);

        assertEquals(epochMillis, DateTimeUtil.fromIso(iso));
    }

    @Test
    void fromIsoThrowsIllegalArgumentExceptionForInvalidInput() {
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> DateTimeUtil.fromIso("not-a-date"));

        assertEquals("Invalid date-time value: not-a-date", ex.getMessage());
    }
}
