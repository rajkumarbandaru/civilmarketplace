-- ============================================================================
-- RK Technologies (the platform) and CivEngMarket (a tenant on it) become two tenants.
--
--   RK TECHNOLOGIES  ── tenant `platform`: the platform console and factory, nothing else
--        │
--        ├── CIVENGMARKET  ── tenant `civengmarket`: the construction marketplace
--        ├── B2B tenant    ── created through the factory
--        └── Retail tenant ── created through the factory
--
-- Until now `platform` was both at once: the operator tenant that creates every other tenant, and
-- the civil-engineering marketplace itself (vertical CIVIL_MARKETPLACE, every marketplace module
-- switched on). So "super admin" meant two unrelated jobs, and the only thing telling them apart
-- was which host the person had signed in on.
--
-- The operator keeps the key `platform`: it is every tenanted service's bootstrap schema and the
-- key the gateway, the integration resolver and the payment config all name, and renaming it buys
-- nothing but risk. Its display name becomes RK Technologies.
--
-- CivEngMarket starts ACTIVE with an empty schema in every service — tenanted services create and
-- migrate the schema of every ACTIVE tenant at boot (TenantSchemaBootstrap), and auth-service's dev
-- seeder signs its accounts in. Marketplace data in the old `platform` schemas is not copied: dev
-- data is reseeded.
-- ============================================================================

-- CivEngMarket: the marketplace the platform tenant used to host, with the look it had there.
INSERT INTO tenants (tenant_key, name, subdomain, status, contact_email, owner_name, owner_email,
                     plan, vertical, enabled_modules, landing_path, created_by,
                     logo_url, primary_color, accent_color, surface_color, sidebar_color, color_mode,
                     border_radius, font_family, brand_name, preset_key, ui_style, button_style,
                     layout_style, density, site_layout)
SELECT 'civengmarket', 'CivEngMarket', 'civengmarket', 'ACTIVE', contact_email, 'CivEngMarket Owner',
       contact_email, 'enterprise', 'CIVIL_MARKETPLACE',
       'auth,users,payments,notifications,support,admin,audit,messaging,bookings,projects,reviews,search,procurement',
       landing_path, 'migration-V13',
       logo_url, primary_color, accent_color, surface_color, sidebar_color, color_mode,
       border_radius, font_family, brand_name, preset_key, ui_style, button_style,
       layout_style, density, site_layout
  FROM tenants
 WHERE tenant_key = 'platform'
   AND NOT EXISTS (SELECT 1 FROM tenants WHERE tenant_key = 'civengmarket');

INSERT INTO tenant_status_history (tenant_key, from_status, to_status, actor, reason)
SELECT 'civengmarket', NULL, 'ACTIVE', 'migration-V13', 'Split out of the platform operator tenant'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM tenant_status_history WHERE tenant_key = 'civengmarket');

-- Every plan feature the marketplace ran on the operator tenant, so nothing disappears.
INSERT INTO tenant_subscriptions (tenant_key, plan_key, plan_version, status, updated_by)
SELECT 'civengmarket', p.plan_key, p.version, 'ACTIVE', 'migration-V13'
  FROM plans p
 WHERE p.plan_key = 'enterprise'
   AND p.version = (SELECT MAX(version) FROM plans WHERE plan_key = 'enterprise')
   AND NOT EXISTS (SELECT 1 FROM tenant_subscriptions WHERE tenant_key = 'civengmarket');

-- The menu decisions made for the marketplace belong to the marketplace.
INSERT IGNORE INTO tenant_menu_override (tenant_key, item_key, visible, label_override, sort_order)
SELECT 'civengmarket', item_key, visible, label_override, sort_order
  FROM tenant_menu_override
 WHERE tenant_key = 'platform';

-- A customer tenant, so mail and AI start on the platform's shared account like any tenant the
-- factory creates (TenantIntegrationService.seedDefaults). Payments, SMS and WhatsApp must be
-- CivEngMarket's own accounts, connected from the console.
INSERT IGNORE INTO tenant_integrations (tenant_key, capability, mode, enabled, updated_by)
VALUES ('civengmarket', 'EMAIL', 'PLATFORM_SHARED', TRUE, 'migration-V13'),
       ('civengmarket', 'AI',    'PLATFORM_SHARED', TRUE, 'migration-V13');

-- RK Technologies: the console only. Marketplace modules come off, so the gateway 404s their
-- routes on the platform host and the menu drops their items there.
UPDATE tenants
   SET name = 'RK Technologies',
       enabled_modules = 'auth,users,payments,notifications,support,admin,audit,messaging,tenantadmin',
       landing_path = NULL
 WHERE tenant_key = 'platform';
