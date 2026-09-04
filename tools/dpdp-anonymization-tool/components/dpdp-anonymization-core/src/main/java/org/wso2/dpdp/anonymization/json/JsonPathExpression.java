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

import org.wso2.dpdp.anonymization.validation.AnonymizationException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Restricted JSON Pointer syntax with one extension: '*' selects every array element.
 */
public final class JsonPathExpression {

    private final String source;
    private final List<String> segments;

    private JsonPathExpression(String source, List<String> segments) {
        this.source = source;
        this.segments = segments;
    }

    public static JsonPathExpression parse(String path) throws AnonymizationException {
        if (path == null || path.isEmpty() || path.charAt(0) != '/') {
            throw new AnonymizationException("JSON path must be an absolute pointer: " + path);
        }
        String[] raw = path.substring(1).split("/", -1);
        List<String> segments = new ArrayList<>();
        for (String segment : raw) {
            String decoded = decode(segment, path);
            if (decoded.isEmpty()) {
                throw new AnonymizationException("JSON path cannot contain an empty segment: " + path);
            }
            segments.add(decoded);
        }
        return new JsonPathExpression(path, Collections.unmodifiableList(segments));
    }

    private static String decode(String value, String path) throws AnonymizationException {
        StringBuilder decoded = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current != '~') {
                decoded.append(current);
                continue;
            }
            if (i + 1 >= value.length()) {
                throw new AnonymizationException("Invalid JSON pointer escape: " + path);
            }
            char escaped = value.charAt(++i);
            if (escaped == '0') {
                decoded.append('~');
            } else if (escaped == '1') {
                decoded.append('/');
            } else {
                throw new AnonymizationException("Invalid JSON pointer escape: " + path);
            }
        }
        return decoded.toString();
    }

    public List<String> getSegments() {
        return segments;
    }

    @Override
    public String toString() {
        return source;
    }
}
