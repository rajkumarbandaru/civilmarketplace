package com.civileng.marketplace.notification.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "announcements", indexes = {
        @Index(name = "idx_announcement_created", columnList = "created_at"),
        @Index(name = "idx_announcement_due", columnList = "status, scheduled_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "body", nullable = false, length = 4000)
    private String body;

    /** Comma-separated role names, or '*' for every ACTIVE user. Never blank. */
    @Column(name = "target_roles", nullable = false, length = 500)
    private String targetRoles;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    /** Only meaningful once {@link #status} is SENT; 0 while an announcement is still waiting. */
    @Column(name = "recipient_count", nullable = false)
    @Builder.Default
    private Integer recipientCount = 0;

    @Enumerated(EnumType.STRING)
    // columnDefinition is explicit for the same reason it is on EmailLog.status: Hibernate 6 maps
    // a STRING enum to MySQL's native ENUM type, which the VARCHAR(20) the migration creates then
    // fails validation against at startup.
    @Column(name = "status", nullable = false, length = 20, columnDefinition = "varchar(20)")
    @Builder.Default
    private AnnouncementStatus status = AnnouncementStatus.SENT;

    /**
     * When this should go out, or null if it went out on creation.
     *
     * An instant rather than a {@code LocalDateTime} like {@link #createdAt}: the containers run
     * on UTC and the operators do not, so a wall-clock time here would fire hours off. See
     * V6__announcement_schedule.sql.
     */
    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    /** When the fan-out actually finished. Null until then. */
    @Column(name = "sent_at")
    private Instant sentAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
