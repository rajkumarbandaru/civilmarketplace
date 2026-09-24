package com.civileng.marketplace.auth.controller;

import com.civileng.marketplace.auth.service.InvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Invitations. Two audiences: the public link the invitee opens, and — under {@code /admin}, which
 * the gateway never routes from outside — tenant-service creating a new workspace's owner.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Invitations", description = "Workspace owner accounts and first-password links")
public class InvitationController {

    private final InvitationService invitationService;

    public record OwnerRequest(String name, @NotBlank String email) { }

    public record InviteRequest(@NotBlank String linkBase, String workspaceName) { }

    public record AcceptRequest(@NotBlank String password) { }

    @PostMapping("/admin/tenant-owner")
    @Operation(summary = "Internal: create this workspace's owner account (no password)")
    public ResponseEntity<InvitationService.Owner> ensureOwner(@Valid @RequestBody OwnerRequest request) {
        return ResponseEntity.ok(invitationService.ensureOwner(request.name(), request.email()));
    }

    @PostMapping("/admin/users/{userId}/invitation")
    @Operation(summary = "Internal: email a user a single-use link to set their password")
    public ResponseEntity<Map<String, LocalDateTime>> invite(@PathVariable Long userId,
                                                             @Valid @RequestBody InviteRequest request) {
        return ResponseEntity.ok(Map.of("expiresAt",
                invitationService.invite(userId, request.linkBase(), request.workspaceName())));
    }

    @GetMapping("/invitations/{token}")
    @Operation(summary = "Who an invitation link is for")
    public ResponseEntity<InvitationService.InvitationPreview> preview(@PathVariable String token) {
        return ResponseEntity.ok(invitationService.preview(token));
    }

    @PostMapping("/invitations/{token}/accept")
    @Operation(summary = "Set a first password with an invitation link")
    public ResponseEntity<Map<String, Object>> accept(@PathVariable String token, @Valid @RequestBody AcceptRequest request) {
        invitationService.accept(token, request.password());
        return ResponseEntity.ok(Map.of("success", true, "message", "Password set. You can sign in now."));
    }
}
