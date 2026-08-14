-- Nominee service schema (MySQL).
    create table nominations (
        nomination_id varchar(36) not null,
        org_id varchar(255) not null default 'DEFAULT_ORG',
        owner_id varchar(255) not null,
        nominee_id varchar(255) not null,
        nominee_email varchar(320) not null,
        permissions varchar(255) not null,
        status enum ('ACCEPTED','ACTIVE','DEACTIVATED','PENDING','REJECTED') not null,
        nominated_at datetime(6) not null,
        accepted_at datetime(6),
        activated_by varchar(255),
        activated_at datetime(6),
        activation_ticket varchar(255),
        deactivated_by varchar(255),
        deactivated_at datetime(6),
        deactivation_reason varchar(255),
        primary key (nomination_id),
        unique key uq_owner_nominee (owner_id, nominee_id, org_id),
        key idx_nominee (nominee_id),
        key idx_org_status (org_id, status)
    ) engine=InnoDB;

    create table nominee_audit_events (
        audit_event_id varchar(36) not null,
        org_id varchar(255) not null default 'DEFAULT_ORG',
        nomination_id varchar(36),
        owner_id varchar(255) not null,
        nominee_id varchar(255) not null,
        event_type enum ('ACCEPTED','ACTION_DENIED','ACTION_PERFORMED','ACTIVATED','DEACTIVATED','NOMINATED','PERMISSIONS_CHANGED','REJECTED','REMOVED','SESSION_DENIED','SESSION_STARTED') not null,
        detail text,
        occurred_at datetime(6) not null,
        primary key (audit_event_id),
        key idx_nomination_time (nomination_id, occurred_at),
        key idx_owner_time (owner_id, occurred_at),
        key idx_org_time (org_id, occurred_at)
    ) engine=InnoDB;
