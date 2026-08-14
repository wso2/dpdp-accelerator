package com.openfgc.nomineeservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One append-only record of something that happened to a nomination. Rows are
 * never updated or deleted once written.
 */
@Entity
@Table(name = "nominee_audit_events",
       // Every read of this table is ordered by time, so each index ends in
       // occurred_at and the database never has to sort the result set.
       indexes = {
               @Index(name = "idx_nomination_time", columnList = "nomination_id, occurred_at"),
               @Index(name = "idx_owner_time", columnList = "owner_id, occurred_at"),
               @Index(name = "idx_org_time", columnList = "org_id, occurred_at")
       })
public class NomineeAuditEvent {

    public enum EventType {
        NOMINATED,
        PERMISSIONS_CHANGED,
        ACCEPTED,
        REJECTED,
        ACTIVATED,
        DEACTIVATED,
        REMOVED,
        SESSION_STARTED,
        SESSION_DENIED,
        ACTION_PERFORMED,
        ACTION_DENIED
    }

    /** Identifies this one recorded event. One nomination produces many. */
    @Id
    @Column(name = "audit_event_id", length = 36)
    private String id = UUID.randomUUID().toString();

    /**
     * The organization this event belongs to, carried by every table in OpenFGC.
     */
    @Column(nullable = false)
    private String orgId;

    /**
     * Which nomination this event was about - the same value, under the same
     * name, as {@code nominations.nomination_id}.
     *
     * <p>Nullable, and deliberately not a foreign key. The log outlives what it
     * describes: removing a nomination must leave its history standing, which a
     * foreign key could only prevent or cascade away. An attempt to act with no
     * nomination at all is recorded with this column null, and that is among the
     * most important events the trail captures.
     */
    @Column(name = "nomination_id", length = 36)
    private String nominationId;

    /**
     * The people involved, copied rather than joined. An audit row is a snapshot
     * of what was true when the event happened, and must stay readable after the
     * nomination it refers to has been removed.
     */
    @Column(nullable = false)
    private String ownerId;

    @Column(nullable = false)
    private String nomineeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventType eventType;

    /**
     * Free-form context, such as a ticket reference or a permission change.
     *
     * <p>TEXT rather than a bounded column: a permission change renders both the
     * old and new sets, and this write shares the caller's transaction. A value
     * too long for the column would not merely lose a log line, it would roll
     * back the operation being logged.
     */
    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(nullable = false)
    private Instant occurredAt = Instant.now();

    protected NomineeAuditEvent() {
        // JPA
    }

    public NomineeAuditEvent(String orgId, String nominationId, String ownerId, String nomineeId,
                              EventType eventType, String detail) {
        this.orgId = orgId;
        this.nominationId = nominationId;
        this.ownerId = ownerId;
        this.nomineeId = nomineeId;
        this.eventType = eventType;
        this.detail = detail;
    }

    public String getId() {
        return id;
    }

    public String getOrgId() {
        return orgId;
    }

    public String getNominationId() {
        return nominationId;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getNomineeId() {
        return nomineeId;
    }

    public EventType getEventType() {
        return eventType;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
