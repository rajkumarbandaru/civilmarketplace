-- ============================================================================
-- Booking Service - Initial Schema
-- Database: civil_engineer_bookings
-- ============================================================================

-- Service categories table
CREATE TABLE service_categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(1000),
    icon VARCHAR(500),
    image VARCHAR(500),
    parent_id BIGINT,
    sort_order INT DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES service_categories(id),
    INDEX idx_category_slug (slug),
    INDEX idx_category_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Bookings table
CREATE TABLE bookings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_code VARCHAR(20) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL,
    worker_id BIGINT,
    service_category VARCHAR(100),
    service_name VARCHAR(255),
    booking_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    description VARCHAR(2000),
    address_id BIGINT,
    location_lat DECIMAL(10,8),
    location_lng DECIMAL(11,8),
    address_line VARCHAR(500),
    city VARCHAR(100),
    scheduled_date TIMESTAMP NULL,
    estimated_duration_minutes INT,
    estimated_cost DECIMAL(12,2),
    final_cost DECIMAL(12,2),
    platform_fee DECIMAL(12,2),
    gst_amount DECIMAL(12,2),
    total_amount DECIMAL(12,2),
    payment_status VARCHAR(30) DEFAULT 'PENDING',
    cancellation_reason VARCHAR(500),
    cancelled_by BIGINT,
    cancelled_at TIMESTAMP NULL,
    started_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    is_emergency BOOLEAN NOT NULL DEFAULT FALSE,
    is_recurring BOOLEAN NOT NULL DEFAULT FALSE,
    recurring_frequency VARCHAR(20),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_booking_customer (customer_id),
    INDEX idx_booking_worker (worker_id),
    INDEX idx_booking_status (status),
    INDEX idx_booking_scheduled (scheduled_date),
    INDEX idx_booking_city (city)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Quotations table
CREATE TABLE quotations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    worker_id BIGINT NOT NULL,
    estimated_amount DECIMAL(12,2) NOT NULL,
    platform_fee DECIMAL(12,2),
    gst_amount DECIMAL(12,2),
    total_amount DECIMAL(12,2) NOT NULL,
    description VARCHAR(2000),
    validity_days INT DEFAULT 7,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    notes VARCHAR(1000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_quotation_booking FOREIGN KEY (booking_id) REFERENCES bookings(id),
    INDEX idx_quote_booking (booking_id),
    INDEX idx_quote_worker (worker_id),
    INDEX idx_quote_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Booking milestones table
CREATE TABLE booking_milestones (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    milestone_name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    amount DECIMAL(12,2),
    status VARCHAR(30) DEFAULT 'PENDING',
    due_date DATE,
    completed_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_milestone_booking FOREIGN KEY (booking_id) REFERENCES bookings(id),
    INDEX idx_milestone_booking (booking_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- Seed Categories
-- ============================================================================
INSERT INTO service_categories (name, slug, description, sort_order, is_active) VALUES
('House Planning', 'house-planning', 'Complete house design and planning services', 1, TRUE),
('Commercial Planning', 'commercial-planning', 'Commercial building design services', 2, TRUE),
('Structural Engineering', 'structural-engineering', 'Structural analysis and design', 3, TRUE),
('Survey Services', 'survey-services', 'Land and construction survey services', 4, TRUE),
('Interior Design', 'interior-design', 'Interior design and decoration', 5, TRUE),
('Construction Services', 'construction-services', 'General construction services', 6, TRUE),
('Renovation', 'renovation', 'Building renovation and remodeling', 7, TRUE),
('Architecture', 'architecture', 'Architectural design services', 8, TRUE),
('Plumbing', 'plumbing', 'Plumbing installation and repair', 9, TRUE),
('Electrical', 'electrical', 'Electrical installation and repair', 10, TRUE);
