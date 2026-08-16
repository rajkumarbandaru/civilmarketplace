package com.civileng.marketplace.notification.dto;

import com.civileng.marketplace.notification.model.EmailTemplate;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class EmailTemplateDto {

    private EmailTemplateDto() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TemplateResponse {
        private Long id;
        private String templateKey;
        private String name;
        private String description;
        private String subject;
        private String htmlBody;
        /** Placeholder name to example value, parsed out of the stored JSON. */
        private Map<String, Object> sampleVariables;
        /** Every {@code ${...}} found in subject + body, so the editor can list what is available. */
        private List<String> placeholders;
        private Boolean active;
        /** Built-ins are sent by key from the code, so they can be edited but not deleted. */
        private Boolean systemOwned;
        /** True when this row is currently overriding a shipped classpath template. */
        private Boolean overridingDefault;
        private Long updatedBy;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static TemplateResponse from(EmailTemplate t,
                                            Map<String, Object> samples,
                                            List<String> placeholders,
                                            boolean overridingDefault) {
            return TemplateResponse.builder()
                    .id(t.getId())
                    .templateKey(t.getTemplateKey())
                    .name(t.getName())
                    .description(t.getDescription())
                    .subject(t.getSubject())
                    .htmlBody(t.getHtmlBody())
                    .sampleVariables(samples)
                    .placeholders(placeholders)
                    .active(t.getActive())
                    .systemOwned(t.getSystemOwned())
                    .overridingDefault(overridingDefault)
                    .updatedBy(t.getUpdatedBy())
                    .createdAt(t.getCreatedAt())
                    .updatedAt(t.getUpdatedAt())
                    .build();
        }
    }

    /**
     * Create carries the key; update does not, because renaming a key would silently orphan the
     * built-in that {@code EmailService} sends by that name.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateTemplateRequest {
        @NotBlank
        @Size(max = 120)
        @Pattern(regexp = "[a-z0-9-]+",
                message = "Key may contain lowercase letters, digits and hyphens only")
        private String templateKey;

        @NotBlank
        @Size(max = 160)
        private String name;

        @Size(max = 500)
        private String description;

        @NotBlank
        @Size(max = 300)
        private String subject;

        @NotBlank
        private String htmlBody;

        private Map<String, Object> sampleVariables;

        private Boolean active;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateTemplateRequest {
        @NotBlank
        @Size(max = 160)
        private String name;

        @Size(max = 500)
        private String description;

        @NotBlank
        @Size(max = 300)
        private String subject;

        @NotBlank
        private String htmlBody;

        private Map<String, Object> sampleVariables;

        private Boolean active;
    }

    /**
     * Preview renders whatever the editor currently holds, saved or not — otherwise an admin would
     * have to save a broken template to find out that it is broken.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PreviewRequest {
        private String subject;
        private String htmlBody;
        private Map<String, Object> variables;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PreviewResponse {
        private String subject;
        private String html;
        /** Populated instead of {@code html} when the template does not compile. */
        private String error;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestSendRequest {
        @NotBlank
        @Email
        private String recipient;
        private Map<String, Object> variables;
    }
}
