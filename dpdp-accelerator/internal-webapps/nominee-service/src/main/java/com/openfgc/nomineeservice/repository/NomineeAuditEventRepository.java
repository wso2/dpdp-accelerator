package com.openfgc.nomineeservice.repository;

import com.openfgc.nomineeservice.domain.NomineeAuditEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NomineeAuditEventRepository extends JpaRepository<NomineeAuditEvent, String> {

    List<NomineeAuditEvent> findByNominationIdOrderByOccurredAtDesc(String nominationId);

    List<NomineeAuditEvent> findByOwnerIdOrderByOccurredAtDesc(String ownerId);

}
