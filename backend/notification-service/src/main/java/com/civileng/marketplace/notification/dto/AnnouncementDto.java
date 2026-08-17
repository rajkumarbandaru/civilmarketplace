package com.civileng.marketplace.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.time.Instant;
import java.util.List;

public class AnnouncementDto {

    @Data
    public static class CreateAnnouncementRequest {

        @NotBlank(message = "Title is required")
        private String title;

        @NotBlank(message = "Body is required")
        private String body;

        /**
         * Role names to target, or a single-element list containing "*" for every ACTIVE user.
         * Matches the "comma-separated roles, or '*'" convention admin-service's ui_menu_items
         * catalogue already uses, so this list is stored joined with commas.
         */
        @NotEmpty(message = "At least one target role (or \"*\" for everyone) is required")
        private List<String> targetRoles;

        /**
         * When to send, as an ISO-8601 instant with offset ({@code 2026-08-18T02:00:00+05:30}).
         * Null — or any time already past — means send now.
         *
         * Deliberately not annotated {@code @Future}: the client's clock is not the server's, and
         * a browser a few seconds fast would otherwise get a validation error for asking to send
         * immediately. A time in the past is treated as "now", which is what the caller meant.
         */
        private Instant scheduledAt;
    }
}
