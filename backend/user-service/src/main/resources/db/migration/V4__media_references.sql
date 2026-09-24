-- ============================================================================
-- Files are now uploaded to media-service and referenced by id.
--
-- KYC: new submissions carry media_id and no URL. A private document's link is signed and
-- short-lived, so it is fetched fresh on every view rather than stored. document_url stays for
-- rows submitted before this change.
-- Portfolio: image_url keeps the permanent public URL for display; media_id records which upload
-- it came from.
-- ============================================================================

ALTER TABLE kyc_documents
    ADD COLUMN media_id VARCHAR(36) NULL,
    MODIFY document_url VARCHAR(500) NULL;

ALTER TABLE worker_portfolios
    ADD COLUMN media_id VARCHAR(36) NULL;
