package com.civileng.marketplace.auditservice.controller;

import com.civileng.marketplace.auditservice.model.ErasureRequest;
import com.civileng.marketplace.auditservice.service.AuditQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/privacy")
@RequiredArgsConstructor
@Tag(name = "Privacy", description = "Data-subject rights APIs")
public class PrivacyController {

    private final AuditQueryService auditQueryService;

    @PostMapping("/erasure-requests")
    @Operation(summary = "Raise a right-to-erasure request for the calling user")
    public ResponseEntity<ErasureRequest> requestErasure(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(auditQueryService.requestErasure(userId, userId, reason));
    }

    @GetMapping("/erasure-requests")
    @Operation(summary = "List the calling user's erasure requests")
    public ResponseEntity<List<ErasureRequest>> myErasureRequests(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(auditQueryService.erasureRequestsFor(userId));
    }

    @GetMapping("/my-audit-report")
    @Operation(summary = "Right-to-access export of the calling user's own audit trail")
    public ResponseEntity<Map<String, Object>> myReport(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(auditQueryService.exportForUser(userId));
    }

    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "success", true, "service", "audit-service",
                "status", "UP", "timestamp", System.currentTimeMillis()));
    }
}
