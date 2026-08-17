-- ============================================================================
-- Let an announcement be written now and delivered later.
--
-- Until now publishing was the same act as sending: the POST fanned out inside its own
-- transaction and there was no state in which an announcement existed but had not gone out. A
-- maintenance notice for 2am therefore had to be typed at 2am. These columns add the missing
-- state so the row can sit until its time comes.
--
-- Times are stored as UTC instants, not wall-clock LocalDateTimes like created_at above. The
-- service containers set no TZ and so run on UTC while the operators are on IST, and a bare
-- "02:00" would be five and a half hours out — the one kind of error a scheduler must not make.
-- The API takes and returns an ISO-8601 instant with its offset, and the browser does the
-- conversion at each end.
--
-- Existing rows are backfilled to SENT with sent_at = created_at, which is exactly what they
-- were: sent, at the moment they were created.
-- ============================================================================

ALTER TABLE announcements
    -- SCHEDULED -> SENDING -> SENT, or SCHEDULED -> CANCELLED. SENDING exists only so a
    -- fan-out in progress cannot be claimed a second time by another instance's scheduler;
    -- nothing is ever left in it deliberately.
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'SENT' AFTER recipient_count,
    -- NULL for anything sent immediately: there was no appointed time, and recording "now"
    -- would make an immediate send indistinguishable from one scheduled for the same second.
    ADD COLUMN scheduled_at DATETIME NULL AFTER status,
    ADD COLUMN sent_at DATETIME NULL AFTER scheduled_at;

UPDATE announcements SET sent_at = created_at WHERE sent_at IS NULL;

-- The scheduler's only query is "SCHEDULED rows whose time has passed", run every minute
-- forever. Leading with status keeps it off the ever-growing tail of sent announcements.
CREATE INDEX idx_announcement_due ON announcements (status, scheduled_at);
