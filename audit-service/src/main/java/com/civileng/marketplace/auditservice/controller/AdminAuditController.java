package com.civileng.marketplace.auditservice.controller;

import com.civileng.marketplace.auditservice.exception.AccessDeniedException;
import com.civileng.marketplace.auditservice.model.AccessAnomalyAlert;
import com.civileng.marketplace.auditservice.model.AuditEvent;
import com.civileng.marketplace.auditservice.model.ErasureRequest;
import com.civileng.marketplace.auditservice.repository.AccessAnomalyAlertRepository;
import com.civileng.marketplace.auditservice.service.AuditQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin/audit")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Audit", description = "Audit log query, export and integrity APIs")
public class AdminAuditController {

    private static final Set<String> ADMIN_ROLES =
            Set.of("SUPER_ADMIN", "ADMIN", "SUB_ADMIN", "REGIONAL_ADMIN");

    private final AuditQueryService auditQueryService;
    private final AccessAnomalyAlertRepository anomalyRepository;

    @GetMapping("/events")
    @Operation(summary = "Search audit events")
    public ResponseEntity<Map<String, Object>> events(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) Long subjectUserId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAdmin(role);
        Page<AuditEvent> result = auditQueryService.search(actorId, subjectUserId, entityType,
                action == null ? null : action.toUpperCase(),
                parse(from), parse(to), PageRequest.of(page, Math.min(size, 200)));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", result.getContent());
        body.put("page", page);
        body.put("size", size);
        body.put("totalElements", result.getTotalElements());
        body.put("totalPages", result.getTotalPages());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/export")
    @Operation(summary = "Export all audit records for one data subject (right to access)")
    public ResponseEntity<Map<String, Object>> export(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam Long userId) {
        requireAdmin(role);
        return ResponseEntity.ok(auditQueryService.exportForUser(userId));
    }

    @GetMapping("/anomalies")
    @Operation(summary = "List unacknowledged access anomalies")
    public ResponseEntity<Map<String, Object>> anomalies(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAdmin(role);
        Page<AccessAnomalyAlert> result = anomalyRepository
                .findByAcknowledgedFalseOrderByCreatedAtDesc(PageRequest.of(page, size));
        return ResponseEntity.ok(Map.of(
                "success", true, "data", result.getContent(),
                "totalElements", result.getTotalElements()));
    }

    @GetMapping("/integrity")
    @Operation(summary = "Verify the audit log's hash chain")
    public ResponseEntity<Map<String, Object>> integrity(
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireAdmin(role);
        return ResponseEntity.ok(auditQueryService.verifyIntegrity());
    }

    @PutMapping("/erasure-requests/{id}")
    @Operation(summary = "Update the status of an erasure request")
    public ResponseEntity<ErasureRequest> updateErasure(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader("X-User-Id") Long handledBy,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        requireAdmin(role);
        return ResponseEntity.ok(auditQueryService.updateErasureStatus(
                id, body.getOrDefault("status", "COMPLETED"), handledBy, body.get("note")));
    }

    private void requireAdmin(String role) {
        if (role == null || !ADMIN_ROLES.contains(role)) {
            throw new AccessDeniedException("Admin role required");
        }
    }

    private static Instant parse(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid timestamp (expected ISO-8601): " + value);
        }
    }
}
