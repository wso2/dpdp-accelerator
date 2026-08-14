package com.openfgc.nomineeservice.service;

import com.openfgc.nomineeservice.domain.NomineeAuditEvent;
import com.openfgc.nomineeservice.domain.NomineeAuditEvent.EventType;
import com.openfgc.nomineeservice.repository.NomineeAuditEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only writer of audit records.
 *
 * Rows are appended and never updated or deleted, so a record of what a nominee
 * did survives the nomination itself being changed or removed.
 */
@Service
public class NomineeAuditService {

    private final NomineeAuditEventRepository auditEvents;

    public NomineeAuditService(NomineeAuditEventRepository auditEvents) {
        this.auditEvents = auditEvents;
    }

    @Transactional
    public void record(String orgId, String nominationId, String ownerId, String nomineeId,
                       EventType type, String detail) {
        auditEvents.save(new NomineeAuditEvent(orgId, nominationId, ownerId, nomineeId, type, detail));
    }

}
