package com.civileng.marketplace.payment.controller;

import com.civileng.marketplace.payment.exception.AccessDeniedException;
import com.civileng.marketplace.payment.model.EscrowHold;
import com.civileng.marketplace.payment.service.EscrowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin/escrow")
@RequiredArgsConstructor
@Tag(name = "Admin Escrow", description = "Dispute-linked holds and platform-wide escrow view")
public class AdminEscrowController {

    private static final Set<String> ADMIN_ROLES =
            Set.of("SUPER_ADMIN", "ADMIN", "SUB_ADMIN", "REGIONAL_ADMIN");

    private final EscrowService escrowService;

    @GetMapping
    @Operation(summary = "Every escrow hold, optionally filtered by status")
    public ResponseEntity<Page<EscrowHold>> list(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAdmin(role);
        return ResponseEntity.ok(escrowService.listAll(status, PageRequest.of(page, size)));
    }

    @PostMapping("/{escrowId}/resolve")
    @Operation(summary = "Resolve a dispute: RELEASE, REFUND or HOLD")
    public ResponseEntity<EscrowHold> resolve(
            @RequestHeader(value = "X-User-Id", required = false) Long adminId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable Long escrowId,
            @RequestBody Map<String, String> body) {
        requireAdmin(role);
        return ResponseEntity.ok(escrowService.resolveDispute(
                escrowId, adminId, body.get("outcome"), body.get("reason")));
    }

    private void requireAdmin(String role) {
        if (role == null || !ADMIN_ROLES.contains(role)) {
            throw new AccessDeniedException("Admin role required");
        }
    }
}
