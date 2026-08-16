package com.civileng.marketplace.notification.controller;

import com.civileng.marketplace.notification.service.EmailLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Brevo's delivery callbacks — the only source of DELIVERED and UNDELIVERED in the log.
 *
 * <p>Unauthenticated by necessity: Brevo signs nothing and sends no bearer token, so the endpoint
 * is guarded instead by a shared secret in the URL ({@code ?token=}), configured as
 * {@code app.email.webhook-token} and pasted into the Brevo dashboard's webhook URL. With no token
 * configured the endpoint refuses everything rather than defaulting open — an unguarded endpoint
 * here lets anyone rewrite delivery history.
 *
 * <p>Always answers 200. A webhook that returns an error gets retried, and a retry storm over a
 * message id we do not recognise costs more than the event is worth.
 */
@RestController
@RequestMapping("/api/v1/notifications/webhooks")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Email Webhooks", description = "Delivery callbacks from the email provider")
public class EmailWebhookController {

    private final EmailLogService emailLogService;

    @Value("${app.email.webhook-token:}")
    private String webhookToken;

    @PostMapping("/brevo")
    @Operation(summary = "Brevo delivery event callback")
    public ResponseEntity<Map<String, Object>> brevo(
            @RequestParam(required = false) String token,
            @RequestBody(required = false) Object payload) {

        if (webhookToken == null || webhookToken.isBlank() || !webhookToken.equals(token)) {
            log.warn("[EmailWebhook] rejected a Brevo callback with a bad or missing token");
            return ResponseEntity.ok(Map.of("success", false, "message", "Invalid token"));
        }

        // Brevo posts a single event object, but its batch mode posts an array of them.
        List<?> events = payload instanceof List<?> list ? list : List.of(payload);
        int applied = 0;
        for (Object event : events) {
            if (event instanceof Map<?, ?> map) {
                boolean matched = emailLogService.applyProviderEvent(
                        str(map.get("message-id")) != null ? str(map.get("message-id")) : str(map.get("messageId")),
                        str(map.get("event")),
                        str(map.get("reason")));
                if (matched) {
                    applied++;
                }
            }
        }
        return ResponseEntity.ok(Map.of("success", true, "applied", applied));
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
