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
 * mirrors the "only add what's missing" idiom already used for role permissions in
 * {@code DPDPConsentPortalRoleProvisioningUtil}. Runs on every {@code onTenantCreate}/
 * {@code onTenantUpdate} (see {@code DPDPIdentityExtensionTenantMgtListener}), so an
 * administrator's Console edit to a template's subject/body is never overwritten by a later
 * tenant-update event - only a template this tenant doesn't have yet gets written, permanently.
 * There is no override path back to the bundled default once a tenant has its own copy; picking
 * up a bundled-default change (e.g. after editing {@code email-dpdp-config.xml}) only ever
 * affects tenants provisioned after that change.
 *
 * <p>The bundled default subject/body for each type comes from
 * {@code <IS_HOME>/repository/conf/email/email-dpdp-config.xml} when present ({@link
 * EmailTemplateConfigLoader}), falling back to this class's own Java literal subjects and the
 * bundled {@code complaint-email-body.html} classpath resource otherwise - editing that file
 * changes the bundled default without a Java rebuild.
 */
public final class EmailTemplateProvisioningUtil {

    private static final Log LOG = LogFactory.getLog(EmailTemplateProvisioningUtil.class);
    private static final String EMAIL_CHANNEL = "EMAIL";
    private static final String DEFAULT_LOCALE = "en_US";
    private static final String CONTENT_TYPE = "text/html";

    // Template types - each mirrors the TEMPLATE_TYPE value the notification's own trigger sets
    // (see complaint.mgt.service's EmailNotificationClient, which fires TRIGGER_NOTIFICATION
    // directly and duplicates these same three literals rather than depending on this bundle).
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

    // Shared HTML shell for all three notification types, bundled as an OSGi resource rather than
    // an inline Java string - see that file's own header comment for what it contains and why.
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
     * Writes the template content only if this tenant doesn't already have it - an administrator's
     * Console edit is otherwise indistinguishable from the bundled default once written, so
     * overwriting unconditionally on every tenant update would silently discard it. This check is
     * unconditional and permanent: there is no flag or action that bypasses it once a template
     * exists for a tenant. {@code addNotificationTemplateType} is not upsert-safe (throws if
     * already registered), so that failure is swallowed separately and never blocks the check/
     * write below it.
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

        if (templateExists(templateManager, templateType, tenantDomain)) {
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

    /**
     * {@code getNotificationTemplate} returns {@code null} for a template this tenant doesn't have
     * yet (see its own javadoc); any exception is treated the same way - as "not present yet",
     * since the only alternative is blocking provisioning entirely on a lookup failure.
     */
    private static boolean templateExists(NotificationTemplateManager templateManager, String templateType,
            String tenantDomain) {

        try {
            return templateManager.getNotificationTemplate(EMAIL_CHANNEL, templateType, DEFAULT_LOCALE, tenantDomain)
                    != null;
        } catch (NotificationTemplateManagerException e) {
            LOG.debug("Could not look up existing email template '" + templateType + "' for tenant '"
                    + LogSanitizer.sanitize(tenantDomain) + "'; treating it as not yet provisioned.", e);
            return false;
        }
    }
}
