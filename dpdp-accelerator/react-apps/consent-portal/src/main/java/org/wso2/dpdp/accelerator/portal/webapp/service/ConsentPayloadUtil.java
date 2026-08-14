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

package org.wso2.dpdp.accelerator.portal.webapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.wso2.dpdp.accelerator.portal.webapp.util.HttpUtil;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Consent Server payload shaping shared by the first-party ({@link
 * org.wso2.dpdp.accelerator.portal.webapp.servlet.MyConsentsServlet}) and
 * acting-as ({@link org.wso2.dpdp.accelerator.portal.webapp.servlet.ActingConsentsServlet})
 * consent flows, so "approved by the owner" and "approved by their nominee"
 * always produce byte-identical Consent Server payloads.
 */
public final class ConsentPayloadUtil {

    private ConsentPayloadUtil() {
    }

    /** Reports whether userId holds an authorization on the described consent. */
    public static boolean consentBelongsTo(String body, String userId) {

        try {
            JsonNode consent = HttpUtil.mapper().readTree(body);
            for (JsonNode authorization : consent.path("authorizations")) {
                if (userId.equals(authorization.path("userId").asText(null))) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    /** Returns the consent's trusted group id if userId holds an authorization on it, else null. */
    public static String ownedConsentGroupId(String body, String userId) throws IOException {

        JsonNode consent = HttpUtil.mapper().readTree(body);
        String trimmedUser = userId.trim();
        for (JsonNode authorization : consent.path("authorizations")) {
            String authUser = authorization.path("userId").asText(null);
            if (authUser != null && authUser.trim().equals(trimmedUser)) {
                return consent.path("groupId").asText("").trim();
            }
        }
        return null;
    }

    /**
     * Builds the consent update payload for an approval action: mandatory
     * elements are always approved, an optional element is approved only when
     * it matches a selection, and the approval is recorded as an
     * "authorisation" entry for userId (who performed it is carried by the
     * audit trail, not this record).
     */
    public static ObjectNode buildApprovalUpdatePayload(JsonNode consent, JsonNode selections, String userId)
            throws IOException {

        Set<String> selectedKeys = new LinkedHashSet<>();
        for (JsonNode selection : selections) {
            selectedKeys.add(approvalKey(selection.path("purposeId").asText(""),
                    selection.path("purposeVersion").asText(""), selection.path("elementId").asText(""),
                    selection.path("elementVersion").asText("")));
        }
        Set<String> matchedKeys = new LinkedHashSet<>();

        ArrayNode updatedPurposes = HttpUtil.mapper().createArrayNode();
        for (JsonNode purpose : consent.path("purposes")) {
            String purposeId = purpose.path("purposeId").asText("");
            String purposeVersion = purpose.path("version").asText("");

            ObjectNode updatedPurpose = HttpUtil.mapper().createObjectNode();
            updatedPurpose.put("name", purpose.path("name").asText(""));
            updatedPurpose.put("version", purposeVersion);
            ArrayNode updatedElements = updatedPurpose.putArray("elements");

            for (JsonNode element : purpose.path("elements")) {
                boolean mandatory = element.path("mandatory").asBoolean(false);
                boolean approved = element.path("approved").asBoolean(false);
                if (mandatory) {
                    approved = true;
                } else {
                    String key = approvalKey(purposeId, purposeVersion, element.path("elementId").asText(""),
                            element.path("version").asText(""));
                    if (selectedKeys.contains(key)) {
                        approved = true;
                        matchedKeys.add(key);
                    }
                }
                ObjectNode updatedElement = updatedElements.addObject();
                updatedElement.put("name", element.path("name").asText(""));
                updatedElement.put("namespace", element.path("namespace").asText(""));
                updatedElement.put("version", element.path("version").asText(""));
                updatedElement.put("approved", approved);
                if (element.has("value")) {
                    updatedElement.set("value", element.get("value"));
                }
            }
            updatedPurposes.add(updatedPurpose);
        }
        if (matchedKeys.size() != selectedKeys.size()) {
            throw new IOException("invalid approval selection");
        }

        ArrayNode updatedAuthorizations = HttpUtil.mapper().createArrayNode();
        boolean ownerAuthorizationUpdated = false;
        for (JsonNode authorization : consent.path("authorizations")) {
            String authUserId = authorization.path("userId").asText(null);
            ObjectNode updated = HttpUtil.mapper().createObjectNode();
            if (authUserId != null && authUserId.trim().toLowerCase(Locale.ROOT)
                    .equals(userId.trim().toLowerCase(Locale.ROOT))) {
                updated.put("userId", userId);
                updated.put("type", "authorisation");
                updated.put("status", "APPROVED");
                updated.set("resources", HttpUtil.mapper().createObjectNode());
                ownerAuthorizationUpdated = true;
            } else {
                if (authUserId != null) {
                    updated.put("userId", authUserId);
                }
                updated.put("type", authorization.path("type").asText(""));
                updated.put("status", authorization.path("status").asText(""));
                JsonNode resources = authorization.get("resources");
                updated.set("resources", resources != null ? resources : HttpUtil.mapper().createObjectNode());
            }
            updatedAuthorizations.add(updated);
        }
        if (!ownerAuthorizationUpdated) {
            ObjectNode ownerAuthorization = HttpUtil.mapper().createObjectNode();
            ownerAuthorization.put("userId", userId);
            ownerAuthorization.put("type", "authorisation");
            ownerAuthorization.put("status", "APPROVED");
            ownerAuthorization.set("resources", HttpUtil.mapper().createObjectNode());
            updatedAuthorizations.add(ownerAuthorization);
        }

        ObjectNode payload = HttpUtil.mapper().createObjectNode();
        payload.put("type", consent.path("type").asText(""));
        copyIfPresent(consent, payload, "expirationTime");
        copyIfPresent(consent, payload, "recurringIndicator");
        copyIfPresent(consent, payload, "dataAccessValidityDuration");
        copyIfPresent(consent, payload, "frequency");
        payload.set("purposes", updatedPurposes);
        JsonNode attributes = consent.get("attributes");
        payload.set("attributes", attributes != null ? attributes : HttpUtil.mapper().createObjectNode());
        payload.set("authorizations", updatedAuthorizations);
        return payload;
    }

    /**
     * Builds the minimal consent update required to reject a consent: only a
     * consent still {@code CREATED} (never authorized either way) can be
     * rejected, and rejection touches only the caller's own authorization
     * entry.
     */
    public static ObjectNode buildRejectionUpdatePayload(JsonNode consent, String userId) throws IOException {

        if (!"CREATED".equalsIgnoreCase(consent.path("status").asText(""))) {
            throw new IOException("consent is not in a rejectable state");
        }

        ArrayNode updatedAuthorizations = HttpUtil.mapper().createArrayNode();
        boolean found = false;
        for (JsonNode authorization : consent.path("authorizations")) {
            String authUserId = authorization.path("userId").asText(null);
            String status = authorization.path("status").asText("");
            ObjectNode updated = HttpUtil.mapper().createObjectNode();
            if (authUserId != null && authUserId.trim().toLowerCase(Locale.ROOT)
                    .equals(userId.trim().toLowerCase(Locale.ROOT))) {
                status = "REJECTED";
                found = true;
            }
            if (authUserId != null) {
                updated.put("userId", authUserId);
            }
            updated.put("type", authorization.path("type").asText(""));
            updated.put("status", status);
            JsonNode resources = authorization.get("resources");
            updated.set("resources", resources != null ? resources : HttpUtil.mapper().createObjectNode());
            updatedAuthorizations.add(updated);
        }
        if (!found) {
            throw new IOException("no authorization entry for this user");
        }

        ObjectNode payload = HttpUtil.mapper().createObjectNode();
        payload.set("authorizations", updatedAuthorizations);
        return payload;
    }

    private static void copyIfPresent(JsonNode source, ObjectNode target, String field) {

        if (source.has(field) && !source.get(field).isNull()) {
            target.set(field, source.get(field));
        }
    }

    private static String approvalKey(String purposeId, String purposeVersion, String elementId,
                                       String elementVersion) {

        return purposeId + ' ' + purposeVersion + ' ' + elementId + ' ' + elementVersion;
    }
}
