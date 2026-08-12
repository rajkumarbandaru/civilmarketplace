-- ============================================================================
-- Auth Service - Initial Schema
-- ============================================================================

CREATE TABLE roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    is_system_role BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) UNIQUE,
    phone VARCHAR(20),
    password_hash VARCHAR(255),
    name VARCHAR(255) NOT NULL,
    profile_picture VARCHAR(500),
    provider VARCHAR(50),
    provider_id VARCHAR(255),
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    phone_verified BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    role_id BIGINT NOT NULL,
    two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    last_login_at TIMESTAMP NULL,
    login_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_role FOREIGN KEY (role_id) REFERENCES roles(id),
    INDEX idx_user_email (email),
    INDEX idx_user_phone (phone),
    INDEX idx_user_status (status),
    INDEX idx_user_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE refresh_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    token VARCHAR(500) NOT NULL,
    user_id BIGINT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_refresh_token (token(255)),
    INDEX idx_refresh_token_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed roles
INSERT INTO roles (name, description, is_system_role) VALUES
('SUPER_ADMIN', 'Super Administrator with full system access', TRUE),
('ADMIN', 'Administrator', TRUE),
('SUB_ADMIN', 'Sub Administrator', TRUE),
('REGIONAL_ADMIN', 'Regional Administrator', TRUE),
('CITY_MANAGER', 'City Manager', TRUE),
('CUSTOMER', 'Regular customer', TRUE),
('WORKER', 'Service worker/professional', TRUE),
('LABOUR', 'Labour worker', TRUE),
('LABOUR_CONTRACTOR', 'Labour contractor', TRUE),
('CIVIL_ENGINEER', 'Civil engineer', TRUE),
('STRUCTURAL_ENGINEER', 'Structural engineer', TRUE),
('SITE_ENGINEER', 'Site engineer', TRUE),
('ARCHITECT', 'Architect', TRUE),
('INTERIOR_DESIGNER', 'Interior designer', TRUE),
('EXTERIOR_DESIGNER', 'Exterior designer', TRUE),
('PAINTER', 'Painter', TRUE),
('PLUMBER', 'Plumber', TRUE),
('ELECTRICIAN', 'Electrician', TRUE),
('WELDER', 'Welder', TRUE),
('CARPENTER', 'Carpenter', TRUE),
('FABRICATOR', 'Fabricator', TRUE),
('SURVEYOR', 'Surveyor', TRUE),
('MATERIAL_SUPPLIER', 'Material supplier', TRUE),
('EQUIPMENT_RENTAL', 'Equipment rental provider', TRUE);
