package com.civileng.marketplace.notification.dto;

import com.civileng.marketplace.notification.model.EmailLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

public final class EmailLogDto {

    private EmailLogDto() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LogResponse {
        private Long id;
        private String templateKey;
        /** The template's display name when one is registered, else the raw key. */
        private String templateName;
        /** EMAIL | SMS | WHATSAPP | IN_APP */
        private String channel;
        private String recipient;
        private String subject;
        /**
         * What was actually sent. Only populated when a single row is fetched — the list omits it
         * so a page of results stays small. Null on rows sent before bodies were captured.
         */
        private String body;
        private String status;
        private String provider;
        private String providerMessageId;
        private String errorMessage;
        private Long triggeredBy;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static LogResponse from(EmailLog l, String templateName, boolean includeBody) {
            return LogResponse.builder()
                    .id(l.getId())
                    .templateKey(l.getTemplateKey())
                    .templateName(templateName)
                    .channel(l.getChannel() == null ? null : l.getChannel().name())
                    .recipient(l.getRecipient())
                    .subject(l.getSubject())
                    .body(includeBody ? l.getBody() : null)
                    .status(l.getStatus() == null ? null : l.getStatus().name())
                    .provider(l.getProvider())
                    .providerMessageId(l.getProviderMessageId())
                    .errorMessage(l.getErrorMessage())
                    .triggeredBy(l.getTriggeredBy())
                    .createdAt(l.getCreatedAt())
                    .updatedAt(l.getUpdatedAt())
                    .build();
        }
    }

    /** Counts per status and per channel, for the tiles above the list. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LogSummary {
        private long total;
        private Map<String, Long> byStatus;
        private Map<String, Long> byChannel;
    }
}
