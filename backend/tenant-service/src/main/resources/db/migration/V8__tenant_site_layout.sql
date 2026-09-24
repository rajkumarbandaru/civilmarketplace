-- The website layout chosen at onboarding (architecture 05 §5), carried with the rest of the
-- branding into the tenant's theme documents at publish.
ALTER TABLE tenants ADD COLUMN site_layout VARCHAR(20) NULL AFTER density;
