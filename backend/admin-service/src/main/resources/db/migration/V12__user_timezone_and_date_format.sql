-- ============================================================================
-- Admin Service - member timezone and date format
--
-- Sits alongside colour mode and density on the same per-member row: these are
-- preferences about how one person reads the site, and they follow that person
-- across every workspace they belong to.
--
-- Both nullable, because null means "follow the workspace / use the browser's
-- own zone" rather than "unset". A member who never opens the screen keeps
-- their local time, which is what they had before this column existed.
-- ============================================================================

ALTER TABLE ui_user_appearance
    ADD COLUMN timezone VARCHAR(64) NULL AFTER density,
    ADD COLUMN date_format VARCHAR(20) NULL AFTER timezone;
