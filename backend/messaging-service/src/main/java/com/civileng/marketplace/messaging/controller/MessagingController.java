package com.civileng.marketplace.messaging.controller;

import com.civileng.marketplace.messaging.model.Message;
import com.civileng.marketplace.messaging.model.MessageThread;
import com.civileng.marketplace.messaging.service.MessagingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@Tag(name = "Messaging", description = "Booking-scoped chat between customer and worker")
public class MessagingController {

    private final MessagingService messagingService;

    @PostMapping("/api/v1/bookings/{bookingId}/messages")
    @Operation(summary = "Send a message in a booking's conversation")
    public ResponseEntity<Message> sendMessage(
            @RequestHeader("X-User-Id") Long senderId,
            @PathVariable Long bookingId,
            @RequestBody SendMessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(messagingService.sendMessage(bookingId, senderId, request.getBody()));
    }

    @GetMapping("/api/v1/bookings/{bookingId}/messages")
    @Operation(summary = "Get a booking's conversation, most recent first; marks it read")
    public ResponseEntity<Page<Message>> getMessages(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long bookingId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return ResponseEntity.ok(messagingService.getMessages(bookingId, userId,
                PageRequest.of(page, Math.min(size, 100))));
    }

    @GetMapping("/api/v1/threads")
    @Operation(summary = "List the caller's conversations, most recent first")
    public ResponseEntity<Page<MessageThread>> getMyThreads(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(messagingService.getMyThreads(userId, PageRequest.of(page, size)));
    }

    @GetMapping("/api/v1/threads/unread-count")
    @Operation(summary = "Total unread messages across all the caller's conversations")
    public ResponseEntity<Map<String, Object>> unreadCount(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(Map.of("success", true, "unreadCount",
                messagingService.getUnreadCount(userId)));
    }

    @GetMapping("/api/v1/messaging/health")
    @Operation(summary = "Health check")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "success", true, "service", "messaging-service",
                "status", "UP", "timestamp", System.currentTimeMillis()));
    }

    @Data
    static class SendMessageRequest {
        @NotBlank
        private String body;
    }
}
