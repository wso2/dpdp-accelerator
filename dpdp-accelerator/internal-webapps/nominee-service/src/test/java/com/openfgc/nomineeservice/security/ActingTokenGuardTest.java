package com.openfgc.nomineeservice.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openfgc.nomineeservice.service.NotAuthorizedException;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Delegation must not be transitive: a nominee acting for an owner may exercise
 * the owner's data rights, but may not appoint further nominees on their behalf.
 */
class ActingTokenGuardTest {

    private final ActingTokenGuard guard = new ActingTokenGuard();

    private Jwt token(Map<String, Object> extraClaims) {
        Jwt.Builder b = Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .claim("sub", "owner-id")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        extraClaims.forEach(b::claim);
        return b.build();
    }

    @Test
    void allowsAnOrdinaryUserActingAsThemselves() {
        assertThatCode(() -> guard.requireNotActingForSomeoneElse(token(Map.of())))
                .doesNotThrowAnyException();
    }

    // An exchanged impersonation access token carries `act`.
    @Test
    void rejectsAnImpersonationAccessToken() {
        Jwt jwt = token(Map.of("act", Map.of("sub", "nominee-id")));

        assertThatThrownBy(() -> guard.requireNotActingForSomeoneElse(jwt))
                .isInstanceOf(NotAuthorizedException.class)
                .hasMessageContaining("acting for someone else");
    }

    // A subject token carries `may_act` instead - equally not the subject acting
    // for themselves.
    @Test
    void rejectsASubjectToken() {
        Jwt jwt = token(Map.of("may_act", Map.of("sub", "nominee-id")));

        assertThatThrownBy(() -> guard.requireNotActingForSomeoneElse(jwt))
                .isInstanceOf(NotAuthorizedException.class);
    }

    // The scope alone cannot distinguish these cases: an owner who granted
    // ACCOUNT_UPDATE puts portal:profile:write:self into the nominee's token,
    // which is the very scope the nomination endpoints require.
    @Test
    void rejectsEvenWhenTheTokenCarriesTheRequiredScope() {
        Jwt jwt = token(Map.of(
                "act", Map.of("sub", "nominee-id"),
                "scope", "portal:profile:write:self"));

        assertThatThrownBy(() -> guard.requireNotActingForSomeoneElse(jwt))
                .isInstanceOf(NotAuthorizedException.class);
    }
}
