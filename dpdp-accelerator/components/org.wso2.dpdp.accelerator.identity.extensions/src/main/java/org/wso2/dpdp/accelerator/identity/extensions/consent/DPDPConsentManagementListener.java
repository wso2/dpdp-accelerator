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

package org.wso2.dpdp.accelerator.identity.extensions.consent;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.consent.mgt.core.PrivilegedConsentManager;
import org.wso2.carbon.consent.mgt.core.listener.AbstractConsentManagementListener;
import org.wso2.carbon.consent.mgt.core.model.ConsentAuthorization;
import org.wso2.carbon.consent.mgt.core.model.Receipt;
import org.wso2.carbon.consent.mgt.core.model.ReceiptInput;
import org.wso2.carbon.consent.mgt.core.model.ReceiptUpdateInput;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.dpdp.accelerator.common.util.LogSanitizer;
import org.wso2.dpdp.accelerator.consent.extensions.service.constants.ConsentHistoryServiceConstants.ActionType;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;
import org.wso2.dpdp.accelerator.identity.extensions.util.DPDPLifecycleEventUtil;

import java.sql.Timestamp;
import java.util.List;

/**
 * Captures consent lifecycle events into
 * {@code DPDP_CONSENT_STATUS_AUDIT}/{@code DPDP_CONSENT_HISTORY},
 * and - for create/update/revoke - notifies {@code DPDPLifecycleEventListener}
 * so the Event Notification
 * Framework can fan them out. Snapshots are captured on the {@code post*}
 * hooks, tagged with the
 * action that just ran.
 *
 * <p>
 * Named after the IS extension point it implements
 * ({@code ConsentManagementListener}), not
 * either of its jobs - same convention as
 * {@code DPDPIdentityExtensionTenantMgtListener} for
 * {@code TenantMgtListener}.
 *
 * <p>
 * Also maintains {@code DPDP_CONSENT_EXPIRY_TRACKER} and, on every {@code pre*}
 * hook, checks
 * whether this consent already lapsed before the scheduled
 * {@link org.wso2.dpdp.accelerator.identity.extensions.consent.scheduler.ConsentExpiryJobScheduler}
 * caught
 * it - see {@link DPDPConsentExpiryReconciler}.
 *
 * <p>
 * A capture or notification failure must never block the consent mutation, so
 * every hook
 * swallows and logs its own exceptions.
 */
public class DPDPConsentManagementListener extends AbstractConsentManagementListener {

    private static final Log LOG = LogFactory.getLog(DPDPConsentManagementListener.class);
    private static final int LISTENER_ORDER_ID = 100;
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String PENDING_STATUS = "PENDING";
    private static final String REVOKED_STATUS = "REVOKED";
    private static final String DELETED_STATUS = "DELETED";

    private static final ThreadLocal<String> PREVIOUS_STATUS = new ThreadLocal<>();

    @Override
    public int getDefaultOrderId() {

        return LISTENER_ORDER_ID;
    }

    @Override
    public boolean isEnable() {

        return DPDPIdentityExtensionDataHolder.getInstance().getConfigurationService().isConsentHistoryEnabled();
    }

    @Override
    public void postAddConsent(ReceiptInput receiptInput, String tenantDomain) {

        // receiptInput.getState() is null whenever the caller's request omits "state" -
        // the REST
        // layer's documented ACTIVE default is applied at the DB/schema level, never
        // written back
        // onto this object. Resolved from the request's own shape rather than a live
        // read: a live
        // read (getReceiptWithExtendedSchema) applies resolveConsentState()'s dynamic
        // expiry check,
        // which would misreport a consent created with an already-past expiryTime as
        // EXPIRED
        // instead of its true, as-persisted ACTIVE/PENDING creation state. REJECTED
        // never hits this
        // fallback - it requires an explicit, non-null "state" in the request.
        String currentStatus = receiptInput.getState() != null
                ? receiptInput.getState()
                : (receiptInput.getAuthorizations() != null && !receiptInput.getAuthorizations().isEmpty()
                        ? PENDING_STATUS
                        : ACTIVE_STATUS);
        recordStatusAudit(receiptInput.getConsentReceiptId(), tenantDomain, null, currentStatus, ActionType.CREATE);
        Receipt receipt = captureSnapshot(receiptInput.getConsentReceiptId(), tenantDomain, ActionType.CREATE);
        trackExpiry(receiptInput.getConsentReceiptId(), tenantDomain, receiptInput.getExpiryTime());
        DPDPLifecycleEventUtil.notify(l -> l.onConsentCreated(tenantDomain, receiptInput.getConsentReceiptId(),
                currentStatus, getActionBy(), DPDPConsentSnapshotBuilder.resolvePurposes(receipt)));
    }

    @Override
    public void preUpdateConsent(ReceiptUpdateInput updateInput, String tenantDomain) {

        DPDPConsentExpiryReconciler.expireConsentIfDue(tenantDomain, updateInput.getConsentReceiptId());
        capturePreviousStatus(updateInput.getConsentReceiptId());
    }

    @Override
    public void postUpdateConsent(ReceiptUpdateInput updateInput, String tenantDomain) {

        // Unlike revoke/delete, an update's outcome status isn't fixed in advance -
        // pushing the
        // expiry into the past or future can flip the resolved state (see
        // DPDPConsentExpiryReconciler), so the current status is re-read live here,
        // same as
        // postAuthorizeConsent already does, instead of assuming it matches the
        // pre-mutation value.
        String consentId = updateInput.getConsentReceiptId();
        String previousStatus = takePreviousStatus();
        try {
            PrivilegedConsentManager consentManager = DPDPIdentityExtensionDataHolder.getInstance()
                    .getPrivilegedConsentManager();
            Receipt receipt = consentManager.getReceiptWithExtendedSchema(consentId);
            String currentStatus = receipt.getState();
            recordStatusAudit(consentId, tenantDomain, previousStatus, currentStatus, ActionType.UPDATE);
            captureSnapshotFromReceipt(tenantDomain, consentId, ActionType.UPDATE, consentManager, receipt);
            DPDPLifecycleEventUtil.notify(l -> l.onConsentUpdated(tenantDomain, consentId, previousStatus,
                    currentStatus, getActionBy(), DPDPConsentSnapshotBuilder.resolvePurposes(receipt)));
        } catch (Exception e) {
            LOG.error("Error reading the post-update state for consent: " + LogSanitizer.sanitize(consentId), e);
        }

        // isClearExpiry() and getExpiryTime() are the two distinct signals an update
        // can carry -
        // neither set means this update didn't touch expiry at all, so the tracker is
        // left alone.
        if (updateInput.isClearExpiry()) {
            untrackExpiry(consentId, tenantDomain);
        } else if (updateInput.getExpiryTime() != null) {
            trackExpiry(consentId, tenantDomain, updateInput.getExpiryTime());
        }
    }

    @Override
    public void preRevokeConsent(String receiptId, String tenantDomain) {

        DPDPConsentExpiryReconciler.expireConsentIfDue(tenantDomain, receiptId);
        capturePreviousStatus(receiptId);
    }

    @Override
    public void postRevokeConsent(String receiptId, String tenantDomain) {

        String previousStatus = takePreviousStatus();
        recordStatusAudit(receiptId, tenantDomain, previousStatus, REVOKED_STATUS, ActionType.REVOKE);
        Receipt receipt = captureSnapshot(receiptId, tenantDomain, ActionType.REVOKE);
        untrackExpiry(receiptId, tenantDomain);
        DPDPLifecycleEventUtil.notify(l -> l.onConsentRevoked(tenantDomain, receiptId, previousStatus,
                getActionBy(), DPDPConsentSnapshotBuilder.resolvePurposes(receipt)));
    }

    @Override
    public void preDeleteConsent(String receiptId, String tenantDomain) {

        DPDPConsentExpiryReconciler.expireConsentIfDue(tenantDomain, receiptId);
        capturePreDeleteSnapshot(receiptId, tenantDomain);
    }

    @Override
    public void postDeleteConsent(String receiptId, String tenantDomain) {

        // The receipt row is gone by now - DELETED is an accelerator-defined status,
        // not one
        // carbon-consent-management itself ever stores.
        recordStatusAudit(receiptId, tenantDomain, takePreviousStatus(), DELETED_STATUS, ActionType.DELETE);
        // Kept for symmetry - delete has no reachable path through any REST API IS
        // ships today,
        // so postRevokeConsent above is the cleanup path this actually depends on in
        // practice.
        untrackExpiry(receiptId, tenantDomain);
    }

    @Override
    public void preAuthorizeConsent(String consentId, String userId, String authStatus, String tenantDomain) {

        DPDPConsentExpiryReconciler.expireConsentIfDue(tenantDomain, consentId);
        capturePreviousStatus(consentId);
    }

    @Override
    public void postAuthorizeConsent(String consentId, String userId, String authStatus, String tenantDomain) {

        // authStatus is the per-authorization status (e.g. APPROVED/REJECTED), not
        // necessarily
        // the consent's own recomputed overall state, so the current status is re-read
        // live.
        String previousStatus = takePreviousStatus();
        try {
            PrivilegedConsentManager consentManager = DPDPIdentityExtensionDataHolder.getInstance()
                    .getPrivilegedConsentManager();
            Receipt receipt = consentManager.getReceiptWithExtendedSchema(consentId);
            String currentStatus = receipt.getState();
            ActionType actionType = mapAuthorizeActionType(authStatus);
            recordStatusAudit(consentId, tenantDomain, previousStatus, currentStatus, actionType);
            captureSnapshotFromReceipt(tenantDomain, consentId, actionType, consentManager, receipt);

            // An authorizer revoking their own authorization is still a revocation from the
            // Event Notification Framework's perspective - fired on the dedicated
            // consent.revoke
            // topic, same as a direct revokeReceipt() call. Approve/reject are ordinary
            // state
            // transitions, covered by consent.update (its topic description already says
            // "state
            // transition notifications").
            if (actionType == ActionType.AUTHORIZE_REVOKE) {
                DPDPLifecycleEventUtil.notify(l -> l.onConsentRevoked(tenantDomain, consentId, previousStatus,
                        getActionBy(), DPDPConsentSnapshotBuilder.resolvePurposes(receipt)));
            } else {
                DPDPLifecycleEventUtil.notify(l -> l.onConsentUpdated(tenantDomain, consentId, previousStatus,
                        currentStatus, getActionBy(), DPDPConsentSnapshotBuilder.resolvePurposes(receipt)));
            }

            // REJECTED/REVOKED can never resolve to EXPIRED (see
            // DPDPConsentExpiryReconciler) -
            // only ACTIVE/PENDING are worth tracking. Re-checked on every call, so a
            // consent that
            // moves back to ACTIVE/PENDING later (e.g. a rejected authorization gets
            // re-approved)
            // becomes trackable again at that point.
            if (ACTIVE_STATUS.equals(currentStatus) || PENDING_STATUS.equals(currentStatus)) {
                trackExpiry(consentId, tenantDomain, receipt.getExpiryTime());
            } else {
                untrackExpiry(consentId, tenantDomain);
            }
        } catch (Exception e) {
            LOG.error("Error reading the post-authorize state for consent: " + LogSanitizer.sanitize(consentId), e);
        }
    }

    /**
     * {@code authStatus} matches {@link ConsentAuthorization.AuthorizationStatus}'s
     * names
     * (APPROVED/REJECTED/REVOKED) - anything else defaults to APPROVE rather than
     * throwing, since
     * this is invoked from a listener hook that must never let a mutation-blocking
     * exception
     * escape.
     */
    private static ActionType mapAuthorizeActionType(String authStatus) {

        if ("REJECTED".equals(authStatus)) {
            return ActionType.AUTHORIZE_REJECT;
        }
        if ("REVOKED".equals(authStatus)) {
            return ActionType.AUTHORIZE_REVOKE;
        }
        return ActionType.AUTHORIZE_APPROVE;
    }

    /**
     * Reads and remembers the consent's status before a mutation that changes it,
     * purely so the
     * corresponding {@code post*} hook can report the correct
     * {@code previousStatus} on its
     * status-audit row - the snapshot itself is captured after the mutation
     * completes (see
     * {@link #captureSnapshot}), not here.
     */
    private void capturePreviousStatus(String consentId) {

        PREVIOUS_STATUS.remove();
        try {
            Receipt receipt = DPDPIdentityExtensionDataHolder.getInstance().getPrivilegedConsentManager()
                    .getReceiptWithExtendedSchema(consentId);
            PREVIOUS_STATUS.set(receipt.getState());
        } catch (Exception e) {
            LOG.error("Error reading the pre-mutation status for consent: " + LogSanitizer.sanitize(consentId), e);
        }
    }

    /**
     * Captures a fresh, post-mutation snapshot and returns the {@link Receipt} it
     * fetched, so
     * callers needing purposes for the lifecycle notification can reuse it instead
     * of a second
     * fetch. {@code null} if the receipt fetch failed (already logged here).
     */
    private Receipt captureSnapshot(String consentId, String tenantDomain, ActionType actionType) {

        PrivilegedConsentManager consentManager;
        Receipt receipt;
        try {
            consentManager = DPDPIdentityExtensionDataHolder.getInstance().getPrivilegedConsentManager();
            receipt = consentManager.getReceiptWithExtendedSchema(consentId);
        } catch (Exception e) {
            LOG.error("Error reading receipt for consent: " + LogSanitizer.sanitize(consentId), e);
            return null;
        }

        captureSnapshotFromReceipt(tenantDomain, consentId, actionType, consentManager, receipt);
        return receipt;
    }

    /**
     * Shared by {@code captureSnapshot}, {@code postAuthorizeConsent}, and
     * {@code postUpdateConsent} - each already has the receipt in hand from resolving
     * the current status or initial fetch, so it's reused here instead of a second,
     * redundant fetch. Caught separately from the caller's own try block so a
     * snapshot failure can't also skip the expiry tracking or lifecycle notification
     * that follows it there.
     */
    private void captureSnapshotFromReceipt(String tenantDomain, String consentId, ActionType actionType,
            PrivilegedConsentManager consentManager, Receipt receipt) {

        try {
            List<ConsentAuthorization> authorizations = consentManager.getConsentAuthorizations(consentId);
            storeSnapshot(tenantDomain, consentId, actionType, receipt, authorizations);
        } catch (Exception e) {
            LOG.error("Error capturing a '" + actionType + "' consent history snapshot for consent: "
                    + LogSanitizer.sanitize(consentId), e);
        }
    }

    /**
     * Delete is the one action where the receipt row is gone immediately afterward,
     * so this is
     * the only chance to capture anything - previous-status tracking and the
     * snapshot itself have
     * to share this single pre-mutation read.
     */
    private void capturePreDeleteSnapshot(String consentId, String tenantDomain) {

        PREVIOUS_STATUS.remove();
        try {
            PrivilegedConsentManager consentManager = DPDPIdentityExtensionDataHolder.getInstance()
                    .getPrivilegedConsentManager();
            Receipt receipt = consentManager.getReceiptWithExtendedSchema(consentId);
            List<ConsentAuthorization> authorizations = consentManager.getConsentAuthorizations(consentId);
            PREVIOUS_STATUS.set(receipt.getState());
            storeSnapshot(tenantDomain, consentId, ActionType.DELETE, receipt, authorizations);
        } catch (Exception e) {
            LOG.error("Error capturing the pre-delete consent history snapshot for consent: "
                    + LogSanitizer.sanitize(consentId), e);
        }
    }

    private void storeSnapshot(String tenantDomain, String consentId, ActionType actionType, Receipt receipt,
            List<ConsentAuthorization> authorizations) throws Exception {

        String snapshotJson = DPDPConsentSnapshotBuilder.buildSnapshotJson(receipt, authorizations);
        DPDPIdentityExtensionDataHolder.getInstance().getConsentHistoryService()
                .recordHistorySnapshot(tenantDomain, consentId, actionType, snapshotJson, getActionBy());
    }

    private void recordStatusAudit(String consentId, String tenantDomain, String previousStatus,
            String currentStatus, ActionType actionType) {

        try {
            DPDPIdentityExtensionDataHolder.getInstance().getConsentHistoryService()
                    .recordStatusAudit(tenantDomain, consentId, previousStatus, currentStatus, actionType,
                            getActionBy());
        } catch (Exception e) {
            LOG.error("Error recording a '" + actionType + "' status-audit row for consent: "
                    + LogSanitizer.sanitize(consentId), e);
        }
    }

    private void trackExpiry(String consentId, String tenantDomain, Timestamp expiryTime) {

        if (expiryTime == null) {
            return;
        }
        try {
            // The source database may store less precision than the request timestamp.
            Receipt persistedReceipt = DPDPIdentityExtensionDataHolder.getInstance().getPrivilegedConsentManager()
                    .getReceiptWithExtendedSchema(consentId);
            if (persistedReceipt.getExpiryTime() != null) {
                DPDPIdentityExtensionDataHolder.getInstance().getConsentExpiryService()
                        .trackExpiry(tenantDomain, consentId, persistedReceipt.getExpiryTime().getTime());
            }
        } catch (Exception e) {
            LOG.error("Error tracking expiry for consent: " + LogSanitizer.sanitize(consentId), e);
        }
    }

    private void untrackExpiry(String consentId, String tenantDomain) {

        try {
            DPDPIdentityExtensionDataHolder.getInstance().getConsentExpiryService()
                    .untrackExpiry(tenantDomain, consentId);
        } catch (Exception e) {
            LOG.error("Error untracking expiry for consent: " + LogSanitizer.sanitize(consentId), e);
        }
    }

    private static String takePreviousStatus() {

        String previousStatus = PREVIOUS_STATUS.get();
        PREVIOUS_STATUS.remove();
        return previousStatus;
    }

    private static String getActionBy() {

        return PrivilegedCarbonContext.getThreadLocalCarbonContext().getUsername();
    }
}
