-- ============================================================================
-- Initialize all service databases
-- Each microservice has its own database
-- ============================================================================

CREATE DATABASE IF NOT EXISTS civil_engineer_auth
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS civil_engineer_users
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS civil_engineer_bookings
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS civil_engineer_payments
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS civil_engineer_notifications
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS civil_engineer_reviews
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS civil_engineer_audit
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS civil_engineer_projects
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS civil_engineer_messaging
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS admin_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS civil_engineer_support
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- Create application user with required privileges
CREATE USER IF NOT EXISTS 'civil_user'@'%' IDENTIFIED BY 'civil_pass';
GRANT ALL PRIVILEGES ON civil_engineer_auth.* TO 'civil_user'@'%';
GRANT ALL PRIVILEGES ON civil_engineer_users.* TO 'civil_user'@'%';
GRANT ALL PRIVILEGES ON civil_engineer_bookings.* TO 'civil_user'@'%';
GRANT ALL PRIVILEGES ON civil_engineer_payments.* TO 'civil_user'@'%';
GRANT ALL PRIVILEGES ON civil_engineer_notifications.* TO 'civil_user'@'%';
GRANT ALL PRIVILEGES ON civil_engineer_reviews.* TO 'civil_user'@'%';
GRANT ALL PRIVILEGES ON civil_engineer_audit.* TO 'civil_user'@'%';
GRANT ALL PRIVILEGES ON civil_engineer_projects.* TO 'civil_user'@'%';
GRANT ALL PRIVILEGES ON civil_engineer_messaging.* TO 'civil_user'@'%';
GRANT ALL PRIVILEGES ON admin_db.* TO 'civil_user'@'%';
GRANT ALL PRIVILEGES ON civil_engineer_support.* TO 'civil_user'@'%';
FLUSH PRIVILEGES;
