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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatutoryDuePeriodPolicyTest {

    private static final String PROP = "CO_STATUTORY_DUE_PERIOD_DAYS";

    @AfterEach
    void clearSystemProperty() {
        System.clearProperty(PROP);
    }

    @Test
    void defaultsToNinetyDaysWhenPropertyNotSet() {
        System.clearProperty(PROP);

        assertEquals(90L * 24 * 60 * 60 * 1000, StatutoryDuePeriodPolicy.getDuePeriodMillis());
    }

    @Test
    void usesConfiguredDaysWhenPropertyIsSet() {
        System.setProperty(PROP, "30");

        assertEquals(30L * 24 * 60 * 60 * 1000, StatutoryDuePeriodPolicy.getDuePeriodMillis());
    }

    @Test
    void fallsBackToDefaultWhenPropertyIsNotAValidNumber() {
        System.setProperty(PROP, "not-a-number");

        assertEquals(90L * 24 * 60 * 60 * 1000, StatutoryDuePeriodPolicy.getDuePeriodMillis());
    }
}
