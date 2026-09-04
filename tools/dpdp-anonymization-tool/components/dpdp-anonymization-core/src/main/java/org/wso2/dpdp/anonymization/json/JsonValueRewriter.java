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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.wso2.dpdp.anonymization.model.IdentityEvidence;
import org.wso2.dpdp.anonymization.validation.AnonymizationException;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public final class JsonValueRewriter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public RewriteResult rewriteConsentSnapshot(String json, IdentityEvidence evidence, String replacement)
            throws AnonymizationException {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            int replacements = rewriteAtPaths(root, evidence, replacement,
                    asPaths("/piiPrincipalId", "/authorizations/*/userId"));
            return new RewriteResult(replacements == 0 ? json : OBJECT_MAPPER.writeValueAsString(root), replacements);
        } catch (IOException e) {
            throw new AnonymizationException("Malformed consent snapshot JSON.", e);
        }
    }

    public RewriteResult rewriteEventPayload(String json, IdentityEvidence evidence, String replacement,
                                             List<JsonPathExpression> paths) throws AnonymizationException {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            int replacements = rewriteAtPaths(root, evidence, replacement, paths);
            return new RewriteResult(replacements == 0 ? json : OBJECT_MAPPER.writeValueAsString(root), replacements);
        } catch (IOException e) {
            throw new AnonymizationException("Malformed event payload JSON.", e);
        }
    }

    private int rewriteAtPaths(JsonNode root, IdentityEvidence evidence, String replacement,
                               List<JsonPathExpression> paths) throws AnonymizationException {
        int replacements = 0;
        for (JsonPathExpression path : paths) {
            replacements += rewrite(root, path.getSegments(), 0, evidence, replacement);
        }
        return replacements;
    }

    private int rewrite(JsonNode current, List<String> segments, int offset, IdentityEvidence evidence,
                        String replacement) throws AnonymizationException {
        if (current == null || offset >= segments.size()) {
            return 0;
        }
        String segment = segments.get(offset);
        boolean leaf = offset == segments.size() - 1;
        if ("*".equals(segment)) {
            if (!current.isArray()) {
                return 0;
            }
            int count = 0;
            for (JsonNode child : current) {
                count += rewrite(child, segments, offset + 1, evidence, replacement);
            }
            return count;
        }
        if (current.isObject()) {
            JsonNode child = current.get(segment);
            if (child == null) {
                return 0;
            }
            if (leaf) {
                if (!child.isTextual()) {
                    throw new AnonymizationException("Configured JSON path resolves to a non-string value: /" + segment);
                }
                if (evidence.matchesIdentifier(child.textValue())) {
                    ((ObjectNode) current).put(segment, replacement);
                    return 1;
                }
                return 0;
            }
            return rewrite(child, segments, offset + 1, evidence, replacement);
        }
        if (current.isArray()) {
            int index;
            try {
                index = Integer.parseInt(segment);
            } catch (NumberFormatException e) {
                return 0;
            }
            if (index < 0 || index >= current.size()) {
                return 0;
            }
            JsonNode child = current.get(index);
            if (leaf) {
                if (!child.isTextual()) {
                    throw new AnonymizationException("Configured JSON path resolves to a non-string array value.");
                }
                if (evidence.matchesIdentifier(child.textValue())) {
                    ((ArrayNode) current).set(index, OBJECT_MAPPER.getNodeFactory().textNode(replacement));
                    return 1;
                }
                return 0;
            }
            return rewrite(child, segments, offset + 1, evidence, replacement);
        }
        return 0;
    }

    private List<JsonPathExpression> asPaths(String first, String second) throws AnonymizationException {
        return java.util.Arrays.asList(JsonPathExpression.parse(first), JsonPathExpression.parse(second));
    }

    public static final class RewriteResult {

        private final String json;
        private final int replacementCount;

        public RewriteResult(String json, int replacementCount) {
            this.json = json;
            this.replacementCount = replacementCount;
        }

        public String getJson() {
            return json;
        }

        public int getReplacementCount() {
            return replacementCount;
        }
    }
}
