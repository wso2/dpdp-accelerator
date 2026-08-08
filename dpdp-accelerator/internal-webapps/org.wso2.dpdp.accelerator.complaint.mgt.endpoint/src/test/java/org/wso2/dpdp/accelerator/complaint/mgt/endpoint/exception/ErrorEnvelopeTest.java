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

package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ErrorEnvelopeTest {

    @Test
    void noArgsConstructorGeneratesATraceId() {
        ErrorEnvelope envelope = new ErrorEnvelope();

        assertNotNull(envelope.getTraceId());
    }

    @Test
    void allArgsConstructorUsesGivenTraceIdWhenPresent() {
        ErrorEnvelope envelope = new ErrorEnvelope("CO-4040", "Not found", "desc", "trace-1");

        assertEquals("CO-4040", envelope.getCode());
        assertEquals("Not found", envelope.getMessage());
        assertEquals("desc", envelope.getDescription());
        assertEquals("trace-1", envelope.getTraceId());
    }

    @Test
    void allArgsConstructorGeneratesTraceIdWhenGivenNull() {
        ErrorEnvelope envelope = new ErrorEnvelope("CO-4040", "Not found", "desc", null);

        assertNotNull(envelope.getTraceId());
    }

    @Test
    void settersUpdateAllFields() {
        ErrorEnvelope envelope = new ErrorEnvelope();
        envelope.setCode("CO-5000");
        envelope.setMessage("Internal error");
        envelope.setDescription("desc");
        envelope.setTraceId("trace-2");

        assertEquals("CO-5000", envelope.getCode());
        assertEquals("Internal error", envelope.getMessage());
        assertEquals("desc", envelope.getDescription());
        assertEquals("trace-2", envelope.getTraceId());
    }
}
