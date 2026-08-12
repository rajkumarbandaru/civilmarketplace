package com.civileng.marketplace.payment.controller;

import com.civileng.marketplace.payment.dto.CreateEscrowRequest;
import com.civileng.marketplace.payment.model.EscrowHold;
import com.civileng.marketplace.payment.service.EscrowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/escrow")
@RequiredArgsConstructor
@Tag(name = "Escrow", description = "Milestone escrow hold and release (SRS CP-06 FR-06)")
public class EscrowController {

    private static final Set<String> ADMIN_ROLES =
            Set.of("SUPER_ADMIN", "ADMIN", "SUB_ADMIN", "REGIONAL_ADMIN");

    private final EscrowService escrowService;

    @PostMapping
    @Operation(summary = "Create an escrow hold and its funding order")
    public ResponseEntity<EscrowHold> create(
            @RequestHeader("X-User-Id") Long payerId,
            @Valid @RequestBody CreateEscrowRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(escrowService.createHold(payerId, request));
    }

    @GetMapping("/mine")
    @Operation(summary = "Escrow holds where the caller is payer or payee")
    public ResponseEntity<Page<EscrowHold>> mine(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "payer") String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size);
        return ResponseEntity.ok("payee".equalsIgnoreCase(role)
                ? escrowService.listAsPayee(userId, pageable)
                : escrowService.listAsPayer(userId, pageable));
    }

    @GetMapping("/booking/{bookingId}")
    @Operation(summary = "Escrow holds against a booking")
    public ResponseEntity<List<EscrowHold>> forBooking(@PathVariable Long bookingId) {
        return ResponseEntity.ok(escrowService.getForBooking(bookingId));
    }

    /** Service-to-service: feeds project-service's budget-vs-actual rollup. */
    @GetMapping("/project/{projectId}")
    @Operation(summary = "Escrow holds against a project")
    public ResponseEntity<List<EscrowHold>> forProject(@PathVariable Long projectId) {
        return ResponseEntity.ok(escrowService.getForProject(projectId));
    }

    @GetMapping("/{escrowId}")
    @Operation(summary = "Get an escrow hold")
    public ResponseEntity<EscrowHold> get(
            @RequestHeader("X-User-Id") Long actorId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable Long escrowId) {
        return ResponseEntity.ok(escrowService.get(escrowId, actorId, isAdmin(role)));
    }

    @PostMapping("/{escrowId}/release")
    @Operation(summary = "Payer confirms the milestone and releases the hold")
    public ResponseEntity<EscrowHold> release(
            @RequestHeader("X-User-Id") Long actorId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable Long escrowId) {
        return ResponseEntity.ok(escrowService.release(escrowId, actorId, isAdmin(role)));
    }

    @PostMapping("/{escrowId}/refund")
    @Operation(summary = "Payer or admin returns the held funds")
    public ResponseEntity<EscrowHold> refund(
            @RequestHeader("X-User-Id") Long actorId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable Long escrowId,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return ResponseEntity.ok(escrowService.refund(escrowId, actorId, isAdmin(role), reason));
    }

    @PostMapping("/{escrowId}/dispute")
    @Operation(summary = "Either party freezes the hold pending resolution")
    public ResponseEntity<EscrowHold> dispute(
            @RequestHeader("X-User-Id") Long actorId,
            @PathVariable Long escrowId,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(escrowService.dispute(escrowId, actorId, body.get("reason")));
    }

    private boolean isAdmin(String role) {
        return role != null && ADMIN_ROLES.contains(role);
    }
}
