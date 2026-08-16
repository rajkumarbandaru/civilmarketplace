-- Editable site content: every heading, paragraph, link and image on the public pages.
--
-- Until now the landing page and footer were literals inside the React bundle, so changing a
-- headline or a footer link meant a code change and a redeploy. These tables move that copy behind
-- the same Super Admin console that already owns the theme and the menu.
--
-- The shape is deliberately generic — a section with a few text slots, holding an ordered list of
-- items with the same slots — because the alternative (a column per screen) means a migration
-- every time a section gains a field. Which slots a section uses is the renderer's business, and
-- is documented per section_key in the seed rows below.

CREATE TABLE IF NOT EXISTS site_content_sections (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    -- HOME | FOOTER | GLOBAL. Groups sections by where they render.
    page_key     VARCHAR(40)  NOT NULL,
    -- Stable identifier the renderer looks up by; admins rename the title, never this.
    section_key  VARCHAR(80)  NOT NULL,
    title        VARCHAR(300) NULL,
    subtitle     VARCHAR(600) NULL,
    body         TEXT         NULL,
    image_url    VARCHAR(500) NULL,
    link_label   VARCHAR(120) NULL,
    link_url     VARCHAR(500) NULL,
    -- Footer only: which of the five columns this group is stacked into.
    column_index INT          NOT NULL DEFAULT 0,
    sort_order   INT          NOT NULL DEFAULT 0,
    -- Hidden rather than deleted keeps a seeded section recoverable after a mistaken removal.
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    -- Seeded sections cannot be deleted: the renderer looks them up by key and a missing key
    -- falls back to the shipped copy, so deleting one is a confusing no-op rather than a removal.
    -- Admins hide them instead.
    system_owned BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   DATETIME     NULL,
    updated_at   DATETIME     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_site_content_section_key (section_key),
    KEY ix_site_content_sections_page (page_key, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS site_content_items (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    section_id BIGINT       NOT NULL,
    title      VARCHAR(300) NULL,
    subtitle   VARCHAR(600) NULL,
    body       TEXT         NULL,
    -- A Material-UI icon name, resolved client-side by DynamicIcon.
    icon       VARCHAR(60)  NULL,
    image_url  VARCHAR(500) NULL,
    -- Where clicking the item goes. An in-app path ("/services") is routed; an absolute URL opens
    -- as a normal link; empty means the item is not clickable.
    link_url   VARCHAR(500) NULL,
    -- Small leading label — the step number on How It Works, a tag anywhere else.
    badge      VARCHAR(60)  NULL,
    sort_order INT          NOT NULL DEFAULT 0,
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at DATETIME     NULL,
    updated_at DATETIME     NULL,
    PRIMARY KEY (id),
    KEY ix_site_content_items_section (section_id, sort_order),
    CONSTRAINT fk_site_content_items_section FOREIGN KEY (section_id)
        REFERENCES site_content_sections (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Uploaded images live in the database rather than on a container filesystem: the services run
-- without a shared volume, so a file written by one admin-service replica would 404 from another.
CREATE TABLE IF NOT EXISTS site_content_media (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    filename     VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    data         LONGBLOB     NOT NULL,
    uploaded_by  BIGINT       NULL,
    created_at   DATETIME     NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------- seed: the copy shipping today
--
-- Seeded with exactly what the pages render now, so turning the feature on changes nothing until
-- an admin edits something. The client keeps its own copy of these defaults for the case where the
-- content service cannot be reached.

INSERT IGNORE INTO site_content_sections
    (page_key, section_key, title, subtitle, body, link_label, link_url, column_index, sort_order, system_owned, created_at, updated_at)
VALUES
-- Hero. The word wrapped in **asterisks** in the title renders in the accent colour, which is how
-- an admin moves the highlight without a code change. `body` is the chip above the headline.
('HOME', 'home.hero',
 'Book Civil Engineering **Professionals** Instantly',
 'From architects and structural engineers to surveyors and contractors — find and book trusted civil engineering experts near you, on demand.',
 'India''s #1 Civil Engineering Platform', 'Search', '/services', 0, 10, TRUE, NOW(), NOW()),
('HOME', 'home.stats', NULL, NULL, NULL, NULL, NULL, 0, 20, TRUE, NOW(), NOW()),
('HOME', 'home.how_it_works', 'How It Works',
 'Get your civil engineering work done in three simple steps', NULL, NULL, NULL, 0, 30, TRUE, NOW(), NOW()),
('HOME', 'home.services', 'Our Services',
 'Comprehensive civil engineering services for all your construction needs', NULL, NULL, NULL, 0, 40, TRUE, NOW(), NOW()),
('HOME', 'home.cta', 'Ready to Start Your Project?',
 'Join thousands of satisfied customers who found the perfect civil engineering professional',
 NULL, NULL, NULL, 0, 50, TRUE, NOW(), NOW()),
-- Global brand: the wordmark and logo the navbar and footer share.
('GLOBAL', 'global.brand', 'CivEngMarket', NULL, NULL, NULL, '/', 0, 10, TRUE, NOW(), NOW()),
-- Footer. `column_index` packs the groups into five columns so no single column runs twice as
-- long as its neighbours; `body` on the brand block is the paragraph under the wordmark.
('FOOTER', 'footer.brand', 'CivEngMarket', NULL,
 'India''s #1 platform for booking civil engineering professionals. Connecting customers with trusted architects, engineers, surveyors, and construction experts.',
 NULL, NULL, 0, 10, TRUE, NOW(), NOW()),
('FOOTER', 'footer.design',       'Design & Planning',    NULL, NULL, NULL, NULL, 1, 10, TRUE, NOW(), NOW()),
('FOOTER', 'footer.survey',       'Survey & Engineering', NULL, NULL, NULL, NULL, 1, 20, TRUE, NOW(), NOW()),
('FOOTER', 'footer.construction', 'Construction',         NULL, NULL, NULL, NULL, 2, 10, TRUE, NOW(), NOW()),
('FOOTER', 'footer.marketplace',  'Marketplace',          NULL, NULL, NULL, NULL, 2, 20, TRUE, NOW(), NOW()),
('FOOTER', 'footer.materials',    'Materials',            NULL, NULL, NULL, NULL, 3, 10, TRUE, NOW(), NOW()),
('FOOTER', 'footer.professionals','For Professionals',    NULL, NULL, NULL, NULL, 4, 10, TRUE, NOW(), NOW()),
('FOOTER', 'footer.company',      'Company',              NULL, NULL, NULL, NULL, 5, 10, TRUE, NOW(), NOW()),
('FOOTER', 'footer.support',      'Support',              NULL, NULL, NULL, NULL, 5, 20, TRUE, NOW(), NOW()),
-- `body` is the copyright line ({year} is substituted at render time); `subtitle` is the tagline
-- on the opposite end of the same row.
('FOOTER', 'footer.legal', NULL, 'Made with ❤️ for civil engineering professionals',
 '© {year} Civil Engineering Marketplace. All rights reserved.', NULL, NULL, 0, 90, TRUE, NOW(), NOW());

-- Hero trust badges.
INSERT IGNORE INTO site_content_items (section_id, title, icon, sort_order, created_at, updated_at)
SELECT s.id, v.title, 'Security', v.sort_order, NOW(), NOW()
FROM site_content_sections s
JOIN (SELECT 'Verified Professionals' AS title, 10 AS sort_order
      UNION ALL SELECT 'Secure Payments', 20
      UNION ALL SELECT '24/7 Support', 30) v
WHERE s.section_key = 'home.hero'
  AND NOT EXISTS (SELECT 1 FROM site_content_items x WHERE x.section_id = s.id);

-- Stats: title is the number, subtitle its label.
INSERT IGNORE INTO site_content_items (section_id, title, subtitle, icon, sort_order, created_at, updated_at)
SELECT s.id, v.title, v.subtitle, v.icon, v.sort_order, NOW(), NOW()
FROM site_content_sections s
JOIN (SELECT '10,000+' AS title, 'Professionals' AS subtitle, 'People' AS icon, 10 AS sort_order
      UNION ALL SELECT '50,000+', 'Projects Completed', 'Verified', 20
      UNION ALL SELECT '4.8/5',   'Average Rating',     'Star',     30
      UNION ALL SELECT '100+',    'Cities Covered',     'Speed',    40) v
WHERE s.section_key = 'home.stats'
  AND NOT EXISTS (SELECT 1 FROM site_content_items x WHERE x.section_id = s.id);

-- How It Works: badge is the step number.
INSERT IGNORE INTO site_content_items (section_id, badge, title, body, sort_order, created_at, updated_at)
SELECT s.id, v.badge, v.title, v.body, v.sort_order, NOW(), NOW()
FROM site_content_sections s
JOIN (SELECT '01' AS badge, 'Describe Your Project' AS title,
             'Tell us what you need — from house plans to structural analysis' AS body, 10 AS sort_order
      UNION ALL SELECT '02', 'Get Matched with Experts',
             'We connect you with verified professionals in your area', 20
      UNION ALL SELECT '03', 'Book & Track',
             'Book instantly and track progress in real-time', 30) v
WHERE s.section_key = 'home.how_it_works'
  AND NOT EXISTS (SELECT 1 FROM site_content_items x WHERE x.section_id = s.id);

-- CTA buttons. The first is the filled one, the rest are outlined.
INSERT IGNORE INTO site_content_items (section_id, title, link_url, sort_order, created_at, updated_at)
SELECT s.id, v.title, v.link_url, v.sort_order, NOW(), NOW()
FROM site_content_sections s
JOIN (SELECT 'Get Started Free' AS title, '/register' AS link_url, 10 AS sort_order
      UNION ALL SELECT 'Browse Services', '/services', 20) v
WHERE s.section_key = 'home.cta'
  AND NOT EXISTS (SELECT 1 FROM site_content_items x WHERE x.section_id = s.id);

-- Footer social icons.
INSERT IGNORE INTO site_content_items (section_id, title, icon, link_url, sort_order, created_at, updated_at)
SELECT s.id, v.title, v.icon, v.link_url, v.sort_order, NOW(), NOW()
FROM site_content_sections s
JOIN (SELECT 'Facebook' AS title, 'Facebook' AS icon, 'https://facebook.com' AS link_url, 10 AS sort_order
      UNION ALL SELECT 'Twitter',  'Twitter',  'https://twitter.com',  20
      UNION ALL SELECT 'Instagram','Instagram','https://instagram.com',30
      UNION ALL SELECT 'LinkedIn', 'LinkedIn', 'https://linkedin.com', 40
      UNION ALL SELECT 'YouTube',  'YouTube',  'https://youtube.com',  50) v
WHERE s.section_key = 'footer.brand'
  AND NOT EXISTS (SELECT 1 FROM site_content_items x WHERE x.section_id = s.id);

-- Footer links. A link whose URL is left empty is rendered as plain text, which is what the
-- not-yet-built pages (Careers, Press) want.
INSERT IGNORE INTO site_content_items (section_id, title, link_url, sort_order, created_at, updated_at)
SELECT s.id, v.title, v.link_url, v.sort_order, NOW(), NOW()
FROM site_content_sections s
JOIN (SELECT 'House Planning' AS title, '/services/architecture?q=House%20Planning' AS link_url, 10 AS sort_order
      UNION ALL SELECT 'Villa Planning',      '/services/architecture?q=Villa%20Planning', 20
      UNION ALL SELECT 'Architecture Design', '/services/architecture?q=Architecture%20Design', 30
      UNION ALL SELECT 'Elevation Design',    '/services/architecture?q=Elevation%20Design', 40
      UNION ALL SELECT 'Interior Design',     '/services/design?q=Interior%20Design', 50
      UNION ALL SELECT '3D Modeling',         '/services/design?q=3D%20Modeling', 60) v
WHERE s.section_key = 'footer.design'
  AND NOT EXISTS (SELECT 1 FROM site_content_items x WHERE x.section_id = s.id);

INSERT IGNORE INTO site_content_items (section_id, title, link_url, sort_order, created_at, updated_at)
SELECT s.id, v.title, v.link_url, v.sort_order, NOW(), NOW()
FROM site_content_sections s
JOIN (SELECT 'Structural Engineering' AS title, '/services/engineering?q=Structural%20Engineering' AS link_url, 10 AS sort_order
      UNION ALL SELECT 'Earthquake Design', '/services/engineering?q=Earthquake%20Design', 20
      UNION ALL SELECT 'BIM Modeling',      '/services/engineering?q=BIM%20Modeling', 30
      UNION ALL SELECT 'Land Survey',       '/services/survey?q=Land%20Survey', 40
      UNION ALL SELECT 'Drone Survey',      '/services/survey?q=Drone%20Survey', 50
      UNION ALL SELECT 'GIS Mapping',       '/services/survey?q=GIS%20Mapping', 60) v
WHERE s.section_key = 'footer.survey'
  AND NOT EXISTS (SELECT 1 FROM site_content_items x WHERE x.section_id = s.id);

INSERT IGNORE INTO site_content_items (section_id, title, link_url, sort_order, created_at, updated_at)
SELECT s.id, v.title, v.link_url, v.sort_order, NOW(), NOW()
FROM site_content_sections s
JOIN (SELECT 'Building Construction' AS title, '/services/construction?q=Building%20Construction' AS link_url, 10 AS sort_order
      UNION ALL SELECT 'Renovation',          '/services/construction?q=Renovation', 20
      UNION ALL SELECT 'Electrical Work',     '/services/construction?q=Electrical%20Work', 30
      UNION ALL SELECT 'Plumbing Services',   '/services/construction?q=Plumbing%20Services', 40
      UNION ALL SELECT 'Contractor Services', '/services/construction?q=Contractor%20Services', 50
      UNION ALL SELECT 'Site Supervision',    '/services/construction?q=Site%20Supervision', 60
      UNION ALL SELECT 'Project Management',  '/services/construction?q=Project%20Management', 70) v
WHERE s.section_key = 'footer.construction'
  AND NOT EXISTS (SELECT 1 FROM site_content_items x WHERE x.section_id = s.id);

INSERT IGNORE INTO site_content_items (section_id, title, link_url, sort_order, created_at, updated_at)
SELECT s.id, v.title, v.link_url, v.sort_order, NOW(), NOW()
FROM site_content_sections s
JOIN (SELECT 'Material Supply' AS title, '/services/materials' AS link_url, 10 AS sort_order
      UNION ALL SELECT 'Equipment Rental',        '/services/equipment', 20
      UNION ALL SELECT 'Transport & Logistics',   '/services/transport', 30
      UNION ALL SELECT 'Skilled Labour',          '/services/labour', 40
      UNION ALL SELECT 'Daily Wage Labour',       '/services/labour', 50
      UNION ALL SELECT 'Skill & Safety Training', '/services/training', 60
      UNION ALL SELECT 'Request a Quote (RFQ)',   '/services', 70) v
WHERE s.section_key = 'footer.marketplace'
  AND NOT EXISTS (SELECT 1 FROM site_content_items x WHERE x.section_id = s.id);

INSERT IGNORE INTO site_content_items (section_id, title, link_url, sort_order, created_at, updated_at)
SELECT s.id, v.title, v.link_url, v.sort_order, NOW(), NOW()
FROM site_content_sections s
JOIN (SELECT 'Cement' AS title, '/services/materials?q=Cement' AS link_url, 10 AS sort_order
      UNION ALL SELECT 'Iron & TMT Steel Bars',        '/services/materials?q=Iron%20%26%20TMT%20Steel%20Bars', 20
      UNION ALL SELECT 'Bricks & Blocks',              '/services/materials?q=Bricks%20%26%20Blocks', 30
      UNION ALL SELECT 'Sand & Filling Material',      '/services/materials?q=Sand%20%26%20Filling%20Material', 40
      UNION ALL SELECT 'Aggregates & Crushed Stone',   '/services/materials?q=Aggregates%20%26%20Crushed%20Stone', 50
      UNION ALL SELECT 'Concrete (Ready Mix)',         '/services/materials?q=Concrete', 60
      UNION ALL SELECT 'Ceramic & Vitrified Tiles',    '/services/materials?q=Ceramic%20%26%20Vitrified%20Tiles', 70
      UNION ALL SELECT 'Paints & Coatings',            '/services/materials?q=Paints%20%26%20Coatings', 80
      UNION ALL SELECT 'Pipes & Fittings',             '/services/materials?q=Pipes%20%26%20Fittings', 90
      UNION ALL SELECT 'Sanitaryware & Bath Fittings', '/services/materials?q=Sanitaryware', 100
      UNION ALL SELECT 'All Materials (A–Z)',          '/services/materials', 110) v
WHERE s.section_key = 'footer.materials'
  AND NOT EXISTS (SELECT 1 FROM site_content_items x WHERE x.section_id = s.id);

INSERT IGNORE INTO site_content_items (section_id, title, link_url, sort_order, created_at, updated_at)
SELECT s.id, v.title, v.link_url, v.sort_order, NOW(), NOW()
FROM site_content_sections s
JOIN (SELECT 'Register as Worker' AS title, '/register?role=WORKER' AS link_url, 10 AS sort_order
      UNION ALL SELECT 'Register as Engineer',           '/register?role=ENGINEER', 20
      UNION ALL SELECT 'Register as Architect',          '/register?role=ARCHITECT', 30
      UNION ALL SELECT 'Register as Surveyor',           '/register?role=SURVEYOR', 40
      UNION ALL SELECT 'Register as Contractor',         '/register?role=CONTRACTOR', 50
      UNION ALL SELECT 'Register as Material Supplier',  '/register?role=MATERIAL_SUPPLIER', 60
      UNION ALL SELECT 'Register as Equipment Supplier', '/register?role=EQUIPMENT_SUPPLIER', 70
      UNION ALL SELECT 'Register as Transport Provider', '/register?role=TRANSPORT_PROVIDER', 80
      UNION ALL SELECT 'Partner Program',                '/register', 90
      UNION ALL SELECT 'Earnings',                       '/register', 100) v
WHERE s.section_key = 'footer.professionals'
  AND NOT EXISTS (SELECT 1 FROM site_content_items x WHERE x.section_id = s.id);

INSERT IGNORE INTO site_content_items (section_id, title, link_url, sort_order, created_at, updated_at)
SELECT s.id, v.title, v.link_url, v.sort_order, NOW(), NOW()
FROM site_content_sections s
JOIN (SELECT 'About Us' AS title, NULL AS link_url, 10 AS sort_order
      UNION ALL SELECT 'Careers',    NULL, 20
      UNION ALL SELECT 'Blog',       NULL, 30
      UNION ALL SELECT 'Press',      NULL, 40
      UNION ALL SELECT 'Contact Us', NULL, 50) v
WHERE s.section_key = 'footer.company'
  AND NOT EXISTS (SELECT 1 FROM site_content_items x WHERE x.section_id = s.id);

INSERT IGNORE INTO site_content_items (section_id, title, link_url, sort_order, created_at, updated_at)
SELECT s.id, v.title, v.link_url, v.sort_order, NOW(), NOW()
FROM site_content_sections s
JOIN (SELECT 'Help Center' AS title, '/support' AS link_url, 10 AS sort_order
      UNION ALL SELECT 'Raise a Ticket',     '/support', 20
      UNION ALL SELECT 'Safety Guidelines',  NULL, 30
      UNION ALL SELECT 'Dispute Resolution', NULL, 40
      UNION ALL SELECT 'Terms of Service',   NULL, 50
      UNION ALL SELECT 'Privacy Policy',     NULL, 60
      UNION ALL SELECT 'Refund Policy',      NULL, 70) v
WHERE s.section_key = 'footer.support'
  AND NOT EXISTS (SELECT 1 FROM site_content_items x WHERE x.section_id = s.id);

-- Navigation for the new console screen. Sits in the System group beside Theme (sort_order 190),
-- because both answer "what does the public site look like".
INSERT IGNORE INTO ui_menu_items
    (item_key, label, path, icon, section, menu_group, sort_order, exact_match, default_roles)
VALUES
('admin-content', 'Site Content', '/admin/content', 'Article', 'Platform', 'System',
 185, FALSE, 'SUPER_ADMIN');
