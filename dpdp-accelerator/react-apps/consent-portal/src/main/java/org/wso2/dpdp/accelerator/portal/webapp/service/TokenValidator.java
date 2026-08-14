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
import org.wso2.dpdp.accelerator.portal.webapp.model.AuthenticatedUser;
import org.wso2.dpdp.accelerator.portal.webapp.util.PortalConfig;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

/**
 * Validates Identity Server JWT access tokens against the server's JWKS
 * endpoint and extracts the authenticated principal.
 */
public final class TokenValidator {

    private static volatile TokenValidator instance;

    private final ConfigurableJWTProcessor<SecurityContext> jwtProcessor;
    private final String orgIdClaim;

    private TokenValidator(PortalConfig config) throws MalformedURLException {

        URL jwksUrl = new URL(config.getIdentityServerInternalBaseUrl() + "/oauth2/jwks");
        JWKSource<SecurityContext> keySource = new RemoteJWKSet<>(jwksUrl);
        orgIdClaim = config.getOrgIdClaim();
        jwtProcessor = new DefaultJWTProcessor<>();
        // The Identity Server issues RFC 9068 access tokens typed "at+jwt"; the
        // Nimbus default accepts only "JWT" or an absent type.
        jwtProcessor.setJWSTypeVerifier(new DefaultJOSEObjectTypeVerifier<>(
                new JOSEObjectType("at+jwt"), JOSEObjectType.JWT, null));
        jwtProcessor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource));
    }

    public static TokenValidator getInstance(PortalConfig config) throws TokenValidationException {

        if (instance == null) {
            synchronized (TokenValidator.class) {
                if (instance == null) {
                    try {
                        instance = new TokenValidator(config);
                    } catch (MalformedURLException e) {
                        throw new TokenValidationException("Invalid JWKS URL", e);
                    }
                }
            }
        }
        return instance;
    }

    /**
     * Verifies signature and expiry, and returns the principal carried by the token.
     */
    public AuthenticatedUser validate(String accessToken) throws TokenValidationException {

        JWTClaimsSet claims;
        try {
            claims = jwtProcessor.process(accessToken, null);
        } catch (Exception e) {
            throw new TokenValidationException("Access token validation failed", e);
        }

        String subject = claims.getSubject();
        if (subject == null || subject.isEmpty()) {
            throw new TokenValidationException("Access token has no subject");
        }

        String orgId = stringClaim(claims, orgIdClaim);
        String scope = stringClaim(claims, "scope");
        List<String> scopes = scope == null ? List.of() : Arrays.asList(scope.split("\\s+"));
        return new AuthenticatedUser(subject, orgId, scopes);
    }

    private static String stringClaim(JWTClaimsSet claims, String name) {

        Object value = claims.getClaim(name);
        return value instanceof String ? (String) value : null;
    }
}
