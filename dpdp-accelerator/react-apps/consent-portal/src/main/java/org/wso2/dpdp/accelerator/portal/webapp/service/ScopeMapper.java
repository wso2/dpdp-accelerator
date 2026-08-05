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

import java.util.ArrayList;
import java.util.List;

/**
 * Maps Identity Server scopes to the {@code portal:*} scope vocabulary the
 * SPA validates (frontend/src/utils/portalScopes.ts). Identity Server scopes
 * with no portal equivalent are simply not granted, which hides the
 * corresponding UI areas.
 */
public final class ScopeMapper {

    public static final String PORTAL_CONSENTS_READ_SELF = "portal:consents:read:self";
    public static final String PORTAL_CONSENTS_WRITE_SELF = "portal:consents:write:self";
    public static final String PORTAL_CONSENTS_READ_ANY = "portal:consents:read:any";
    public static final String PORTAL_CONSENTS_WRITE_ANY = "portal:consents:write:any";
    public static final String PORTAL_ELEMENTS_READ = "portal:elements:read";
    public static final String PORTAL_ELEMENTS_WRITE = "portal:elements:write";
    public static final String PORTAL_PURPOSES_READ = "portal:purposes:read";
    public static final String PORTAL_PURPOSES_WRITE = "portal:purposes:write";

    public static final String IS_INTERNAL_LOGIN = "internal_login";
    public static final String IS_CONSENT_VIEW = "internal_consent_mgt_consent_view";
    public static final String IS_CONSENT_CREATE = "internal_consent_mgt_consent_create";
    public static final String IS_CONSENT_UPDATE = "internal_consent_mgt_consent_update";
    public static final String IS_ELEMENT_VIEW = "internal_consent_mgt_element_view";
    public static final String IS_ELEMENT_CREATE = "internal_consent_mgt_element_create";
    public static final String IS_ELEMENT_DELETE = "internal_consent_mgt_element_delete";
    public static final String IS_PURPOSE_VIEW = "internal_consent_mgt_purpose_view";
    public static final String IS_PURPOSE_CREATE = "internal_consent_mgt_purpose_create";
    public static final String IS_PURPOSE_UPDATE = "internal_consent_mgt_purpose_update";
    public static final String IS_PURPOSE_DELETE = "internal_consent_mgt_purpose_delete";

    private ScopeMapper() {
    }

    public static List<String> toPortalScopes(List<String> identityServerScopes) {

        List<String> portalScopes = new ArrayList<>();
        if (identityServerScopes.contains(IS_INTERNAL_LOGIN)) {
            portalScopes.add(PORTAL_CONSENTS_READ_SELF);
            portalScopes.add(PORTAL_CONSENTS_WRITE_SELF);
        }
        if (identityServerScopes.contains(IS_CONSENT_VIEW)) {
            portalScopes.add(PORTAL_CONSENTS_READ_ANY);
        }
        if (identityServerScopes.contains(IS_CONSENT_UPDATE)
                || identityServerScopes.contains(IS_CONSENT_CREATE)) {
            portalScopes.add(PORTAL_CONSENTS_WRITE_ANY);
        }
        if (identityServerScopes.contains(IS_ELEMENT_VIEW)) {
            portalScopes.add(PORTAL_ELEMENTS_READ);
        }
        if (identityServerScopes.contains(IS_ELEMENT_CREATE)
                || identityServerScopes.contains(IS_ELEMENT_DELETE)) {
            portalScopes.add(PORTAL_ELEMENTS_WRITE);
        }
        if (identityServerScopes.contains(IS_PURPOSE_VIEW)) {
            portalScopes.add(PORTAL_PURPOSES_READ);
        }
        if (identityServerScopes.contains(IS_PURPOSE_CREATE)
                || identityServerScopes.contains(IS_PURPOSE_UPDATE)
                || identityServerScopes.contains(IS_PURPOSE_DELETE)) {
            portalScopes.add(PORTAL_PURPOSES_WRITE);
        }
        return portalScopes;
    }
}
