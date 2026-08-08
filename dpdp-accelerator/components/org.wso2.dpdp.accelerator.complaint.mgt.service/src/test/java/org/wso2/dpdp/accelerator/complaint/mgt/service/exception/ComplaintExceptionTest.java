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

package org.wso2.dpdp.accelerator.complaint.mgt.service.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ComplaintExceptionTest {

    @Test
    void exposesCodeMessageDescriptionAndStatusCode() {
        ComplaintException exception = new ComplaintException("CO-4040", "Complaint not found",
                "No complaint exists with the given ID for this organization.", 404);

        assertEquals("CO-4040", exception.getCode());
        assertEquals("Complaint not found", exception.getMessage());
        assertEquals("No complaint exists with the given ID for this organization.", exception.getDescription());
        assertEquals(404, exception.getStatusCode());
    }

    @Test
    void isARuntimeException() {
        ComplaintException exception = new ComplaintException("CO-5000", "Internal error", "desc", 500);

        assertEquals(RuntimeException.class, exception.getClass().getSuperclass());
    }
}
