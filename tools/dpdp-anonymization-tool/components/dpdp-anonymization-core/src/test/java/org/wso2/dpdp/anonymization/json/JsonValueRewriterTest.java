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

package org.wso2.dpdp.anonymization.json;

import org.junit.jupiter.api.Test;
import org.wso2.dpdp.anonymization.model.IdentityEvidence;
import org.wso2.dpdp.anonymization.validation.AnonymizationException;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonValueRewriterTest {

    private static final String SOURCE = "18b7c17d-ef74-48c5-a0c8-a1df9b21ff87";
    private static final String TARGET = "216d6aac-7e84-4484-a71e-c52f89b3cb1d";

    @Test
    void rewritesOnlyConfiguredExactScalars() throws Exception {
        String json = "{\"userId\":\"" + SOURCE + "\",\"description\":\"prefix " + SOURCE
                + " suffix\",\"nested\":[{\"userId\":\"" + SOURCE + "\"}]}";
        JsonValueRewriter.RewriteResult result = new JsonValueRewriter().rewriteEventPayload(json,
                new IdentityEvidence(SOURCE, Collections.<String>emptySet()), TARGET,
                Arrays.asList(JsonPathExpression.parse("/userId"),
                        JsonPathExpression.parse("/nested/*/userId")));

        assertEquals(2, result.getReplacementCount());
        assertTrue(result.getJson().contains("prefix " + SOURCE + " suffix"));
    }

    @Test
    void rejectsNonStringConfiguredValue() throws Exception {
        assertThrows(AnonymizationException.class, () -> new JsonValueRewriter().rewriteEventPayload(
                "{\"userId\":42}", new IdentityEvidence(SOURCE, Collections.<String>emptySet()), TARGET,
                Collections.singletonList(JsonPathExpression.parse("/userId"))));
    }

    @Test
    void rejectsInvalidPointerEscape() {
        assertThrows(AnonymizationException.class, () -> JsonPathExpression.parse("/user~2id"));
    }
}
