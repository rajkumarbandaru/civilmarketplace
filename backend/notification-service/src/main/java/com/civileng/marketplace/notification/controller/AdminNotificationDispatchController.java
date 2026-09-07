package com.civileng.marketplace.notification.controller;

import com.civileng.marketplace.web.common.StaffRoles;
import com.civileng.marketplace.notification.dto.NotificationRequest;
import com.civileng.marketplace.web.common.AccessDeniedException;
import com.civileng.marketplace.notification.service.NotificationDispatcher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Lets staff send a notification over any channel directly, without waiting for a platform
 * event to fire it. Its main use is verifying that SMTP, Twilio SMS and Twilio WhatsApp are
 * wired correctly in an environment — a "did the credentials land?" check that otherwise
 * requires provoking a real booking or payment.
 */
@RestController
@RequestMapping("/api/v1/admin/notifications")
@RequiredArgsConstructor
@Tag(name = "Admin Notifications", description = "Direct multi-channel notification dispatch")
public class AdminNotificationDispatchController {


    private final NotificationDispatcher dispatcher;

    @PostMapping("/dispatch")
    @Operation(summary = "Send a notification over the given channels (IN_APP, EMAIL, SMS, WHATSAPP)")
    public ResponseEntity<Map<String, Object>> dispatch(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody NotificationRequest request) {
        requireAdmin(role);
        dispatcher.dispatch(request);

        // Delivery is asynchronous and best-effort per channel, so this only confirms the
        // request was accepted — check the service log for per-channel outcomes.
        return ResponseEntity.accepted().body(Map.of(
                "accepted", true,
                "channels", request.channels()));
    }

    private void requireAdmin(String role) {
        if (!StaffRoles.isStaff(role)) {
            throw new AccessDeniedException("Admin role required");
        }
    }
}
