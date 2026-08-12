package com.civileng.marketplace.user.controller;

import com.civileng.marketplace.user.model.KycDocument;
import com.civileng.marketplace.user.service.KycService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/users/admin/kyc")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin KYC Management", description = "Admin endpoints for KYC document review")
public class AdminKycController {

    private static final Set<String> ADMIN_ROLES =
            Set.of("SUPER_ADMIN", "ADMIN", "SUB_ADMIN", "REGIONAL_ADMIN");

    private final KycService kycService;

    @GetMapping("/pending")
    @Operation(summary = "List KYC documents pending review")
    public ResponseEntity<Map<String, Object>> getPending(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Id", required = false) Long actorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAdmin(role);
        Page<KycDocument> result = kycService.getPendingDocuments(
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt")),
                actorId, role);
        return ResponseEntity.ok(Map.of(
                "success", true, "data", result.getContent(),
                "page", page, "size", size,
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages()
        ));
    }

    @PutMapping("/{documentId}/approve")
    @Operation(summary = "Approve a KYC document")
    public ResponseEntity<KycDocument> approve(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader("X-User-Id") Long reviewerId,
            @PathVariable Long documentId) {
        requireAdmin(role);
        return ResponseEntity.ok(kycService.approve(documentId, reviewerId, role));
    }

    @PutMapping("/{documentId}/reject")
    @Operation(summary = "Reject a KYC document")
    public ResponseEntity<KycDocument> reject(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader("X-User-Id") Long reviewerId,
            @PathVariable Long documentId,
            @RequestBody Map<String, String> body) {
        requireAdmin(role);
        String reason = body.getOrDefault("reason", "Not specified");
        return ResponseEntity.ok(kycService.reject(documentId, reviewerId, role, reason));
    }

    private void requireAdmin(String role) {
        if (role == null || !ADMIN_ROLES.contains(role)) {
            throw new AccessDeniedException("Admin role required");
        }
    }

    @ResponseStatus(HttpStatus.FORBIDDEN)
    public static class AccessDeniedException extends RuntimeException {
        public AccessDeniedException(String message) {
            super(message);
        }
    }
}
