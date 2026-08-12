-- ============================================================================
-- Review Service - Initial Schema (Reputation / Ratings & Reviews)
-- ============================================================================

CREATE TABLE reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    reviewer_id BIGINT NOT NULL,
    reviewee_id BIGINT NOT NULL,
    rating INT NOT NULL,
    comment VARCHAR(2000),
    category_tags VARCHAR(255),
    response_text VARCHAR(1000),
    response_at TIMESTAMP NULL,
    is_hidden BOOLEAN NOT NULL DEFAULT FALSE,
    hidden_reason VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_review_booking_reviewer (booking_id, reviewer_id),
    INDEX idx_review_reviewee (reviewee_id),
    INDEX idx_review_booking (booking_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE rating_summaries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    average_rating DECIMAL(3,2) NOT NULL DEFAULT 0.00,
    total_reviews INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_summary_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
