package com.civileng.marketplace.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

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
    }
}
