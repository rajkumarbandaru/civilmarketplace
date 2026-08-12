-- ============================================================================
-- User Service - KYC Documents
-- ============================================================================

CREATE TABLE kyc_documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    document_type VARCHAR(30) NOT NULL,
    document_number VARCHAR(100),
    document_url VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    rejection_reason VARCHAR(500),
    reviewed_by BIGINT,
    reviewed_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_kyc_user (user_id),
    INDEX idx_kyc_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
