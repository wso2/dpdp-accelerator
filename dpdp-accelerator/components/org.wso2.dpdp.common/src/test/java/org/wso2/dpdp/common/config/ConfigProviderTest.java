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

package org.wso2.dpdp.common.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigProviderTest {

    @AfterEach
    void clearCarbonHome() {
        System.clearProperty("carbon.home");
    }

    private void writeDeploymentToml(Path carbonHome, String content) throws IOException {
        Path confDir = carbonHome.resolve("repository").resolve("conf");
        Files.createDirectories(confDir);
        Files.writeString(confDir.resolve("deployment.toml"), content);
    }

    @Test
    void getStringReturnsDefaultWhenCarbonHomeIsNotSet() {
        String value = ConfigProvider.getString("datasource.ComplaintDB.url", "default-url");

        assertEquals("default-url", value);
    }

    @Test
    void getStringReturnsDefaultWhenDeploymentTomlDoesNotExist(@TempDir Path carbonHome) {
        System.setProperty("carbon.home", carbonHome.toString());

        String value = ConfigProvider.getString("datasource.ComplaintDB.url", "default-url");

        assertEquals("default-url", value);
    }

    @Test
    void getStringResolvesNestedTableKey(@TempDir Path carbonHome) throws IOException {
        writeDeploymentToml(carbonHome, "[datasource.ComplaintDB]\n"
                + "url = \"jdbc:mysql://db-host:3306/complaint_db\"\n"
                + "username = \"co_user\"\n");
        System.setProperty("carbon.home", carbonHome.toString());

        assertEquals("jdbc:mysql://db-host:3306/complaint_db",
                ConfigProvider.getString("datasource.ComplaintDB.url", "default-url"));
        assertEquals("co_user", ConfigProvider.getString("datasource.ComplaintDB.username", "default-user"));
    }

    @Test
    void getStringReturnsDefaultWhenKeyIsAbsentFromAnOtherwiseValidFile(@TempDir Path carbonHome) throws IOException {
        writeDeploymentToml(carbonHome, "[datasource.ComplaintDB]\n"
                + "url = \"jdbc:mysql://db-host:3306/complaint_db\"\n");
        System.setProperty("carbon.home", carbonHome.toString());

        assertEquals("default-driver", ConfigProvider.getString("datasource.ComplaintDB.driver", "default-driver"));
    }

    @Test
    void getStringReturnsDefaultWhenDeploymentTomlIsMalformed(@TempDir Path carbonHome) throws IOException {
        writeDeploymentToml(carbonHome, "this is not valid toml [[[");
        System.setProperty("carbon.home", carbonHome.toString());

        assertEquals("default-url", ConfigProvider.getString("datasource.ComplaintDB.url", "default-url"));
    }
}
