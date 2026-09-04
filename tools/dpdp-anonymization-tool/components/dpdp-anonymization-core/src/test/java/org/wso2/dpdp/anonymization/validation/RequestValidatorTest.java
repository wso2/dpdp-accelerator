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

package org.wso2.dpdp.anonymization.validation;

import org.junit.jupiter.api.Test;
import org.wso2.dpdp.anonymization.model.AnonymizationRequest;
import org.wso2.dpdp.anonymization.model.ExecutionMode;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestValidatorTest {

    @Test
    void acceptsCanonicalDifferentUuids() {
        AnonymizationRequest request = new AnonymizationRequest("example.com",
                "18b7c17d-ef74-48c5-a0c8-a1df9b21ff87",
                "216d6aac-7e84-4484-a71e-c52f89b3cb1d",
                Collections.<String>emptySet(), ExecutionMode.DRY_RUN);
        assertDoesNotThrow(() -> RequestValidator.validate(request));
    }

    @Test
    void rejectsNonCanonicalSourceUuid() {
        AnonymizationRequest request = new AnonymizationRequest("example.com", "1234",
                "216d6aac-7e84-4484-a71e-c52f89b3cb1d",
                Collections.<String>emptySet(), ExecutionMode.DRY_RUN);
        assertThrows(AnonymizationException.class, () -> RequestValidator.validate(request));
    }

    @Test
    void rejectsSameUuid() {
        String uuid = "18b7c17d-ef74-48c5-a0c8-a1df9b21ff87";
        AnonymizationRequest request = new AnonymizationRequest("example.com", uuid, uuid,
                Collections.<String>emptySet(), ExecutionMode.DRY_RUN);
        assertThrows(AnonymizationException.class, () -> RequestValidator.validate(request));
    }
}
