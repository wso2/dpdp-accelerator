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

package org.wso2.dpdp.anonymization.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.wso2.dpdp.anonymization.model.AnonymizationRequest;
import org.wso2.dpdp.anonymization.model.AnonymizationResult;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

public final class ReportWriter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public File write(File directory, AnonymizationRequest request, AnonymizationResult result) throws IOException {
        return write(directory, request, result, null);
    }

    public File writeFailure(File directory, AnonymizationRequest request, AnonymizationResult result,
                             String failureMessage) throws IOException {
        return write(directory, request, result, failureMessage);
    }

    private File write(File directory, AnonymizationRequest request, AnonymizationResult result,
                       String failureMessage) throws IOException {
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Could not create report directory: " + directory.getAbsolutePath());
        }
        String timestamp = timestamp();
        File report = new File(directory, "dpdp-anonymization-" + timestamp + ".json");
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("runId", UUID.randomUUID().toString());
        values.put("timestamp", timestamp);
        values.put("tenant", request.getTenantDomain());
        values.put("sourceUserId", mask(request.getSourceUserId()));
        values.put("replacementUuid", mask(request.getPseudonym()));
        values.put("mode", request.getExecutionMode().name());
        values.put("status", result.getStatus().name());
        values.put("discoveredAliasCount", result.getDiscoveredAliasCount());
        values.put("counts", result.getCounts());
        if (failureMessage != null) {
            values.put("failure", failureMessage);
            values.put("databaseChangesCommitted", false);
        }
        OBJECT_MAPPER.writeValue(report, values);
        return report;
    }

    private static String timestamp() {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    static String mask(String value) {
        if (value == null || value.length() < 8) {
            return "****";
        }
        return "****" + value.substring(value.length() - 4);
    }
}
