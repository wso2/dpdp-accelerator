package com.openfgc.nomineeservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * One owner-to-nominee pairing: who the nominee is, what they were granted, and
 * whether the pairing is currently active. The impersonation gate reads this
 * record before any impersonation token is issued.
 *
 * <p>An owner may have any number of these. DPDP Rule 14(4) allows nominating
 * "one or more individuals", and each nomination carries its own permission set,
 * status and lifecycle, so one nominee can be view-only while another may revoke,
 * and activating or deactivating one never touches the others.
 *
 * <p>The unique constraint is on the (owner, nominee) <i>pair</i> rather than on
 * the owner: the same person may not be nominated twice by the same owner, but
 * the owner is otherwise unrestricted.
 *
 * <p>State changes go through the behaviour methods below rather than setters.
 * Each transition sets every field that transition implies, so a nomination
 * cannot be left half-way between two states.
 */
@Entity
@Table(name = "nominations",
       // Column order on the unique key is chosen to serve reads, not just to
       // enforce uniqueness - which is identical whatever the order. Leading on
       // ownerId lets this same index answer the gate lookup
       // (owner + nominee, run on every acting request) and the owner's own
       // nominee list. Leading on orgId instead would leave both as full scans,
       // because neither query filters on the organization.
       uniqueConstraints = @UniqueConstraint(
               name = "uq_owner_nominee",
               columnNames = {"owner_id", "nominee_id", "org_id"}),
       indexes = {
               @Index(name = "idx_nominee", columnList = "nominee_id"),
               @Index(name = "idx_org_status", columnList = "org_id, status")
       })
public class Nomination {

    /**
     * Identifies this nomination.
     *
     * <p>A surrogate key rather than the natural (owner, nominee) pair: an owner
     * may remove a nomination and appoint the same person again later, and those
     * are two distinct grants with their own permissions, tickets and histories.
     * A natural key would merge them and make their audit trails
     * indistinguishable.
     *
     * <p>Generated here rather than by the database, so the nomination has an
     * identity before it is saved and its first audit event can be written in
     * the same transaction.
     */
    @Id
    @Column(name = "nomination_id", length = 36)
    private String id = UUID.randomUUID().toString();

    /**
     * The organization this nomination belongs to, carried by every table in
     * OpenFGC. Taken from the caller's token or configuration, never from the
     * request.
     */
    @Column(nullable = false)
    private String orgId;

    @Column(nullable = false)
    private String ownerId;

    @Column(nullable = false)
    private String nomineeId;

    @Column(nullable = false, length = 320)
    private String nomineeEmail;

    /**
     * Held in one column rather than a side table - see
     * {@link NomineePermissionSetConverter} for why, and for the warning about
     * matching that column with LIKE.
     */
    @Convert(converter = NomineePermissionSetConverter.class)
    @Column(name = "permissions", nullable = false)
    private Set<NomineePermission> permissions = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NominationStatus status = NominationStatus.PENDING;

    @Column(nullable = false)
    private Instant nominatedAt = Instant.now();

    /** Null until the nominee accepts - the null is the fact that it has not happened. */
    private Instant acceptedAt;

    private String activatedBy;
    private Instant activatedAt;
    private String activationTicket;

    private String deactivatedBy;
    private Instant deactivatedAt;
    private String deactivationReason;

    protected Nomination() {
        // JPA
    }

    public Nomination(String orgId, String ownerId, String nomineeId, String nomineeEmail,
                       Set<NomineePermission> permissions) {
        this.orgId = Objects.requireNonNull(orgId, "orgId");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.nomineeId = Objects.requireNonNull(nomineeId, "nomineeId");
        this.nomineeEmail = Objects.requireNonNull(nomineeEmail, "nomineeEmail");
        this.permissions = new HashSet<>(Objects.requireNonNull(permissions, "permissions"));
    }

    public String getId() {
        return id;
    }

    public String getOrgId() {
        return orgId;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getNomineeId() {
        return nomineeId;
    }

    public String getNomineeEmail() {
        return nomineeEmail;
    }

    /**
     * The granted permissions, unmodifiable. Changes go through
     * {@link #setPermissions(Set)} so the owner's grant cannot be widened by a
     * caller that merely holds a reference to this nomination.
     */
    public Set<NomineePermission> getPermissions() {
        return Collections.unmodifiableSet(permissions);
    }

    /** Replaces the granted permissions. */
    public void setPermissions(Set<NomineePermission> replacement) {
        Objects.requireNonNull(replacement, "replacement");
        this.permissions.clear();
        this.permissions.addAll(replacement);
    }

    public boolean grants(NomineePermission permission) {
        return permissions.contains(permission);
    }

    public NominationStatus getStatus() {
        return status;
    }

    public Instant getNominatedAt() {
        return nominatedAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    /**
     * Records the nominee's acceptance. Acceptance alone does not grant access:
     * the nomination stays inactive until an administrator activates it.
     */
    public void accept() {
        this.status = NominationStatus.ACCEPTED;
        this.acceptedAt = Instant.now();
    }

    /**
     * Withdraws an acceptance, returning the nomination to the nominee.
     *
     * <p>Used when the owner widens a grant that was already accepted: what the
     * nominee agreed to no longer describes what they would hold, so the
     * agreement does not carry over to the wider one.
     */
    public void reopenForAcceptance() {
        this.status = NominationStatus.PENDING;
        this.acceptedAt = null;
    }

    /** Records the nominee's refusal. */
    public void reject() {
        this.status = NominationStatus.REJECTED;
    }

    /** Whether an administrator has brought this nomination into force. */
    public boolean isActive() {
        return this.status == NominationStatus.ACTIVE;
    }

    public String getActivatedBy() {
        return activatedBy;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public String getActivationTicket() {
        return activationTicket;
    }

    /**
     * Activates the nomination and clears any prior deactivation, so a
     * reactivated nomination does not carry a stale reason or actor.
     */
    public void activate(String adminId, String ticketReference) {
        this.status = NominationStatus.ACTIVE;
        this.activatedBy = adminId;
        this.activatedAt = Instant.now();
        this.activationTicket = ticketReference;
        this.deactivatedBy = null;
        this.deactivatedAt = null;
        this.deactivationReason = null;
    }

    public String getDeactivatedBy() {
        return deactivatedBy;
    }

    public Instant getDeactivatedAt() {
        return deactivatedAt;
    }

    public String getDeactivationReason() {
        return deactivationReason;
    }

    public void deactivate(String adminId, String reason) {
        this.status = NominationStatus.DEACTIVATED;
        this.deactivatedBy = adminId;
        this.deactivatedAt = Instant.now();
        this.deactivationReason = reason;
    }

    public boolean isActiveFor(String ownerId, String nomineeId) {
        return this.status == NominationStatus.ACTIVE
                && this.ownerId.equals(ownerId)
                && this.nomineeId.equals(nomineeId);
    }
}
