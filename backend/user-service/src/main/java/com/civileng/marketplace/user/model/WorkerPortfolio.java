package com.civileng.marketplace.user.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** A photo of past work a worker shows on their profile. The table has existed since V1. */
@Entity
@Table(name = "worker_portfolios", indexes = @Index(name = "idx_portfolio_user", columnList = "user_id"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerPortfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", length = 2000)
    private String description;

    /** Permanent public URL of the photo (PORTFOLIO uploads live in the public bucket). */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "media_id", length = 36)
    private String mediaId;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "completion_date")
    private LocalDate completionDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
