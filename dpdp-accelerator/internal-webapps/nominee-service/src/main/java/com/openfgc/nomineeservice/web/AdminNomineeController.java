package com.openfgc.nomineeservice.web;

import com.openfgc.nomineeservice.security.OrgId;
import com.openfgc.nomineeservice.service.NominationService;
import com.openfgc.nomineeservice.web.dto.ActivateRequest;
import com.openfgc.nomineeservice.web.dto.DeactivateRequest;
import com.openfgc.nomineeservice.web.dto.NominationResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrative review of nominations.
 *
 * <p>Activation is deliberately not automatic. A nominee who has accepted still
 * cannot act until an administrator activates the nomination against a recorded
 * ticket reference, which is where the manual legal verification is evidenced.
 *
 * <p>Gated on the {@code :any} profile scopes, which the identity server grants
 * only to accounts holding an administrative role.
 */
@RestController
public class AdminNomineeController {

    private final NominationService nominationService;
    private final OrgId orgId;

    public AdminNomineeController(NominationService nominationService, OrgId orgId) {
        this.nominationService = nominationService;
        this.orgId = orgId;
    }

    @GetMapping("/admin/nominations")
    @PreAuthorize("hasAuthority('SCOPE_portal:profile:read:any')")
    public List<NominationResponse> getByOwner(@RequestParam String ownerId) {
        return nominationService.getByOwnerId(ownerId).stream()
                .map(NominationResponse::from)
                .toList();
    }

    @GetMapping("/admin/nominations/pending")
    @PreAuthorize("hasAuthority('SCOPE_portal:profile:read:any')")
    public List<NominationResponse> pending(@AuthenticationPrincipal Jwt jwt) {
        return nominationService.getPendingActivation(orgId.from(jwt)).stream()
                .map(NominationResponse::from)
                .toList();
    }

    @PostMapping("/admin/nominations/{id}/activate")
    @PreAuthorize("hasAuthority('SCOPE_portal:profile:write:any')")
    public NominationResponse activate(@PathVariable String id,
                                        @AuthenticationPrincipal Jwt jwt,
                                        @Valid @RequestBody ActivateRequest request) {
        return NominationResponse.from(nominationService.activate(id, jwt.getSubject(), request.ticketReference()));
    }

    @PostMapping("/admin/nominations/{id}/deactivate")
    @PreAuthorize("hasAuthority('SCOPE_portal:profile:write:any')")
    public NominationResponse deactivate(@PathVariable String id,
                                          @AuthenticationPrincipal Jwt jwt,
                                          @Valid @RequestBody DeactivateRequest request) {
        return NominationResponse.from(nominationService.deactivate(id, jwt.getSubject(), request.reason()));
    }
}
