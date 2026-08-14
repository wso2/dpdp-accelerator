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

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.wso2.dpdp.accelerator.portal.webapp.exception.TokenValidationException;
import org.wso2.dpdp.accelerator.portal.webapp.model.MaskToken;
import org.wso2.dpdp.accelerator.portal.webapp.util.PortalConfig;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates Identity Server impersonation ("mask") access tokens: the same
 * JWKS-backed signature/expiry checks as {@link TokenValidator}, plus the
 * delegation-specific claims that distinguish a mask token from an ordinary
 * one.
 *
 * A mask token carries an {@code act} claim (the real actor) alongside the
 * ordinary {@code sub} (who the actor is acting for). A token with no {@code
 * act.sub}, or one where it equals {@code sub}, is rejected outright -- it is
 * never treated as an ordinary token belonging to {@code sub}, which would
 * silently hand the caller the owner's whole account.
 */
public final class MaskTokenVerifier {

    /**
     * Deliberately not {@link PortalConfig#getOrgIdClaim()}: that setting picks
     * the human-facing org identifier ("org_handle" -> "carbon.super") shown by
     * {@code /me}, but the Consent Server's {@code org-id} header is a tenant
     * filter and needs the actual {@code org_id} UUID claim. Mixing the two up
     * silently returns zero consents -- the request succeeds, just scoped to a
     * tenant nothing belongs to.
     */
    private static final String MASK_ORG_ID_CLAIM = "org_id";

    private static volatile MaskTokenVerifier instance;

    private final ConfigurableJWTProcessor<SecurityContext> jwtProcessor;

    private MaskTokenVerifier(PortalConfig config) throws MalformedURLException {

        URL jwksUrl = new URL(config.getIdentityServerInternalBaseUrl() + "/oauth2/jwks");
        JWKSource<SecurityContext> keySource = new RemoteJWKSet<>(jwksUrl);
        jwtProcessor = new DefaultJWTProcessor<>();
        jwtProcessor.setJWSTypeVerifier(new DefaultJOSEObjectTypeVerifier<>(
                new JOSEObjectType("at+jwt"), JOSEObjectType.JWT, null));
        jwtProcessor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource));
    }

    public static MaskTokenVerifier getInstance(PortalConfig config) throws TokenValidationException {

        if (instance == null) {
            synchronized (MaskTokenVerifier.class) {
                if (instance == null) {
                    try {
                        instance = new MaskTokenVerifier(config);
                    } catch (MalformedURLException e) {
                        throw new TokenValidationException("Invalid JWKS URL", e);
                    }
                }
            }
        }
        return instance;
    }

    public MaskToken verify(String rawToken) throws TokenValidationException {

        JWTClaimsSet claims;
        try {
            claims = jwtProcessor.process(rawToken, null);
        } catch (Exception e) {
            throw new TokenValidationException("Mask token validation failed", e);
        }

        String owner = claims.getSubject();
        if (owner == null || owner.isEmpty()) {
            throw new TokenValidationException("Mask token has no subject");
        }

        String nominee = actSubClaim(claims);
        if (nominee == null || nominee.isEmpty()) {
            throw new TokenValidationException("Mask token carries no act.sub delegation claim");
        }
        if (nominee.equals(owner)) {
            throw new TokenValidationException("Mask token names the owner as their own nominee");
        }

        Instant expiry = claims.getExpirationTime() == null ? Instant.EPOCH : claims.getExpirationTime().toInstant();
        String orgId = stringClaim(claims, MASK_ORG_ID_CLAIM);
        Set<String> scopes = parseScopeClaim(claims.getClaim("scope"));

        return new MaskToken(owner, nominee, scopes, expiry, orgId);
    }

    @SuppressWarnings("unchecked")
    private static String actSubClaim(JWTClaimsSet claims) {

        Object act = claims.getClaim("act");
        if (!(act instanceof Map)) {
            return null;
        }
        Object sub = ((Map<String, Object>) act).get("sub");
        return sub instanceof String ? (String) sub : null;
    }

    private static String stringClaim(JWTClaimsSet claims, String name) {

        Object value = claims.getClaim(name);
        return value instanceof String ? (String) value : null;
    }

    /** Accepts both encodings Identity Server may emit: a space-delimited string or a JSON array. */
    @SuppressWarnings("unchecked")
    private static Set<String> parseScopeClaim(Object raw) {

        Set<String> scopes = new LinkedHashSet<>();
        if (raw instanceof String) {
            String string = (String) raw;
            scopes.addAll(Arrays.asList(string.trim().split("\\s+")));
            scopes.remove("");
        } else if (raw instanceof List) {
            for (Object entry : (List<Object>) raw) {
                if (entry instanceof String && !((String) entry).isBlank()) {
                    scopes.add(((String) entry).trim());
                }
            }
        }
        return scopes;
    }
}
