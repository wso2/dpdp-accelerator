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

package org.wso2.dpdp.accelerator.complaint.mgt.dao.queries;

import java.util.Locale;

// Mirrors org.wso2.dpdp.accelerator.event.notifications.dao.queries.QueryBuilderUtils.
public final class QueryBuilderUtils {

    private static final String LIKE_ESCAPE_CHARACTER = "!";

    private QueryBuilderUtils() {
    }

    public static String escapeLikePattern(String text) {
        if (text == null) {
            return "";
        }
        return text.replace(LIKE_ESCAPE_CHARACTER, LIKE_ESCAPE_CHARACTER + LIKE_ESCAPE_CHARACTER)
                .replace("%", LIKE_ESCAPE_CHARACTER + "%")
                .replace("_", LIKE_ESCAPE_CHARACTER + "_");
    }

    public static String buildCaseInsensitiveContainsPattern(String text) {
        String normalized = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        return "%" + escapeLikePattern(normalized) + "%";
    }

    public static String buildEscapedLikePredicate(String expression) {
        return expression + " LIKE ? ESCAPE '" + LIKE_ESCAPE_CHARACTER + "'";
    }
}
