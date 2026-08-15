package com.civileng.marketplace.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * One notification to fan out across channels.
 *
 * <p>Contact details travel with the request rather than being looked up: the Kafka events
 * that drive most notifications already carry the recipient's email and phone, and resolving
 * them again would put a synchronous auth-service call in the path of every event.
 */
public record NotificationRequest(
        Long userId,
        @NotBlank String type,
        @NotBlank String title,
        @NotBlank String message,
        String email,
        String phone,
        @NotEmpty List<String> channels,
        String referenceType,
        Long referenceId,
        String data
) {

    public static final List<String> DEFAULT_CHANNELS = List.of("IN_APP");

    public NotificationRequest {
        if (channels == null || channels.isEmpty()) {
            channels = DEFAULT_CHANNELS;
        }
    }

    /** In-app only — the common case for events with no external contact details. */
    public static NotificationRequest inApp(Long userId, String type, String title, String message,
                                            String referenceType, Long referenceId, String data) {
        return new NotificationRequest(userId, type, title, message, null, null,
                DEFAULT_CHANNELS, referenceType, referenceId, data);
    }
}
