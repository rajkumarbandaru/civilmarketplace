-- ============================================================================
-- Authenticator-app (TOTP) second factor. Required for SUPER_ADMIN; see MfaService.
--
-- two_factor_secret         AES-GCM ciphertext of the base32 TOTP secret (never stored in clear)
-- two_factor_last_step      the last 30-second step accepted, so a code cannot be replayed
-- two_factor_recovery_codes SHA-256 hashes of unused one-time recovery codes, JSON array
-- ============================================================================

ALTER TABLE users
    ADD COLUMN two_factor_secret         VARCHAR(512) NULL AFTER two_factor_enabled,
    ADD COLUMN two_factor_last_step      BIGINT       NULL AFTER two_factor_secret,
    ADD COLUMN two_factor_recovery_codes TEXT         NULL AFTER two_factor_last_step;
