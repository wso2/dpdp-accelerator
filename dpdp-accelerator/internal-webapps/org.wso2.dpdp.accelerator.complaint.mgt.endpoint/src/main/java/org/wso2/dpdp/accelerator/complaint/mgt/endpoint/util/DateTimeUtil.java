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

import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * The DAO/service layers store timestamps as epoch millis (bigint, per the ER diagram), while the
 * OpenAPI spec exposes them as "type: string, format: date-time" (ISO-8601). This is the single
 * conversion point between the two.
 */
public class DateTimeUtil {

    private DateTimeUtil() {
    }

    public static String toIso(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).toString();
    }

    public static long fromIso(String isoDateTime) {
        try {
            return Instant.parse(isoDateTime).toEpochMilli();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date-time value: " + isoDateTime, e);
        }
    }
}
