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

package org.wso2.dpdp.accelerator.identity.extensions.notification;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * {@link EmailTemplateConfigLoader} caches its result in a static field for the JVM's lifetime,
 * so every test resets that cache (via reflection, the same technique
 * {@code DPDPConfigParserTest} already uses on {@code DPDPConfigParser}'s own private field) and
 * clears the {@code carbon.config.dir.path} system property it reads, so tests never leak state
 * into each other or into other test classes sharing this module's reused surefire fork.
 */
public class EmailTemplateConfigLoaderTest {

    private static final String CARBON_CONFIG_DIR_PROPERTY = "carbon.config.dir.path";

    @BeforeMethod
    public void resetCacheBeforeEachTest() throws Exception {

        resetCache();
        System.clearProperty(CARBON_CONFIG_DIR_PROPERTY);
    }

    @AfterMethod
    public void resetCacheAndSystemPropertyAfterEachTest() throws Exception {

        resetCache();
        System.clearProperty(CARBON_CONFIG_DIR_PROPERTY);
    }

    @Test
    public void returnsEmptyWhenNoConfigDirCanBeResolved() {

        System.clearProperty(CARBON_CONFIG_DIR_PROPERTY);

        assertFalse(EmailTemplateConfigLoader.getTemplateContent("ComplaintCreated").isPresent());
    }

    @Test
    public void returnsEmptyWhenTheFileDoesNotExist() throws IOException {

        Path configDir = Files.createTempDirectory("email-dpdp-config-test-missing");
        System.setProperty(CARBON_CONFIG_DIR_PROPERTY, configDir.toString());

        assertFalse(EmailTemplateConfigLoader.getTemplateContent("ComplaintCreated").isPresent());
    }

    @Test
    public void readsConfiguredSubjectAndBodyFromXml() throws IOException {

        writeConfigFile("<configurations>"
                + "<configuration type=\"ComplaintCreated\" locale=\"en_US\" emailContentType=\"text/html\">"
                + "<subject>Custom subject {{reference-id}}</subject>"
                + "<body><![CDATA[<p>Custom body {{reference-id}}</p>]]></body>"
                + "</configuration>"
                + "</configurations>");

        Optional<EmailTemplateConfigLoader.TemplateContent> content =
                EmailTemplateConfigLoader.getTemplateContent("ComplaintCreated");

        assertTrue(content.isPresent());
        assertEquals(content.get().getSubject(), "Custom subject {{reference-id}}");
        assertEquals(content.get().getBody(), "<p>Custom body {{reference-id}}</p>");
    }

    @Test
    public void returnsEmptyForATypeNotPresentInTheFile() throws IOException {

        writeConfigFile("<configurations>"
                + "<configuration type=\"ComplaintCreated\" locale=\"en_US\" emailContentType=\"text/html\">"
                + "<subject>Custom subject</subject>"
                + "<body><![CDATA[<p>Custom body</p>]]></body>"
                + "</configuration>"
                + "</configurations>");

        assertFalse(EmailTemplateConfigLoader.getTemplateContent("ComplaintCommentAdded").isPresent());
    }

    @Test
    public void skipsAnIncompleteConfigurationElement() throws IOException {

        writeConfigFile("<configurations>"
                + "<configuration type=\"ComplaintCreated\" locale=\"en_US\" emailContentType=\"text/html\">"
                + "<subject>Only a subject, no body</subject>"
                + "</configuration>"
                + "</configurations>");

        assertFalse(EmailTemplateConfigLoader.getTemplateContent("ComplaintCreated").isPresent());
    }

    @Test
    public void returnsEmptyWhenTheFileIsMalformed() throws IOException {

        writeConfigFile("<configurations><configuration type=\"ComplaintCreated\">"
                + "<subject>Unclosed");

        assertFalse(EmailTemplateConfigLoader.getTemplateContent("ComplaintCreated").isPresent());
    }

    private void writeConfigFile(String xml) throws IOException {

        Path configDir = Files.createTempDirectory("email-dpdp-config-test");
        Path emailDir = Files.createDirectories(configDir.resolve("email"));
        Files.write(emailDir.resolve("email-dpdp-config.xml"), xml.getBytes(StandardCharsets.UTF_8));
        System.setProperty(CARBON_CONFIG_DIR_PROPERTY, configDir.toString());
    }

    private static void resetCache() throws Exception {

        Field field = EmailTemplateConfigLoader.class.getDeclaredField("templatesByType");
        field.setAccessible(true);
        field.set(null, null);
    }
}
