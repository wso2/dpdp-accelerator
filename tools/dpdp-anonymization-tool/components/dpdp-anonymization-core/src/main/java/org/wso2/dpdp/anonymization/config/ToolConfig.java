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

package org.wso2.dpdp.anonymization.config;

import java.util.ArrayList;
import java.util.List;

public class ToolConfig {

    private DatabaseConfig database = new DatabaseConfig();
    private List<EventPayloadRule> eventPayloadRules = new ArrayList<>();
    private String eventPayloadRulesFile = "event-payload-rules.json";
    private int batchSize = 250;
    private String reportDirectory = "reports";

    public DatabaseConfig getDatabase() {
        return database;
    }

    public void setDatabase(DatabaseConfig database) {
        this.database = database;
    }

    public List<EventPayloadRule> getEventPayloadRules() {
        return eventPayloadRules;
    }

    public void setEventPayloadRules(List<EventPayloadRule> eventPayloadRules) {
        this.eventPayloadRules = eventPayloadRules == null ? new ArrayList<EventPayloadRule>() : eventPayloadRules;
    }

    public String getEventPayloadRulesFile() {
        return eventPayloadRulesFile;
    }

    public void setEventPayloadRulesFile(String eventPayloadRulesFile) {
        this.eventPayloadRulesFile = eventPayloadRulesFile;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public String getReportDirectory() {
        return reportDirectory;
    }

    public void setReportDirectory(String reportDirectory) {
        this.reportDirectory = reportDirectory;
    }
}
