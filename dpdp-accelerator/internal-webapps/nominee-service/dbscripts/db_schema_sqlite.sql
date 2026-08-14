-- Nominee service schema (SQLite). See db_schema_mysql.sql for the reasoning

    create table nominations (
        nomination_id varchar(36) not null,
        org_id varchar(255) not null default 'DEFAULT_ORG',
        owner_id varchar(255) not null,
        nominee_id varchar(255) not null,
        nominee_email varchar(320) not null,
        permissions varchar(255) not null,
        status varchar(255) not null check (status in ('PENDING','ACCEPTED','REJECTED','ACTIVE','DEACTIVATED')),
        nominated_at timestamp not null,
        accepted_at timestamp,
        activated_by varchar(255),
        activated_at timestamp,
        activation_ticket varchar(255),
        deactivated_by varchar(255),
        deactivated_at timestamp,
        deactivation_reason varchar(255),
        primary key (nomination_id)
    );

    create unique index uq_owner_nominee on nominations (owner_id, nominee_id, org_id);
    create index idx_nominee on nominations (nominee_id);
    create index idx_org_status on nominations (org_id, status);

    create table nominee_audit_events (
        audit_event_id varchar(36) not null,
        org_id varchar(255) not null default 'DEFAULT_ORG',
        nomination_id varchar(36),
        owner_id varchar(255) not null,
        nominee_id varchar(255) not null,
        event_type varchar(255) not null check (event_type in ('NOMINATED','PERMISSIONS_CHANGED','ACCEPTED','REJECTED','ACTIVATED','DEACTIVATED','REMOVED','SESSION_STARTED','SESSION_DENIED','ACTION_PERFORMED','ACTION_DENIED')),
        detail text,
        occurred_at timestamp not null,
        primary key (audit_event_id)
    );

    create index idx_nomination_time on nominee_audit_events (nomination_id, occurred_at);
    create index idx_owner_time on nominee_audit_events (owner_id, occurred_at);
    create index idx_org_time on nominee_audit_events (org_id, occurred_at);
