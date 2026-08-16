-- The public service catalogue, moved out of the frontend and into the database.
--
-- Until now `service_categories` held the groupings and nothing held the items: the ~116 services,
-- materials, machines and vehicles the site lists were a hard-coded TypeScript array, so adding one
-- meant a code change and a redeploy, and the admin console's category screen managed groups that
-- had nothing visible under them. CatalogueSeeder fills this table from a copy of that same array
-- on first boot, so the site starts with exactly what it had before.
--
-- The category is held as its name rather than a foreign key to service_categories: the public site
-- filters by name, `bookings.service_category` already records it as text, and a rename must not
-- silently repoint historical bookings. Renames are carried across in application code.

CREATE TABLE service_offerings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    slug VARCHAR(255) NOT NULL UNIQUE,
    title VARCHAR(255) NOT NULL,
    category VARCHAR(255) NOT NULL,
    icon VARCHAR(120),
    -- Display price as shown ("₹500/hr", "Quote"): free text because the unit differs per trade,
    -- and a numeric column would force every material and machine into one unit it does not have.
    price VARCHAR(60),
    -- Optional photo, video or animation for the card; NULL means the card falls back to the icon.
    media_url VARCHAR(1000),
    media_type VARCHAR(20),
    rating DOUBLE DEFAULT 0,
    reviews INT DEFAULT 0,
    -- Comma-separated trade names people actually type ("rebar, tmt, sariya"), fed into search.
    aliases VARCHAR(1000),
    sort_order INT DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT NULL,
    INDEX idx_offering_slug (slug),
    INDEX idx_offering_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
