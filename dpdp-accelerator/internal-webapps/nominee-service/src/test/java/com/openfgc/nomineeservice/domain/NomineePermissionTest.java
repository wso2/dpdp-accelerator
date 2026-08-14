package com.openfgc.nomineeservice.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * A stored grant must describe something the nominee can actually carry out.
 * Revoking a consent means finding it first, so a grant of CONSENT_REVOKE alone
 * would name an action its holder could never reach.
 */
class NomineePermissionTest {

    @Test
    void revokingImpliesBeingAbleToSeeWhatIsRevoked() {
        assertThat(NomineePermission.expand(Set.of(NomineePermission.CONSENT_REVOKE)))
                .containsExactlyInAnyOrder(
                        NomineePermission.CONSENT_REVOKE, NomineePermission.CONSENT_VIEW);
    }

    // Revoking and approving are opposite acts. An owner granting one must not
    // silently confer the other.
    @Test
    void approvingIsSeparateFromRevoking() {
        assertThat(NomineePermission.expand(Set.of(NomineePermission.CONSENT_REVOKE)))
                .doesNotContain(NomineePermission.CONSENT_APPROVE);
        assertThat(NomineePermission.expand(Set.of(NomineePermission.CONSENT_APPROVE)))
                .doesNotContain(NomineePermission.CONSENT_REVOKE);
    }

    @Test
    void approvingImpliesBeingAbleToSeeWhatIsApproved() {
        assertThat(NomineePermission.expand(Set.of(NomineePermission.CONSENT_APPROVE)))
                .containsExactlyInAnyOrder(
                        NomineePermission.CONSENT_APPROVE, NomineePermission.CONSENT_VIEW);
    }

    @Test
    void changingOrClosingAnAccountImpliesBeingAbleToSeeIt() {
        assertThat(NomineePermission.expand(Set.of(NomineePermission.ACCOUNT_UPDATE)))
                .contains(NomineePermission.ACCOUNT_VIEW);
        assertThat(NomineePermission.expand(Set.of(NomineePermission.ACCOUNT_DELETE)))
                .contains(NomineePermission.ACCOUNT_VIEW);
    }

    @Test
    void aGrantThatNeedsNothingElseIsLeftAlone() {
        assertThat(NomineePermission.expand(Set.of(NomineePermission.CONSENT_VIEW)))
                .containsExactly(NomineePermission.CONSENT_VIEW);
        assertThat(NomineePermission.expand(Set.of(NomineePermission.ACCOUNT_VIEW)))
                .containsExactly(NomineePermission.ACCOUNT_VIEW);
    }

    @Test
    void grantingNothingStaysNothing() {
        assertThat(NomineePermission.expand(Set.of())).isEmpty();
    }

    // Whatever an owner picks, the result must never contain a permission whose
    // prerequisite is missing.
    @Test
    void everyExpandedGrantIsSelfSufficient() {
        for (NomineePermission permission : NomineePermission.values()) {
            Set<NomineePermission> expanded = NomineePermission.expand(Set.of(permission));
            assertThat(NomineePermission.expand(expanded))
                    .as("expanding %s twice must not add more", permission)
                    .isEqualTo(expanded);
        }
    }

    @Test
    void theExpandedSetCannotBeModifiedByTheCaller() {
        Set<NomineePermission> expanded =
                NomineePermission.expand(Set.of(NomineePermission.CONSENT_REVOKE));
        assertThat(expanded).isUnmodifiable();
    }
}
