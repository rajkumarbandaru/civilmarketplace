package com.civileng.marketplace.notification.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * One delivery attempt on any channel: who it went to, what produced it, and how far it got.
 *
 * <p>The table is still called {@code email_log} for the reason given in V4 — it predates the
 * other channels, and {@link #channel} rather than the table name is what says what a row is.
 */
@Entity
@Table(name = "email_log", indexes = {
        @Index(name = "idx_email_log_created", columnList = "created_at"),
        @Index(name = "idx_email_log_status", columnList = "status"),
        @Index(name = "idx_email_log_channel", columnList = "channel"),
        @Index(name = "idx_email_log_recipient", columnList = "recipient"),
        @Index(name = "idx_email_log_template", columnList = "template_key"),
        @Index(name = "idx_email_log_message_id", columnList = "provider_message_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * What produced this message: an email template key, or — for SMS, WhatsApp and in-app — the
     * notification type, which is the nearest equivalent those channels have.
     */
    @Column(name = "template_key", nullable = false, length = 120)
    private String templateKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20, columnDefinition = "varchar(20)")
    @Builder.Default
    private NotificationChannel channel = NotificationChannel.EMAIL;

    /** An email address, an E.164 phone number, or {@code user:<id>} for in-app. */
    @Column(name = "recipient", nullable = false, length = 320)
    private String recipient;

    @Column(name = "subject", nullable = false, length = 300)
    private String subject;

    /**
     * Exactly what was sent: the rendered HTML for email, the message text for the other channels.
     *
     * <p>Captured at send time rather than re-rendered on demand, because the template it came from
     * may since have been edited — a log that answers "what did we tell them?" with today's wording
     * is worse than one that admits it does not know. Null on rows written before this was stored.
     */
    @Lob
    @Column(name = "body", columnDefinition = "MEDIUMTEXT")
    private String body;

    /**
     * columnDefinition is explicit because Hibernate 6 otherwise maps a STRING enum to MySQL's
     * native {@code ENUM(...)} type, and schema validation then rejects the VARCHAR the migration
     * creates. VARCHAR is the deliberate choice: adding a status stays a code change instead of
     * an ALTER TABLE.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20, columnDefinition = "varchar(20)")
    private EmailStatus status;

    @Column(name = "provider", nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_message_id", length = 200)
    private String providerMessageId;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "triggered_by")
    private Long triggeredBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
