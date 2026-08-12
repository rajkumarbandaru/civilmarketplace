package com.civileng.marketplace.support.controller;

import com.civileng.marketplace.support.dto.CreateTicketRequest;
import com.civileng.marketplace.support.dto.ReplyRequest;
import com.civileng.marketplace.support.dto.StatusChangeRequest;
import com.civileng.marketplace.support.model.SupportTicket;
import com.civileng.marketplace.support.model.TicketMessage;
import com.civileng.marketplace.support.service.SupportService;
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

@RestController
@RequestMapping("/api/v1/support/tickets")
@RequiredArgsConstructor
@Tag(name = "Support", description = "Support / Helpdesk ticketing (SRS OPS-02)")
public class SupportController {

    private final SupportService supportService;

    @PostMapping
    @Operation(summary = "Open a ticket")
    public ResponseEntity<SupportTicket> create(
            @RequestHeader("X-User-Id") Long reporterId,
            @Valid @RequestBody CreateTicketRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(supportService.createTicket(reporterId, request));
    }

    @GetMapping
    @Operation(summary = "List the caller's tickets")
    public ResponseEntity<Page<SupportTicket>> listMine(
            @RequestHeader("X-User-Id") Long reporterId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                supportService.listMine(reporterId, status, PageRequest.of(page, size)));
    }

    /** Two segments, so it never collides with {@code /{ticketId}}. */
    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "service", "support-service",
                "status", "UP",
                "timestamp", System.currentTimeMillis()
        ));
    }

    @GetMapping("/{ticketId}")
    @Operation(summary = "Get a ticket")
    public ResponseEntity<SupportTicket> get(
            @RequestHeader("X-User-Id") Long actorId,
            @RequestHeader(value = "X-User-Role", required = false) String actorRole,
            @PathVariable Long ticketId) {
        return ResponseEntity.ok(supportService.getTicket(ticketId, actorId, actorRole));
    }

    @PatchMapping("/{ticketId}/status")
    @Operation(summary = "Transition a ticket's status (assignee or admin only)")
    public ResponseEntity<SupportTicket> changeStatus(
            @RequestHeader("X-User-Id") Long actorId,
            @RequestHeader(value = "X-User-Role", required = false) String actorRole,
            @PathVariable Long ticketId,
            @Valid @RequestBody StatusChangeRequest request) {
        return ResponseEntity.ok(
                supportService.changeStatus(ticketId, actorId, actorRole, request));
    }

    // ------------------------------------------------------------------ reply thread

    @PostMapping("/{ticketId}/messages")
    @Operation(summary = "Reply on a ticket")
    public ResponseEntity<TicketMessage> reply(
            @RequestHeader("X-User-Id") Long actorId,
            @RequestHeader(value = "X-User-Role", required = false) String actorRole,
            @PathVariable Long ticketId,
            @Valid @RequestBody ReplyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(supportService.addReply(ticketId, actorId, actorRole, request));
    }

    @GetMapping("/{ticketId}/messages")
    @Operation(summary = "List a ticket's replies")
    public ResponseEntity<List<TicketMessage>> listReplies(
            @RequestHeader("X-User-Id") Long actorId,
            @RequestHeader(value = "X-User-Role", required = false) String actorRole,
            @PathVariable Long ticketId) {
        return ResponseEntity.ok(supportService.listReplies(ticketId, actorId, actorRole));
    }
}
