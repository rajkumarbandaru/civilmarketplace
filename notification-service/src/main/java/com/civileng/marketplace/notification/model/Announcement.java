package com.civileng.marketplace.notification.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "announcements", indexes = {
        @Index(name = "idx_announcement_created", columnList = "created_at")
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

    @Column(name = "recipient_count", nullable = false)
    @Builder.Default
    private Integer recipientCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
