package com.openfgc.nomineeservice.repository;

import com.openfgc.nomineeservice.domain.Nomination;
import com.openfgc.nomineeservice.domain.NominationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NominationRepository extends JpaRepository<Nomination, String> {

    /**
     * All nominations made by one owner. An owner may nominate any number of
     * individuals (DPDP Rule 14(4)), each with its own permissions and status.
     */
    List<Nomination> findByOwnerId(String ownerId);

    List<Nomination> findByNomineeId(String nomineeId);

    /**
     * One specific pairing, which is what the gate and every permission check
     * resolve. Authorization is always decided for an (owner, nominee) pair,
     * never for an owner alone.
     */
    Optional<Nomination> findByOwnerIdAndNomineeId(String ownerId, String nomineeId);

    boolean existsByOwnerIdAndNomineeId(String ownerId, String nomineeId);

    boolean existsByOwnerIdAndNomineeIdAndStatus(String ownerId, String nomineeId, NominationStatus status);

    List<Nomination> findByOrgIdAndStatus(String orgId, NominationStatus status);
}
