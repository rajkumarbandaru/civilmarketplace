package com.civileng.marketplace.review.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "rating_summaries", indexes = {
        @Index(name = "idx_summary_user", columnList = "user_id", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RatingSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "average_rating", nullable = false, columnDefinition = "DECIMAL(3,2)")
    @Builder.Default
    private BigDecimal averageRating = BigDecimal.ZERO;

    @Column(name = "total_reviews", nullable = false)
    @Builder.Default
    private Integer totalReviews = 0;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
