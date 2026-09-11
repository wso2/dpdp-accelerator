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

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMException;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.utils.CarbonUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

/**
 * Loads the bundled default subject/body for the complaint notification email templates from
 * {@code <IS_HOME>/repository/conf/email/email-dpdp-config.xml}, mirroring the shape of this
 * product's own {@code repository/conf/email/email-admin-config.xml}. Lets the bundled default
 * be changed by editing this file directly, with no Java rebuild - only affects tenants
 * provisioned after the edit, since an already-provisioned tenant's template is never rewritten
 * (see {@link EmailTemplateProvisioningUtil}). That class falls back to its own bundled classpath
 * resource for any template type this file doesn't define, or if the file itself is missing or
 * fails to parse - the file is entirely optional.
 */
final class EmailTemplateConfigLoader {

    private static final Log LOG = LogFactory.getLog(EmailTemplateConfigLoader.class);
    private static final String CONFIG_DIRECTORY = "email";
    private static final String CONFIG_FILE_NAME = "email-dpdp-config.xml";
    private static final String ELEMENT_CONFIGURATION = "configuration";
    private static final String ATTR_TYPE = "type";
    private static final String ELEMENT_SUBJECT = "subject";
    private static final String ELEMENT_BODY = "body";

    private static final Object LOCK = new Object();
    private static Map<String, TemplateContent> templatesByType;

    private EmailTemplateConfigLoader() {

    }

    /**
     * @return the file-configured subject/body for {@code templateType}, or empty if the file
     * doesn't exist, failed to parse, or has no entry for that type.
     */
    static Optional<TemplateContent> getTemplateContent(String templateType) {

        return Optional.ofNullable(getTemplates().get(templateType));
    }

    private static Map<String, TemplateContent> getTemplates() {

        synchronized (LOCK) {
            if (templatesByType == null) {
                templatesByType = loadTemplates();
            }
            return templatesByType;
        }
    }

    private static Map<String, TemplateContent> loadTemplates() {

        File configFile;
        try {
            configFile = new File(CarbonUtils.getCarbonConfigDirPath(),
                    CONFIG_DIRECTORY + File.separator + CONFIG_FILE_NAME);
        } catch (RuntimeException e) {
            // CarbonUtils.getCarbonConfigDirPath() throws if neither the carbon.home system
            // property nor the CARBON_HOME env var is set - never expected in a real IS runtime,
            // but this loader must not be the reason provisioning fails outright over it.
            LOG.debug("Could not resolve the carbon config directory; falling back to the bundled classpath "
                    + "default for every complaint email template.", e);
            return Collections.emptyMap();
        }
        if (!configFile.exists()) {
            LOG.debug("No " + CONFIG_FILE_NAME + " found at " + configFile
                    + "; falling back to the bundled classpath default for every complaint email template.");
            return Collections.emptyMap();
        }

        try (InputStream inStream = Files.newInputStream(configFile.toPath())) {
            StAXOMBuilder builder = new StAXOMBuilder(inStream);
            OMElement rootElement = builder.getDocumentElement();
            Map<String, TemplateContent> templates = new HashMap<>();
            for (Iterator<OMElement> children = rootElement.getChildElements(); children.hasNext();) {
                OMElement configuration = children.next();
                if (!ELEMENT_CONFIGURATION.equals(configuration.getLocalName())) {
                    continue;
                }
                readConfiguration(configuration, templates);
            }
            LOG.debug("Loaded " + templates.size() + " complaint email template override(s) from " + configFile);
            return Collections.unmodifiableMap(templates);
        } catch (IOException | XMLStreamException | OMException e) {
            LOG.error("Error parsing " + configFile + "; falling back to the bundled classpath default for every "
                    + "complaint email template.", e);
            return Collections.emptyMap();
        }
    }

    private static void readConfiguration(OMElement configuration, Map<String, TemplateContent> templates) {

        String type = configuration.getAttributeValue(new QName(ATTR_TYPE));
        String subject = null;
        String body = null;
        for (Iterator<OMElement> fields = configuration.getChildElements(); fields.hasNext();) {
            OMElement field = fields.next();
            if (ELEMENT_SUBJECT.equals(field.getLocalName())) {
                subject = field.getText();
            } else if (ELEMENT_BODY.equals(field.getLocalName())) {
                body = field.getText();
            }
        }

        if (type == null || subject == null || body == null) {
            LOG.debug("Skipping an incomplete <" + ELEMENT_CONFIGURATION + "> element (missing type/subject/body) "
                    + "in " + CONFIG_FILE_NAME);
            return;
        }
        templates.put(type, new TemplateContent(subject, body));
    }

    /** Immutable subject/body pair read from one {@code <configuration>} element. */
    static final class TemplateContent {

        private final String subject;
        private final String body;

        TemplateContent(String subject, String body) {

            this.subject = subject;
            this.body = body;
        }

        String getSubject() {

            return subject;
        }

        String getBody() {

            return body;
        }
    }
}
