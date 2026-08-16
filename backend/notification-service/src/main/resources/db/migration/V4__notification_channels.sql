-- ============================================================================
-- Widen the delivery log from email to every channel.
--
-- The log was built for email, but email is one of four ways this service reaches a customer, and
-- the other three were invisible: an SMS or WhatsApp send left nothing behind but a line in the
-- container's log file, so "did the OTP text arrive?" had no answer in the console at all. One row
-- per delivery attempt on any channel is the shape that answers it.
--
-- The table keeps its `email_log` name. Renaming it would touch the entity, the repository, four
-- indexes and every query for no behavioural gain; the `channel` column below is what identifies a
-- row, and the name is a historical detail rather than a claim about the contents.
-- ============================================================================

ALTER TABLE email_log
    -- EMAIL | SMS | WHATSAPP | IN_APP. Existing rows are all email, which is what the default
    -- backfills them to.
    ADD COLUMN channel VARCHAR(20) NOT NULL DEFAULT 'EMAIL' AFTER template_key,
    ADD INDEX idx_email_log_channel (channel);

-- The in-app notifications already on record, brought into the log so the screen opens with the
-- history that exists rather than empty.
--
-- Status DELIVERED, not SENT: an in-app notification is delivered by the act of writing the row —
-- there is no provider between us and the user, so there is no later confirmation to wait for.
-- `delivered_at` is preserved as created_at so the ordering matches when things actually happened.
INSERT INTO email_log
    (template_key, channel, recipient, subject, status, provider, created_at, updated_at)
SELECT
    -- The notification type is the closest thing an in-app row has to a template key, and it is
    -- what makes the Type/Source column useful for these rows.
    COALESCE(n.type, 'NOTIFICATION'),
    'IN_APP',
    CONCAT('user:', n.user_id),
    n.title,
    'DELIVERED',
    'in-app',
    COALESCE(n.delivered_at, n.created_at),
    COALESCE(n.delivered_at, n.created_at)
FROM notifications n;
