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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.identity.governance.IdentityMgtConstants;
import org.wso2.carbon.identity.governance.exceptions.notiification.NotificationTemplateManagerException;
import org.wso2.carbon.identity.governance.model.NotificationTemplate;
import org.wso2.carbon.identity.governance.service.notification.NotificationTemplateManager;
import org.wso2.dpdp.accelerator.common.util.LogSanitizer;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Registers the three complaint notification email templates for a tenant, check-then-add -
 * mirrors the idiom already used for role permissions in
 * {@code DPDPConsentPortalRoleProvisioningUtil}. A Console edit is never overwritten by a
 * later tenant-update event, and there is no path back to the bundled default once written.
 *
 * <p>The bundled default comes from {@code email-dpdp-config.xml} when present ({@link
 * EmailTemplateConfigLoader}), falling back to this class's own literals and the bundled
 * {@code complaint-email-body.html} otherwise.
 */
public final class EmailTemplateProvisioningUtil {

    private static final Log LOG = LogFactory.getLog(EmailTemplateProvisioningUtil.class);
    private static final String EMAIL_CHANNEL = "EMAIL";
    private static final String DEFAULT_LOCALE = "en_US";
    private static final String CONTENT_TYPE = "text/html";

    // Mirrors the TEMPLATE_TYPE literals EmailNotificationClient uses to fire TRIGGER_NOTIFICATION.
    private static final String TEMPLATE_TYPE_COMPLAINT_CREATED = "ComplaintCreated";
    private static final String TEMPLATE_TYPE_COMMENT_ADDED = "ComplaintCommentAdded";
    private static final String TEMPLATE_TYPE_COMPLAINT_ACKNOWLEDGED = "ComplaintAcknowledged";

    private EmailTemplateProvisioningUtil() {

    }

    public static void provisionTemplates(String tenantDomain) {

        provisionTemplate(tenantDomain, TEMPLATE_TYPE_COMPLAINT_CREATED, "New complaint filed: {{reference-id}}");
        provisionTemplate(tenantDomain, TEMPLATE_TYPE_COMMENT_ADDED, "New reply on complaint {{reference-id}}");
        provisionTemplate(tenantDomain, TEMPLATE_TYPE_COMPLAINT_ACKNOWLEDGED,
                "We've received your complaint: {{reference-id}}");
    }

    // Shared HTML shell for all three types - see that file's own header for why.
    private static final String EMAIL_BODY_RESOURCE = "/notification/complaint-email-body.html";
    private static final String EMAIL_BODY = loadResource(EMAIL_BODY_RESOURCE);

    private static String loadResource(String path) {

        try (InputStream in = EmailTemplateProvisioningUtil.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing bundled resource: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Error loading bundled resource: " + path, e);
        }
    }

    /**
     * Writes template content only if this tenant doesn't have it yet - a Console edit is
     * indistinguishable from the bundled default once written, so overwriting on update would
     * silently discard it. {@code addNotificationTemplateType} isn't upsert-safe (throws if
     * already registered); that failure is swallowed and never blocks the check/write below.
     */
    private static void provisionTemplate(String tenantDomain, String templateType, String defaultSubject) {

        NotificationTemplateManager templateManager = DPDPIdentityExtensionDataHolder.getInstance()
                .getNotificationTemplateManager();
        try {
            templateManager.addNotificationTemplateType(templateType, EMAIL_CHANNEL, tenantDomain);
        } catch (NotificationTemplateManagerException e) {
            LOG.debug("Notification template type '" + templateType + "' already registered for tenant '"
                    + LogSanitizer.sanitize(tenantDomain) + "'; continuing to check its content.", e);
        }

        Optional<Boolean> exists = templateExists(templateManager, templateType, tenantDomain);
        if (exists.isEmpty()) {
            // Lookup failed for some other reason - could be hiding a customization, so skip
            // rather than risk overwriting it. Retries on the next tenant-update event.
            return;
        }
        if (exists.get()) {
            LOG.debug("Email template '" + templateType + "' already exists for tenant '"
                    + LogSanitizer.sanitize(tenantDomain) + "'; leaving its content as-is so a Console "
                    + "customization is preserved.");
            return;
        }

        Optional<EmailTemplateConfigLoader.TemplateContent> fileContent =
                EmailTemplateConfigLoader.getTemplateContent(templateType);
        String subject = fileContent.map(EmailTemplateConfigLoader.TemplateContent::getSubject)
                .orElse(defaultSubject);
        String body = fileContent.map(EmailTemplateConfigLoader.TemplateContent::getBody).orElse(EMAIL_BODY);

        try {
            NotificationTemplate template = new NotificationTemplate();
            template.setType(templateType);
            template.setDisplayName(templateType);
            template.setLocale(DEFAULT_LOCALE);
            template.setNotificationChannel(EMAIL_CHANNEL);
            template.setContentType(CONTENT_TYPE);
            template.setSubject(subject);
            template.setBody(body);
            template.setFooter("");
            templateManager.addNotificationTemplate(template, tenantDomain);
            LOG.info("Provisioned email template '" + templateType + "' for tenant: "
                    + LogSanitizer.sanitize(tenantDomain));
        } catch (NotificationTemplateManagerException e) {
            LOG.error("Error provisioning email template '" + templateType + "' for tenant: "
                    + LogSanitizer.sanitize(tenantDomain), e);
        }
    }

    // Error code the real NotificationTemplateManager throws for "not found" - the interface's
    // own default returns null instead (see getNotificationTemplate's javadoc).
    private static final String ERROR_CODE_NO_TEMPLATE_FOUND =
            IdentityMgtConstants.ErrorMessages.ERROR_CODE_NO_TEMPLATE_FOUND.getCode();

    /**
     * @return whether the template exists, or empty if the lookup failed for some other reason.
     * Only this specific "not found" error code is folded into "not present" - any other
     * exception could be hiding an existing customization, and returning false would let
     * {@link #provisionTemplate} overwrite it.
     */
    private static Optional<Boolean> templateExists(NotificationTemplateManager templateManager,
            String templateType, String tenantDomain) {

        try {
            return Optional.of(
                    templateManager.getNotificationTemplate(EMAIL_CHANNEL, templateType, DEFAULT_LOCALE,
                            tenantDomain) != null);
        } catch (NotificationTemplateManagerException e) {
            if (ERROR_CODE_NO_TEMPLATE_FOUND.equals(e.getErrorCode())) {
                return Optional.of(false);
            }
            LOG.error("Could not look up existing email template '" + templateType + "' for tenant '"
                    + LogSanitizer.sanitize(tenantDomain) + "'; skipping provisioning rather than risk "
                    + "overwriting an existing customization. Will retry on the next tenant-update event.", e);
            return Optional.empty();
        }
    }
}
