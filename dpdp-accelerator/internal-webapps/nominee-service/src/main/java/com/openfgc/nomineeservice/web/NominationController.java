package com.openfgc.nomineeservice.web;

import com.openfgc.nomineeservice.domain.Nomination;
import com.openfgc.nomineeservice.security.ActingTokenGuard;
import com.openfgc.nomineeservice.security.OrgId;
import com.openfgc.nomineeservice.service.NominationService;
import com.openfgc.nomineeservice.web.dto.CreateNominationRequest;
import com.openfgc.nomineeservice.web.dto.NominationResponse;
import com.openfgc.nomineeservice.web.dto.UpdatePermissionsRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Owner- and nominee-facing nomination endpoints, called directly by the
 * frontend.
 *
 * <p>The caller's identity is always taken from the validated access token's
 * subject rather than from a request parameter or header, so a caller cannot
 * name themselves as somebody else. Every endpoint additionally refuses a token
 * that is acting on another user's behalf: managing nominations is a right the
 * owner exercises personally.
 */
@RestController
public class NominationController {

    private final NominationService nominationService;
    private final ActingTokenGuard actingTokenGuard;
    private final OrgId orgId;

    public NominationController(NominationService nominationService,
                                 ActingTokenGuard actingTokenGuard,
                                 OrgId orgId) {
        this.nominationService = nominationService;
        this.actingTokenGuard = actingTokenGuard;
        this.orgId = orgId;
    }

    /**
     * Adds a nominee to the caller's existing nominations. An owner may nominate
     * any number of people, each with its own permission set.
     */
    @PostMapping("/me/nominees")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SCOPE_portal:profile:write:self')")
    public NominationResponse addMyNominee(@AuthenticationPrincipal Jwt jwt,
                                            @Valid @RequestBody CreateNominationRequest request) {
        actingTokenGuard.requireNotActingForSomeoneElse(jwt);
        Nomination nomination = nominationService.nominate(
                orgId.from(jwt), jwt.getSubject(), request.nomineeId(), request.nomineeEmail(),
                request.permissions());
        return NominationResponse.from(nomination);
    }

    @GetMapping("/me/nominees")
    @PreAuthorize("hasAuthority('SCOPE_portal:profile:read:self')")
    public List<NominationResponse> getMyNominees(@AuthenticationPrincipal Jwt jwt) {
        actingTokenGuard.requireNotActingForSomeoneElse(jwt);
        return nominationService.getByOwnerId(jwt.getSubject()).stream()
                .map(NominationResponse::from)
                .toList();
    }

    /** Changes what one nominee may do, leaving the owner's others untouched. */
    @PatchMapping("/me/nominees/{id}")
    @PreAuthorize("hasAuthority('SCOPE_portal:profile:write:self')")
    public NominationResponse updateMyNomineePermissions(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id,
            @Valid @RequestBody UpdatePermissionsRequest request) {
        actingTokenGuard.requireNotActingForSomeoneElse(jwt);
        return NominationResponse.from(
                nominationService.updatePermissions(id, jwt.getSubject(), request.permissions()));
    }

    /** Removes one nominee by id. */
    @DeleteMapping("/me/nominees/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SCOPE_portal:profile:write:self')")
    public void removeMyNominee(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        actingTokenGuard.requireNotActingForSomeoneElse(jwt);
        nominationService.removeNomination(id, jwt.getSubject());
    }

    @PostMapping("/nominations/{id}/accept")
    @PreAuthorize("hasAuthority('SCOPE_portal:profile:write:self')")
    public NominationResponse accept(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        actingTokenGuard.requireNotActingForSomeoneElse(jwt);
        return NominationResponse.from(nominationService.accept(id, jwt.getSubject()));
    }

    /** The nominee declines. Only the person named on the nomination may do so. */
    @PostMapping("/nominations/{id}/reject")
    @PreAuthorize("hasAuthority('SCOPE_portal:profile:write:self')")
    public NominationResponse reject(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        actingTokenGuard.requireNotActingForSomeoneElse(jwt);
        return NominationResponse.from(nominationService.reject(id, jwt.getSubject()));
    }

    @GetMapping("/nominated-for")
    @PreAuthorize("hasAuthority('SCOPE_portal:profile:read:self')")
    public List<NominationResponse> nominatedFor(@AuthenticationPrincipal Jwt jwt) {
        actingTokenGuard.requireNotActingForSomeoneElse(jwt);
        return nominationService.getByNomineeId(jwt.getSubject()).stream()
                .map(NominationResponse::from)
                .toList();
    }
}
