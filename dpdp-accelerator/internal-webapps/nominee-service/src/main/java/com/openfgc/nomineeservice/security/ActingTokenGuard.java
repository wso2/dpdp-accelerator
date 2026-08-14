package com.openfgc.nomineeservice.security;

import com.openfgc.nomineeservice.service.NotAuthorizedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Refuses nomination management to anyone acting on someone else's behalf.
 *
 * <p><b>Why this cannot be a scope check.</b> An impersonation token carries the
 * OWNER as its subject and the scopes the owner granted the nominee. If the
 * owner granted ACCOUNT_UPDATE, that token holds {@code portal:profile:write:self}
 * - the very scope the nomination endpoints require. This service would read
 * {@code sub} as the owner and conclude the owner was adding a nominee, when in
 * fact the nominee is. They could appoint their own accomplice on an account
 * whose owner is dead or incapacitated and cannot object.
 *
 * <p>The scope cannot distinguish the two cases, and should not have to: the
 * distinction is who is holding the token, which is exactly what the {@code act}
 * and {@code may_act} claims record. So the rule is stated once, here, and
 * applied at every nomination-management entry point.
 *
 * <p><b>Delegation is not transitive.</b> The owner chose one person. That person
 * may exercise the owner's data rights; they may not extend the owner's trust to
 * anybody else.
 */
@Component
public class ActingTokenGuard {

    private static final String ACT = "act";
    private static final String MAY_ACT = "may_act";

    /**
     * Throws if the caller is acting for someone else rather than as themselves.
     *
     * <p>Both claims are checked because the two stages of the flow use different
     * ones: {@code may_act} on the subject token, {@code act} on the exchanged
     * impersonation access token. Either one means "this is not the subject
     * acting for themselves".
     */
    public void requireNotActingForSomeoneElse(Jwt jwt) {
        if (jwt == null) {
            return;
        }
        if (jwt.hasClaim(ACT) || jwt.hasClaim(MAY_ACT)) {
            throw new NotAuthorizedException(
                    "Nominations cannot be managed while acting for someone else");
        }
    }
}
