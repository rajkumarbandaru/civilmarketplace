-- Crypto-shredding (architecture 06 §9): when an archived tenant's encryption keys are destroyed in
-- the secrets broker, everything sealed under them — its provider credentials, in the database and
-- in every backup — becomes unreadable. Recorded here, since the ciphertext rows stay behind.
ALTER TABLE tenants ADD COLUMN keys_destroyed_at TIMESTAMP(3) NULL AFTER placement_epoch;
