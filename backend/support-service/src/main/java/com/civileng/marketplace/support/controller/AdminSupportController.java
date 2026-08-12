package com.civileng.marketplace.support.controller;

import com.civileng.marketplace.support.dto.AssignRequest;
import com.civileng.marketplace.support.exception.AccessDeniedException;
import com.civileng.marketplace.support.model.SupportTicket;
import com.civileng.marketplace.support.service.SupportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

/** Staff queue and ticket assignment. */
@RestController
@RequestMapping("/api/v1/admin/support/tickets")
@RequiredArgsConstructor
@Tag(name = "Admin Support", description = "Staff ticket queue and assignment")
public class AdminSupportController {

    private static final Set<String> ADMIN_ROLES =
            Set.of("SUPER_ADMIN", "ADMIN", "SUB_ADMIN", "REGIONAL_ADMIN");

    private final SupportService supportService;

    @GetMapping
    @Operation(summary = "List every ticket on the platform")
    public ResponseEntity<Page<SupportTicket>> list(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAdmin(role);
        return ResponseEntity.ok(supportService.listAll(status, PageRequest.of(page, size)));
    }

    @PatchMapping("/{ticketId}/assign")
    @Operation(summary = "Assign a ticket to a staff member")
    public ResponseEntity<SupportTicket> assign(
            @RequestHeader("X-User-Id") Long actorId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable Long ticketId,
            @Valid @RequestBody AssignRequest request) {
        return ResponseEntity.ok(supportService.assignTicket(ticketId, actorId, role, request));
    }

    private void requireAdmin(String role) {
        if (role == null || !ADMIN_ROLES.contains(role)) {
            throw new AccessDeniedException("Admin role required");
        }
    }
}
