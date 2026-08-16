-- ============================================================================
-- Keep what was actually sent, not just its subject line.
--
-- The log could say an email went to a customer but not what it said, which is the half that
-- matters when someone asks "what did you tell them?". Re-rendering the template to answer that
-- would be worse than useless: templates are editable, so it would show today's wording as though
-- it were what the customer received, and a log that quietly rewrites history is one you cannot
-- rely on in a dispute.
--
-- So the rendered body is stored at send time, exactly as it left. For email that is the full
-- HTML; for SMS, WhatsApp and in-app it is the message text.
--
-- The cost is size: a transactional email is roughly 2-5 KB of HTML, so this table now grows at
-- about that per email rather than a few hundred bytes. At this platform's volume that is nothing,
-- but it does mean the log is now the kind of table that eventually wants a retention policy —
-- there is deliberately none yet, because deleting delivery evidence should be a decision someone
-- makes on purpose rather than something that arrives with a schema change.
--
-- Existing rows keep a NULL body: nothing was captured when they were written, and inventing one
-- by re-rendering is exactly the lie described above.
-- ============================================================================

ALTER TABLE email_log
    ADD COLUMN body MEDIUMTEXT NULL AFTER subject;
