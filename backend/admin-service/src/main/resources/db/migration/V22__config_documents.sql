-- ============================================================================
-- Versioned configuration documents (architecture 04 §1, §7–§9).
--
-- The theme stops being one mutable row per scope and becomes four documents — branding, theme,
-- style, layout — each versioned on its own. A save is a RELEASE (one or more new document
-- versions, published together); the publish POINTER says which version of each document is
-- live; rollback publishes old content as a new release, so history only ever grows.
--
-- Scopes: 'TENANT' (the whole workspace — the old PLATFORM row) and 'ROLE:<role>' (a workspace
-- override, the old role rows). Content is JSON; an absent key inherits.
--
-- ui_theme_config is imported below and then no longer read. It is left in place for one release
-- so this migration can be checked against it; drop it in a later migration.
-- ============================================================================

CREATE TABLE IF NOT EXISTS config_releases (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    scope                  VARCHAR(60)  NOT NULL,
    -- SEED (shipped default) | ONBOARDING | OPERATOR (Super Admin of the platform tenant)
    -- | CONSOLE (this workspace's admin) | ROLLBACK
    source                 VARCHAR(20)  NOT NULL,
    change_note            VARCHAR(500) NULL,
    rollback_of_release_id BIGINT       NULL,
    created_by             BIGINT       NULL,
    created_at             TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_config_release_scope (scope, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS config_versions (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    release_id          BIGINT       NOT NULL,
    scope               VARCHAR(60)  NOT NULL,
    document            VARCHAR(20)  NOT NULL,
    version_no          INT          NOT NULL,
    schema_version      INT          NOT NULL DEFAULT 1,
    content             TEXT         NOT NULL,
    content_hash        CHAR(64)     NOT NULL,
    validation_report   TEXT         NULL,
    created_at          TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uq_config_version UNIQUE (scope, document, version_no),
    CONSTRAINT fk_config_version_release FOREIGN KEY (release_id) REFERENCES config_releases (id),
    INDEX idx_config_version_release (release_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS config_pointers (
    scope      VARCHAR(60)  NOT NULL,
    document   VARCHAR(20)  NOT NULL,
    version_id BIGINT       NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (scope, document),
    CONSTRAINT fk_config_pointer_version FOREIGN KEY (version_id) REFERENCES config_versions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Import: one release per existing row, four version-1 documents, pointers to them. A row saved
-- at least once (version > 1) counts as the workspace's own choice.
INSERT INTO config_releases (scope, source, change_note)
SELECT CASE WHEN scope_key = 'PLATFORM' THEN 'TENANT' ELSE CONCAT('ROLE:', scope_key) END,
       CASE WHEN version > 1 THEN 'CONSOLE' ELSE 'SEED' END,
       'Imported from the previous theme settings'
  FROM ui_theme_config;

INSERT INTO config_versions (release_id, scope, document, version_no, content, content_hash)
SELECT r.id, r.scope, d.document, 1, d.content, SHA2(d.content, 256)
  FROM ui_theme_config t
  JOIN config_releases r
    ON r.scope = CASE WHEN t.scope_key = 'PLATFORM' THEN 'TENANT' ELSE CONCAT('ROLE:', t.scope_key) END
  JOIN LATERAL (
        SELECT 'branding' AS document, JSON_OBJECT('brandName', t.brand_name, 'logoUrl', t.logo_url) AS content
        UNION ALL
        SELECT 'theme', JSON_OBJECT('mode', t.mode, 'primaryColor', t.primary_color, 'accentColor', t.accent_color,
                                    'surfaceColor', t.surface_color, 'sidebarColor', t.sidebar_color,
                                    'fontFamily', t.font_family)
        UNION ALL
        SELECT 'style', JSON_OBJECT('uiStyle', t.ui_style, 'buttonStyle', t.button_style,
                                    'density', t.density, 'borderRadius', t.border_radius)
        UNION ALL
        SELECT 'layout', JSON_OBJECT('layoutStyle', t.layout_style)
  ) d;

INSERT INTO config_pointers (scope, document, version_id)
SELECT scope, document, id FROM config_versions;
