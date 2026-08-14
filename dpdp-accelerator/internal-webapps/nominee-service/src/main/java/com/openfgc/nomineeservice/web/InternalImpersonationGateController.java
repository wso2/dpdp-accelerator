package com.openfgc.nomineeservice.web;

import com.openfgc.nomineeservice.domain.NomineePermission;
import com.openfgc.nomineeservice.security.InternalApiKey;
import com.openfgc.nomineeservice.service.GateDecision;
import com.openfgc.nomineeservice.service.NominationService;
import com.openfgc.nomineeservice.web.dto.GateActiveResponse;
import com.openfgc.nomineeservice.web.dto.RecordAuditRequest;
import com.openfgc.nomineeservice.web.dto.GatePermissionsResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Answers whether a nominee may act for an owner, and with which permissions.
 *
 * <p>The WSO2 IS impersonation validator calls this before issuing a subject
 * token, and the BFF calls it again on every acting request. Serving both from
 * one place keeps a single definition of what an active nomination grants, so
 * the check made at issue time and the check made per request cannot drift.
 *
 * <p>Callers are infrastructure rather than signed-in users, so these endpoints
 * are authenticated by a shared key instead of a bearer token.
 */
@RestController
public class InternalImpersonationGateController {

    private static final Logger log = LoggerFactory.getLogger(InternalImpersonationGateController.class);

    private final NominationService nominations;
    private final InternalApiKey apiKey;

    public InternalImpersonationGateController(NominationService nominations, InternalApiKey apiKey) {
        this.nominations = nominations;
        this.apiKey = apiKey;
    }

    @GetMapping("/internal/nominations/active")
    public ResponseEntity<GateActiveResponse> isActive(
            @RequestParam("owner") String ownerId,
            @RequestParam("nominee") String nomineeId,
            @RequestHeader(value = "X-Internal-Key", required = false) String key) {

        if (!apiKey.matches(key)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        GateDecision decision = nominations.gateDecision(ownerId, nomineeId);
        log.debug("Impersonation gate check: owner={} nominee={} -> active={}",
                ownerId, nomineeId, decision.active());
        return ResponseEntity.ok(new GateActiveResponse(decision.active()));
    }

    /**
     * The permissions the owner granted this nominee, letting the caller enforce
     * per-action limits such as view versus revoke. An inactive nomination
     * returns an empty list.
     */
    @GetMapping("/internal/nominations/permissions")
    public ResponseEntity<GatePermissionsResponse> permissions(
            @RequestParam("owner") String ownerId,
            @RequestParam("nominee") String nomineeId,
            @RequestHeader(value = "X-Internal-Key", required = false) String key) {

        if (!apiKey.matches(key)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        GateDecision decision = nominations.gateDecision(ownerId, nomineeId);
        List<String> permissions = decision.permissions().stream()
                .map(NomineePermission::name)
                .sorted()
                .toList();

        log.debug("Permissions check: owner={} nominee={} -> active={} permissions={}",
                ownerId, nomineeId, decision.active(), permissions);
        return ResponseEntity.ok(new GatePermissionsResponse(decision.active(), permissions));
    }

    /**
     * Appends an acting event to the audit chain.
     *
     * <p>A nominee reading an owner's records, and a nominee refused an action,
     * are both invisible to the Consent Server: it observes only successful
     * writes. They are recorded here or nowhere.
     */
    @PostMapping("/internal/nominations/audit")
    public ResponseEntity<Void> recordAuditEvent(
            @Valid @RequestBody RecordAuditRequest request,
            @RequestHeader(value = "X-Internal-Key", required = false) String key) {

        if (!apiKey.matches(key)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        nominations.recordActingEvent(
                request.ownerId(), request.nomineeId(), request.event(), request.detail());
        log.debug("Acting event recorded: owner={} nominee={} event={}",
                request.ownerId(), request.nomineeId(), request.event());
        return ResponseEntity.noContent().build();
    }
}
