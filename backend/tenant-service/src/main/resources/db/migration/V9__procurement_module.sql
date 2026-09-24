-- Procurement (B2B) becomes sellable: Professional and Enterprise include it, Starter can add it.
--
-- Plans are immutable once subscribed, so this is a new version of each rather than an edit.
-- Subscribers on version 1 move to version 2: it only adds a feature, so nothing anyone runs can
-- stop, and leaving them behind would make the module unreachable for every existing tenant.
INSERT INTO plans (plan_key, version, name, features, limits)
SELECT plan_key, 2, name, CONCAT(features, ',procurement'), limits
  FROM plans WHERE plan_key IN ('professional', 'enterprise') AND version = 1;

UPDATE tenant_subscriptions SET plan_version = 2
 WHERE plan_key IN ('professional', 'enterprise') AND plan_version = 1;

-- The operator tenant is where the platform is tried out locally; it runs every module it has.
UPDATE tenants SET enabled_modules = CONCAT(enabled_modules, ',procurement')
 WHERE tenant_key = 'platform' AND FIND_IN_SET('procurement', enabled_modules) = 0;
