package com.civileng.marketplace.notification.controller;

import com.civileng.marketplace.notification.dto.EmailTemplateDto.*;
import com.civileng.marketplace.notification.exception.AccessDeniedException;
import com.civileng.marketplace.notification.model.EmailStatus;
import com.civileng.marketplace.notification.service.EmailService;
import com.civileng.marketplace.notification.service.EmailTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Super Admin's email template console: list, edit, preview and test-send the transactional mail.
 *
 * <p>Writes are Super Admin's alone. The mail here goes out over the platform's own sender to real
 * customers, so an edit is closer to changing branding than to changing a booking — the roles that
 * can run day-to-day operations deliberately do not include it.
 */
@RestController
@RequestMapping("/api/v1/admin/notifications/email-templates")
@RequiredArgsConstructor
@Tag(name = "Admin Email Templates", description = "Manage the transactional email templates")
public class AdminEmailTemplateController {

    private static final Set<String> READ_ROLES =
            Set.of("SUPER_ADMIN", "ADMIN", "SUB_ADMIN", "REGIONAL_ADMIN");
    private static final Set<String> WRITE_ROLES = Set.of("SUPER_ADMIN");

    private final EmailTemplateService templateService;
    private final EmailService emailService;

    @GetMapping
    @Operation(summary = "Every email template, built-in and custom")
    public ResponseEntity<List<TemplateResponse>> list(
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        require(role, READ_ROLES);
        return ResponseEntity.ok(templateService.list());
    }

    @GetMapping("/{key}")
    @Operation(summary = "One template, with its placeholders and sample data")
    public ResponseEntity<TemplateResponse> get(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String key) {
        require(role, READ_ROLES);
        return ResponseEntity.ok(templateService.get(key));
    }

    @PostMapping
    @Operation(summary = "Create a custom template")
    public ResponseEntity<TemplateResponse> create(
            @RequestHeader("X-User-Id") Long actorId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody CreateTemplateRequest request) {
        require(role, WRITE_ROLES);
        return ResponseEntity.ok(templateService.create(request, actorId));
    }

    @PutMapping("/{key}")
    @Operation(summary = "Update a template's subject, body, sample data or active flag")
    public ResponseEntity<TemplateResponse> update(
            @RequestHeader("X-User-Id") Long actorId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String key,
            @Valid @RequestBody UpdateTemplateRequest request) {
        require(role, WRITE_ROLES);
        return ResponseEntity.ok(templateService.update(key, request, actorId));
    }

    @DeleteMapping("/{key}")
    @Operation(summary = "Delete a custom template (built-ins reset instead)")
    public ResponseEntity<Map<String, Object>> delete(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String key) {
        require(role, WRITE_ROLES);
        templateService.delete(key);
        return ResponseEntity.ok(Map.of("success", true, "message", "Template deleted"));
    }

    @PostMapping("/{key}/reset")
    @Operation(summary = "Restore a built-in template to the version shipped with the service")
    public ResponseEntity<TemplateResponse> reset(
            @RequestHeader("X-User-Id") Long actorId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String key) {
        require(role, WRITE_ROLES);
        return ResponseEntity.ok(templateService.reset(key, actorId));
    }

    /**
     * Renders the draft in the editor against sample data. Read-only, so it takes the read roles —
     * seeing what a customer receives is not the same authority as changing it.
     */
    @PostMapping("/{key}/preview")
    @Operation(summary = "Render a template (saved or unsaved) against sample data")
    public ResponseEntity<PreviewResponse> preview(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String key,
            @RequestBody(required = false) PreviewRequest request) {
        require(role, READ_ROLES);
        PreviewRequest body = request == null ? new PreviewRequest() : request;
        return ResponseEntity.ok(templateService.preview(
                key, body.getSubject(), body.getHtmlBody(), body.getVariables()));
    }

    /** Sends the real thing to one address, and records it in the delivery log like any other. */
    @PostMapping("/{key}/test-send")
    @Operation(summary = "Send this template to a single address using its sample data")
    public ResponseEntity<Map<String, Object>> testSend(
            @RequestHeader("X-User-Id") Long actorId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String key,
            @Valid @RequestBody TestSendRequest request) {
        require(role, WRITE_ROLES);
        Map<String, Object> variables = templateService.sampleVariables(key, request.getVariables());
        EmailStatus status = emailService.sendNow(
                request.getRecipient(), key, key, variables, actorId);
        return ResponseEntity.ok(Map.of(
                "success", status != EmailStatus.FAILED,
                "status", status.name(),
                "recipient", request.getRecipient()));
    }

    private void require(String role, Set<String> allowed) {
        if (role == null || !allowed.contains(role)) {
            throw new AccessDeniedException(allowed.size() == 1
                    ? "Super Admin role required"
                    : "Admin role required");
        }
    }
}
