-- Branding chosen when a tenant is onboarded: its logo, colours and UI styling.
--
-- Kept on the tenant record rather than only in admin-service because it is part of what an
-- operator decides when creating the tenant, and the tenant's schema in admin-service does not
-- exist yet at that moment — provisioning is asynchronous. tenant-service stores the choice, puts
-- it on the tenant.events message, and admin-service seeds its theme from it once the schema is
-- there. Storing it here also means the operator console can show what a tenant was set up with
-- without a cross-schema read.
--
-- All nullable: branding is optional, and a tenant that skips it starts on the platform theme.
-- Colour columns are 9 chars to hold `#RRGGBBAA`, matching admin-service's ui_theme_config.

ALTER TABLE tenants
    ADD COLUMN logo_url      VARCHAR(500) NULL AFTER custom_domain,
    ADD COLUMN primary_color VARCHAR(9)   NULL AFTER logo_url,
    ADD COLUMN accent_color  VARCHAR(9)   NULL AFTER primary_color,
    ADD COLUMN sidebar_color VARCHAR(9)   NULL AFTER accent_color,
    ADD COLUMN color_mode    VARCHAR(10)  NULL AFTER sidebar_color,
    ADD COLUMN ui_style      VARCHAR(40)  NULL AFTER color_mode,
    ADD COLUMN button_style  VARCHAR(20)  NULL AFTER ui_style,
    ADD COLUMN layout_style  VARCHAR(40)  NULL AFTER button_style,
    ADD COLUMN density       VARCHAR(20)  NULL AFTER layout_style;
