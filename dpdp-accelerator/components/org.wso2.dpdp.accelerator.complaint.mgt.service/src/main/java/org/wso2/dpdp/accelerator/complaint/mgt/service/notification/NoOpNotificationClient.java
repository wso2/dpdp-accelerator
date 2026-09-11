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

package org.wso2.dpdp.accelerator.complaint.mgt.service.notification;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;

/**
 * Wired in by {@code ComplaintServiceComponent} instead of {@link EmailNotificationClient} when
 * {@code Complaints.EmailNotificationsEnabled} is {@code false} - a deliberate no-op rather than
 * relying on {@code EmailNotificationClient} to fail its own OSGi/SMTP lookups silently.
 */
public class NoOpNotificationClient implements NotificationClient {

    private static final Log LOG = LogFactory.getLog(NoOpNotificationClient.class);

    @Override
    public void notifyComplaintCreated(Complaint complaint) {

        if (LOG.isDebugEnabled()) {
            LOG.debug("Complaint email notifications are disabled; skipping complaint-created notification.");
        }
    }

    @Override
    public void notifyCommentAdded(Complaint complaint, ComplaintEvent event) {

        if (LOG.isDebugEnabled()) {
            LOG.debug("Complaint email notifications are disabled; skipping comment-added notification.");
        }
    }
}
