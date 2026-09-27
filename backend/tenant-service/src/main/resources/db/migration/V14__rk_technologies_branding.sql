-- RK Technologies' own look, so the platform console and its sign-in page are never mistaken for a
-- tenant's (CivEngMarket keeps the look the platform tenant had until now — V13 copied it).
--
-- Only fills what nobody has chosen: an operator who already branded the platform keeps theirs.
-- tenant-service republishes every tenant's branding at startup, and admin-service seeds it into the
-- operator tenant's theme unless that theme has already been edited (TenantStateSync).
UPDATE tenants
   SET brand_name    = COALESCE(brand_name, 'RK Technologies'),
       primary_color = COALESCE(primary_color, '#4338CA'),
       accent_color  = COALESCE(accent_color, '#0EA5E9'),
       sidebar_color = COALESCE(sidebar_color, '#0F172A'),
       color_mode    = COALESCE(color_mode, 'light')
 WHERE tenant_key = 'platform';
