package com.openfgc.nomineeservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openfgc.nomineeservice.domain.Nomination;
import com.openfgc.nomineeservice.domain.NominationStatus;
import com.openfgc.nomineeservice.domain.NomineePermission;
import com.openfgc.nomineeservice.repository.NominationRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Covers the behaviour DPDP Rule 14(4) requires: an owner may nominate one or
 * more individuals, each with an independent permission set and lifecycle.
 */
@SpringBootTest
@Transactional
class NominationServiceTest {

    private static final String ORG = "test-org";
    private static final String OWNER = "owner-1";
    private static final String ALICE = "nominee-alice";
    private static final String BOB = "nominee-bob";
    private static final String CAROL = "nominee-carol";

    @Autowired
    private NominationService service;

    @Autowired
    private NominationRepository nominations;

    private Nomination nominate(String nomineeId, NomineePermission... permissions) {
        return service.nominate(ORG, OWNER, nomineeId, nomineeId + "@example.com",
                Set.of(permissions));
    }

    // The core requirement: more than two, each independent.
    @Test
    void ownerCanNominateManyIndividuals() {
        nominate(ALICE, NomineePermission.CONSENT_VIEW);
        nominate(BOB, NomineePermission.CONSENT_VIEW, NomineePermission.CONSENT_REVOKE);
        nominate(CAROL, NomineePermission.CONSENT_VIEW);

        List<Nomination> all = service.getByOwnerId(OWNER);

        assertThat(all).hasSize(3);
        assertThat(all).extracting(Nomination::getNomineeId)
                .containsExactlyInAnyOrder(ALICE, BOB, CAROL);
    }

    // Nominating is additive: an owner's existing nominees keep their own
    // permissions and status when another is added.
    @Test
    void addingANomineeDoesNotRemoveExistingOnes() {
        Nomination alice = nominate(ALICE, NomineePermission.CONSENT_VIEW);

        nominate(BOB, NomineePermission.CONSENT_REVOKE);

        assertThat(nominations.findById(alice.getId())).isPresent();
        assertThat(service.getByOwnerId(OWNER)).hasSize(2);
    }

    @Test
    void eachNomineeCarriesItsOwnPermissions() {
        nominate(ALICE, NomineePermission.CONSENT_VIEW);
        nominate(BOB, NomineePermission.CONSENT_VIEW, NomineePermission.CONSENT_REVOKE);

        Nomination alice = service.getByOwnerId(OWNER).stream()
                .filter(n -> n.getNomineeId().equals(ALICE)).findFirst().orElseThrow();
        Nomination bob = service.getByOwnerId(OWNER).stream()
                .filter(n -> n.getNomineeId().equals(BOB)).findFirst().orElseThrow();

        assertThat(alice.getPermissions()).containsExactly(NomineePermission.CONSENT_VIEW);
        assertThat(bob.getPermissions()).containsExactlyInAnyOrder(
                NomineePermission.CONSENT_VIEW, NomineePermission.CONSENT_REVOKE);
    }

    @Test
    void permissionsCanBeChangedForOneNomineeWithoutAffectingOthers() {
        Nomination alice = nominate(ALICE, NomineePermission.CONSENT_VIEW);
        Nomination bob = nominate(BOB, NomineePermission.CONSENT_VIEW);

        service.updatePermissions(alice.getId(), OWNER,
                Set.of(NomineePermission.CONSENT_VIEW, NomineePermission.CONSENT_REVOKE));

        assertThat(nominations.findById(alice.getId()).orElseThrow().getPermissions())
                .containsExactlyInAnyOrder(NomineePermission.CONSENT_VIEW,
                        NomineePermission.CONSENT_REVOKE);
        assertThat(nominations.findById(bob.getId()).orElseThrow().getPermissions())
                .containsExactly(NomineePermission.CONSENT_VIEW);
    }

    @Test
    void removingOneNomineeLeavesTheOthers() {
        Nomination alice = nominate(ALICE, NomineePermission.CONSENT_VIEW);
        nominate(BOB, NomineePermission.CONSENT_VIEW);
        nominate(CAROL, NomineePermission.CONSENT_VIEW);

        service.removeNomination(alice.getId(), OWNER);

        assertThat(service.getByOwnerId(OWNER)).hasSize(2);
        assertThat(service.getByOwnerId(OWNER)).extracting(Nomination::getNomineeId)
                .containsExactlyInAnyOrder(BOB, CAROL);
    }

    @Test
    void activatingOneNomineeDoesNotActivateOthers() {
        Nomination alice = nominate(ALICE, NomineePermission.CONSENT_VIEW);
        Nomination bob = nominate(BOB, NomineePermission.CONSENT_VIEW);
        service.accept(alice.getId(), ALICE);
        service.accept(bob.getId(), BOB);

        service.activate(alice.getId(), "admin-1", "TICKET-1");

        assertThat(nominations.findById(alice.getId()).orElseThrow().getStatus())
                .isEqualTo(NominationStatus.ACTIVE);
        assertThat(nominations.findById(bob.getId()).orElseThrow().getStatus())
                .isEqualTo(NominationStatus.ACCEPTED);
    }

    @Test
    void sameNomineeCannotBeNominatedTwiceByTheSameOwner() {
        nominate(ALICE, NomineePermission.CONSENT_VIEW);

        assertThatThrownBy(() -> nominate(ALICE, NomineePermission.CONSENT_REVOKE))
                .isInstanceOf(DuplicateNominationException.class);
    }

    @Test
    void theSamePersonMayBeNominatedByDifferentOwners() {
        nominate(ALICE, NomineePermission.CONSENT_VIEW);

        service.nominate(ORG, "owner-2", ALICE, "alice@example.com",
                Set.of(NomineePermission.CONSENT_VIEW));

        assertThat(service.getByNomineeId(ALICE)).hasSize(2);
    }

    // Holding the right scope is not enough - the caller must be the nominee.
    @Test
    void onlyTheNamedNomineeMayAccept() {
        Nomination alice = nominate(ALICE, NomineePermission.CONSENT_VIEW);

        assertThatThrownBy(() -> service.accept(alice.getId(), BOB))
                .isInstanceOf(NotAuthorizedException.class);

        assertThat(nominations.findById(alice.getId()).orElseThrow().getStatus())
                .isEqualTo(NominationStatus.PENDING);
    }

    // A known nomination id is not by itself authority to change it.
    @Test
    void anotherOwnerCannotEditOrRemoveSomeoneElsesNomination() {
        Nomination alice = nominate(ALICE, NomineePermission.CONSENT_VIEW);

        assertThatThrownBy(() -> service.updatePermissions(alice.getId(), "owner-2",
                Set.of(NomineePermission.CONSENT_REVOKE)))
                .isInstanceOf(NominationNotFoundException.class);
        assertThatThrownBy(() -> service.removeNomination(alice.getId(), "owner-2"))
                .isInstanceOf(NominationNotFoundException.class);

        assertThat(nominations.findById(alice.getId())).isPresent();
    }

    // A nomination exists so somebody else may act when the owner cannot.
    // Naming yourself grants nothing new and leaves a record that reads as a
    // delegation when none took place.
    @Test
    void anOwnerCannotNominateThemselves() {
        assertThatThrownBy(() -> service.nominate(ORG, OWNER, OWNER, "owner@example.com",
                Set.of(NomineePermission.CONSENT_VIEW)))
                .isInstanceOf(SelfNominationException.class);

        assertThat(service.getByOwnerId(OWNER)).isEmpty();
    }

    // Activation records that a specific grant was verified. Widening it
    // afterwards would put the nominee beyond what was reviewed while the
    // activation ticket still says otherwise.
    @Test
    void permissionsCannotBeWidenedOnceActive() {
        Nomination alice = nominate(ALICE, NomineePermission.CONSENT_VIEW);
        service.accept(alice.getId(), ALICE);
        service.activate(alice.getId(), "admin-1", "TICKET-1");

        assertThatThrownBy(() -> service.updatePermissions(alice.getId(), OWNER,
                Set.of(NomineePermission.CONSENT_VIEW, NomineePermission.CONSENT_REVOKE)))
                .isInstanceOf(PermissionsFrozenException.class);

        assertThat(nominations.findById(alice.getId()).orElseThrow().getPermissions())
                .containsExactly(NomineePermission.CONSENT_VIEW);
    }

    // Narrowing only ever takes away access the administrator already approved,
    // and an owner should never wait to reduce someone's reach.
    @Test
    void permissionsCanStillBeNarrowedWhileActive() {
        Nomination alice = nominate(ALICE, NomineePermission.CONSENT_VIEW,
                NomineePermission.CONSENT_REVOKE);
        service.accept(alice.getId(), ALICE);
        service.activate(alice.getId(), "admin-1", "TICKET-1");

        service.updatePermissions(alice.getId(), OWNER, Set.of(NomineePermission.CONSENT_VIEW));

        assertThat(nominations.findById(alice.getId()).orElseThrow().getPermissions())
                .containsExactly(NomineePermission.CONSENT_VIEW);
    }

    @Test
    void permissionsAreFreelyEditableBeforeActivation() {
        Nomination alice = nominate(ALICE, NomineePermission.CONSENT_VIEW);

        service.updatePermissions(alice.getId(), OWNER,
                Set.of(NomineePermission.CONSENT_VIEW, NomineePermission.CONSENT_REVOKE));

        assertThat(nominations.findById(alice.getId()).orElseThrow().getPermissions())
                .containsExactlyInAnyOrder(NomineePermission.CONSENT_VIEW,
                        NomineePermission.CONSENT_REVOKE);
    }

    // The owner keeps an immediate escape: removal needs no administrator.
    @Test
    void anOwnerCanAlwaysRemoveAnActiveNomination() {
        Nomination alice = nominate(ALICE, NomineePermission.CONSENT_VIEW);
        service.accept(alice.getId(), ALICE);
        service.activate(alice.getId(), "admin-1", "TICKET-1");

        service.removeNomination(alice.getId(), OWNER);

        assertThat(nominations.findById(alice.getId())).isEmpty();
    }

    @Test
    void onlyTheNamedNomineeMayReject() {
        Nomination alice = nominate(ALICE, NomineePermission.CONSENT_VIEW);

        assertThatThrownBy(() -> service.reject(alice.getId(), BOB))
                .isInstanceOf(NotAuthorizedException.class);

        service.reject(alice.getId(), ALICE);
        assertThat(nominations.findById(alice.getId()).orElseThrow().getStatus())
                .isEqualTo(NominationStatus.REJECTED);
    }

    // A refusal must not be quietly reversed by accepting afterwards.
    @Test
    void aRejectedNominationCannotThenBeAccepted() {
        Nomination alice = nominate(ALICE, NomineePermission.CONSENT_VIEW);
        service.reject(alice.getId(), ALICE);

        assertThatThrownBy(() -> service.accept(alice.getId(), ALICE))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void anOwnerWithNoNomineesGetsAnEmptyList() {
        assertThat(service.getByOwnerId("owner-with-nothing")).isEmpty();
    }

    // A caller holding a nomination must not be able to widen its own grant by
    // mutating the returned collection.
    @Test
    void grantedPermissionsCannotBeModifiedThroughTheGetter() {
        Nomination alice = nominate(ALICE, NomineePermission.CONSENT_VIEW);

        assertThatThrownBy(() -> alice.getPermissions().add(NomineePermission.CONSENT_REVOKE))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(alice.getPermissions()).containsExactly(NomineePermission.CONSENT_VIEW);
    }

    // The gate is the single answer both IS and the BFF act on, so an inactive
    // nomination must report no permissions rather than its stored set.
    @Test
    void theGateReportsNoPermissionsUntilActivated() {
        Nomination alice = nominate(ALICE, NomineePermission.CONSENT_VIEW);

        GateDecision pending = service.gateDecision(OWNER, ALICE);
        assertThat(pending.active()).isFalse();
        assertThat(pending.permissions()).isEmpty();

        service.accept(alice.getId(), ALICE);
        service.activate(alice.getId(), "admin-1", "TICKET-1");

        GateDecision active = service.gateDecision(OWNER, ALICE);
        assertThat(active.active()).isTrue();
        assertThat(active.grants(NomineePermission.CONSENT_VIEW)).isTrue();
        assertThat(active.grants(NomineePermission.CONSENT_REVOKE)).isFalse();

        service.deactivate(alice.getId(), "admin-1", "no longer required");
        assertThat(service.gateDecision(OWNER, ALICE).permissions()).isEmpty();
    }

    // The nominee's agreement is what makes a nomination legitimate. An
    // administrator activating one that was never accepted would grant access
    // nobody consented to, which is the thing the acceptance step exists for.
    @Test
    void aPendingNominationCannotBeActivated() {
        Nomination alice = nominate(ALICE, NomineePermission.CONSENT_VIEW);

        assertThatThrownBy(() -> service.activate(alice.getId(), "admin-1", "TICKET-1"))
                .isInstanceOf(InvalidNominationStateException.class);

        assertThat(nominations.findById(alice.getId()).orElseThrow().getStatus())
                .isEqualTo(NominationStatus.PENDING);
    }

    // A refusal is a decision, not an absence of one.
    @Test
    void aRejectedNominationCannotBeActivated() {
        Nomination alice = nominate(ALICE, NomineePermission.CONSENT_VIEW);
        service.reject(alice.getId(), ALICE);

        assertThatThrownBy(() -> service.activate(alice.getId(), "admin-1", "TICKET-1"))
                .isInstanceOf(InvalidNominationStateException.class);

        assertThat(nominations.findById(alice.getId()).orElseThrow().getStatus())
                .isEqualTo(NominationStatus.REJECTED);
    }

    // Withdrawing something that was never in force would overwrite the record
    // of how it actually ended.
    @Test
    void onlyAnActiveNominationCanBeWithdrawn() {
        Nomination alice = nominate(ALICE, NomineePermission.CONSENT_VIEW);
        service.accept(alice.getId(), ALICE);

        assertThatThrownBy(() -> service.deactivate(alice.getId(), "admin-1", "no longer needed"))
                .isInstanceOf(InvalidNominationStateException.class);

        assertThat(nominations.findById(alice.getId()).orElseThrow().getStatus())
                .isEqualTo(NominationStatus.ACCEPTED);
    }

    // The nominee agreed to a specific grant. A wider one is a different ask,
    // so their acceptance does not carry over to it.
    @Test
    void wideningAnAcceptedGrantSendsItBackToTheNominee() {
        Nomination alice = nominate(ALICE, NomineePermission.CONSENT_VIEW);
        service.accept(alice.getId(), ALICE);

        service.updatePermissions(alice.getId(), OWNER,
                Set.of(NomineePermission.CONSENT_VIEW, NomineePermission.CONSENT_APPROVE));

        Nomination stored = nominations.findById(alice.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(NominationStatus.PENDING);
        assertThat(stored.getAcceptedAt()).isNull();
        assertThat(stored.getPermissions()).contains(NomineePermission.CONSENT_APPROVE);
    }

    // ... and until they accept again, the wider grant cannot be activated.
    @Test
    void aWidenedGrantCannotBeActivatedUntilAcceptedAgain() {
        Nomination alice = nominate(ALICE, NomineePermission.CONSENT_VIEW);
        service.accept(alice.getId(), ALICE);
        service.updatePermissions(alice.getId(), OWNER,
                Set.of(NomineePermission.CONSENT_VIEW, NomineePermission.CONSENT_APPROVE));

        assertThatThrownBy(() -> service.activate(alice.getId(), "admin-1", "TICKET-1"))
                .isInstanceOf(InvalidNominationStateException.class);

        service.accept(alice.getId(), ALICE);
        service.activate(alice.getId(), "admin-1", "TICKET-1");

        assertThat(nominations.findById(alice.getId()).orElseThrow().getStatus())
                .isEqualTo(NominationStatus.ACTIVE);
    }

    // Narrowing only ever takes access away, so it needs no fresh agreement -
    // an owner should never wait on anyone to reduce someone's reach.
    @Test
    void narrowingAnAcceptedGrantKeepsTheAcceptance() {
        Nomination alice = nominate(ALICE, NomineePermission.CONSENT_VIEW,
                NomineePermission.CONSENT_REVOKE);
        service.accept(alice.getId(), ALICE);

        service.updatePermissions(alice.getId(), OWNER, Set.of(NomineePermission.CONSENT_VIEW));

        Nomination stored = nominations.findById(alice.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(NominationStatus.ACCEPTED);
        assertThat(stored.getPermissions()).containsExactly(NomineePermission.CONSENT_VIEW);
    }
}
