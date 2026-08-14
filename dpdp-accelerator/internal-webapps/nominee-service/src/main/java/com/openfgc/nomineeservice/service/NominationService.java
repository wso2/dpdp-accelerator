package com.openfgc.nomineeservice.service;

import com.openfgc.nomineeservice.domain.Nomination;
import com.openfgc.nomineeservice.domain.NominationStatus;
import com.openfgc.nomineeservice.domain.NomineeAuditEvent.EventType;
import com.openfgc.nomineeservice.domain.NomineePermission;
import com.openfgc.nomineeservice.repository.NominationRepository;
import com.openfgc.nomineeservice.security.OrgId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the nomination lifecycle: nominate, accept, activate, deactivate.
 *
 * <p>An owner may nominate any number of individuals. Every mutation below is
 * addressed to one specific nomination, so adding, editing or removing one
 * nominee never disturbs the others.
 */
@Service
public class NominationService {

    private final NominationRepository nominations;
    private final NomineeAuditService auditService;

    public NominationService(NominationRepository nominations, NomineeAuditService auditService) {
        this.nominations = nominations;
        this.auditService = auditService;
    }

    /**
     * Adds a nominee to this owner's existing nominations. The same person may
     * not be nominated twice by the same owner.
     */
    @Transactional
    public Nomination nominate(String orgId, String ownerId, String nomineeId, String nomineeEmail,
                                Set<NomineePermission> permissions) {
        if (ownerId.equals(nomineeId)) {
            throw new SelfNominationException(ownerId);
        }
        if (nominations.existsByOwnerIdAndNomineeId(ownerId, nomineeId)) {
            throw new DuplicateNominationException(ownerId, nomineeId);
        }

        Nomination nomination = new Nomination(orgId, ownerId, nomineeId, nomineeEmail,
                NomineePermission.expand(permissions));
        nominations.save(nomination);
        audit(nomination, EventType.NOMINATED, "permissions=" + permissions);
        return nomination;
    }

    /**
     * Replaces the permissions granted to one nominee.
     *
     * <p>Scoped to the owner: a caller can only edit a nomination they made, so a
     * known nomination id is not by itself authority to change it.
     *
     * <p>Once an administrator has activated the nomination the grant can only be
     * narrowed. Activation records that a specific set of permissions was
     * verified; widening it afterwards would leave the nominee holding more than
     * was reviewed while the activation ticket still says otherwise. Narrowing
     * stays open because it only ever takes access away, and an owner should
     * never wait on an administrator to reduce someone's reach.
     */
    @Transactional
    public Nomination updatePermissions(String nominationId, String ownerId,
                                         Set<NomineePermission> permissions) {
        Nomination nomination = getOwned(nominationId, ownerId);
        Set<NomineePermission> previous = Set.copyOf(nomination.getPermissions());
        Set<NomineePermission> requested = NomineePermission.expand(permissions);

        boolean widening = !previous.containsAll(requested);

        if (nomination.isActive() && widening) {
            throw new PermissionsFrozenException(nominationId);
        }

        nomination.setPermissions(requested);

        // The nominee agreed to a specific grant. Widening it afterwards would
        // leave them holding authority they never saw, and would let the
        // administrator's ticket certify a grant that was never accepted, so the
        // acceptance is withdrawn and the nominee is asked again.
        //
        // Narrowing never needs re-acceptance: it only takes access away, and an
        // owner should not have to wait on anyone to reduce someone's reach.
        boolean reopened = widening && nomination.getStatus() == NominationStatus.ACCEPTED;
        if (reopened) {
            nomination.reopenForAcceptance();
        }

        audit(nomination, EventType.PERMISSIONS_CHANGED,
                "from=" + previous + " to=" + requested
                        + (reopened ? " (widened - acceptance withdrawn, awaiting the nominee again)" : ""));
        return nomination;
    }

    /**
     * The nominee declines. As with accepting, the caller must be the person
     * named on the nomination.
     *
     * <p>Terminal: a refusal is not reversed by accepting afterwards. The owner
     * creates a new nomination if they want to ask again, which leaves the
     * refusal standing in the record.
     */
    @Transactional
    public Nomination reject(String nominationId, String callerId) {
        Nomination nomination = get(nominationId);
        if (!nomination.getNomineeId().equals(callerId)) {
            throw new NotAuthorizedException("Only the nominated user may reject this nomination");
        }
        if (nomination.getStatus() != NominationStatus.PENDING) {
            throw new NotAuthorizedException(
                    "Only a pending nomination can be rejected; this one is "
                            + nomination.getStatus());
        }
        nomination.reject();
        audit(nomination, EventType.REJECTED, null);
        return nomination;
    }

    /**
     * The nominee accepts. The caller must be the nominee named on this
     * nomination - holding the right scope is not enough, or any authenticated
     * user could accept someone else's nomination by id.
     */
    @Transactional
    public Nomination accept(String nominationId, String callerId) {
        Nomination nomination = get(nominationId);
        if (!nomination.getNomineeId().equals(callerId)) {
            throw new NotAuthorizedException("Only the nominated user may accept this nomination");
        }
        // A refusal stands. Accepting afterwards would reverse it silently, and
        // the owner would have no signal that the nominee had already declined.
        if (nomination.getStatus() != NominationStatus.PENDING) {
            throw new NotAuthorizedException(
                    "Only a pending nomination can be accepted; this one is "
                            + nomination.getStatus());
        }
        nomination.accept();
        audit(nomination, EventType.ACCEPTED, null);
        return nomination;
    }

    @Transactional
    public Nomination activate(String nominationId, String adminId, String ticketReference) {
        Nomination nomination = get(nominationId);
        // Only an accepted nomination may be activated. Activating a pending one
        // would grant access the nominee never agreed to; activating a rejected
        // one would overrule a refusal they made deliberately.
        if (nomination.getStatus() != NominationStatus.ACCEPTED) {
            throw new InvalidNominationStateException(
                    "activated", nominationId, nomination.getStatus(), NominationStatus.ACCEPTED);
        }
        nomination.activate(adminId, ticketReference);
        audit(nomination, EventType.ACTIVATED, "admin=" + adminId + " ticket=" + ticketReference);
        return nomination;
    }

    @Transactional
    public Nomination deactivate(String nominationId, String adminId, String reason) {
        Nomination nomination = get(nominationId);
        // Withdrawing anything other than an active nomination would overwrite a
        // record of why it ended with a second, unrelated one.
        if (!nomination.isActive()) {
            throw new InvalidNominationStateException(
                    "withdrawn", nominationId, nomination.getStatus(), NominationStatus.ACTIVE);
        }
        nomination.deactivate(adminId, reason);
        audit(nomination, EventType.DEACTIVATED, "admin=" + adminId + " reason=" + reason);
        return nomination;
    }

    /** Removes one nominee, leaving the owner's other nominations untouched. */
    @Transactional
    public void removeNomination(String nominationId, String ownerId) {
        Nomination nomination = getOwned(nominationId, ownerId);
        audit(nomination, EventType.REMOVED, null);
        nominations.delete(nomination);
    }

    /**
     * Loads a nomination and confirms it belongs to this owner. Returning
     * "not found" rather than "forbidden" for someone else's nomination avoids
     * confirming that an id exists.
     */
    private Nomination getOwned(String nominationId, String ownerId) {
        Nomination nomination = get(nominationId);
        if (!nomination.getOwnerId().equals(ownerId)) {
            throw new NominationNotFoundException(nominationId);
        }
        return nomination;
    }

    public Nomination get(String nominationId) {
        return nominations.findById(nominationId)
                .orElseThrow(() -> new NominationNotFoundException(nominationId));
    }

    /** Every nomination this owner has made. May be empty. */
    public List<Nomination> getByOwnerId(String ownerId) {
        return nominations.findByOwnerId(ownerId);
    }

    public List<Nomination> getByNomineeId(String nomineeId) {
        return nominations.findByNomineeId(nomineeId);
    }

    /** Nominations accepted by the nominee but not yet activated - the admin review queue. */
    public List<Nomination> getPendingActivation(String orgId) {
        return nominations.findByOrgIdAndStatus(orgId, NominationStatus.ACCEPTED);
    }

    /**
     * Resolves what one nominee may currently do on one owner's behalf.
     *
     * <p>This is read on every acting request rather than only at token issue, so
     * removing a permission or deactivating a nomination takes effect on the next
     * request instead of when the token expires.
     */
    @Transactional(readOnly = true)
    public GateDecision gateDecision(String ownerId, String nomineeId) {
        return nominations.findByOwnerIdAndNomineeId(ownerId, nomineeId)
                .filter(nomination -> nomination.getStatus() == NominationStatus.ACTIVE)
                .map(nomination -> GateDecision.allowed(nomination.getPermissions()))
                .orElseGet(GateDecision::denied);
    }

    /**
     * Appends an event observed elsewhere - a nominee starting a session, or an
     * action allowed or refused while acting.
     *
     * <p>The nomination is resolved from the owner-nominee pair rather than taken
     * from the caller, so an event cannot be attributed to an unrelated
     * nomination. A pair with no nomination is still recorded, with a null
     * nomination id: an attempt to act without one is exactly the kind of event
     * the trail exists to capture.
     */
    @Transactional
    public void recordActingEvent(String ownerId, String nomineeId, EventType type, String detail) {
        Optional<Nomination> nomination = nominations.findByOwnerIdAndNomineeId(ownerId, nomineeId);
        // These arrive on the internal endpoints, which authenticate with a shared
        // key and carry no user token, so the organization comes from the
        // nomination itself. An attempt to act with no nomination at all is still
        // recorded, under the default organization.
        auditService.record(nomination.map(Nomination::getOrgId).orElse(OrgId.DEFAULT),
                nomination.map(Nomination::getId).orElse(null), ownerId, nomineeId, type, detail);
    }

    void audit(Nomination nomination, EventType type, String detail) {
        auditService.record(nomination.getOrgId(), nomination.getId(), nomination.getOwnerId(),
                nomination.getNomineeId(), type, detail);
    }
}
