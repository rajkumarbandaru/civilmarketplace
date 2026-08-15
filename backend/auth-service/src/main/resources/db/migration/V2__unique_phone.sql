-- One account per phone number.
--
-- `email` has carried a UNIQUE constraint since V1, but `phone` only had a plain index, so
-- duplicates were prevented by an application-level "already registered?" check alone. That
-- check cannot stop two concurrent registrations — both read, both see nothing, both insert.
-- Only the database can.
--
-- Multiple NULLs are permitted by a MySQL unique index, which is what we want: OAuth2 signups
-- have no phone number.
--
-- Soft-deleted rows still occupy their number, matching how the existing email constraint
-- already behaves. Freeing an identifier requires a hard delete.

-- Bring legacy national-format numbers to the E.164 the application now writes, so that
-- `9876519174` and `+919876519174` cannot coexist as two accounts. Skips any row whose
-- normalised form is already taken, leaving a genuine conflict for the constraint to surface
-- rather than silently overwriting one of them.
UPDATE users u
JOIN (
    SELECT id, CONCAT('+91', phone) AS normalised
    FROM users
    WHERE phone IS NOT NULL
      AND phone NOT LIKE '+%'
      AND phone REGEXP '^[0-9]{10}$'
) candidate ON candidate.id = u.id
SET u.phone = candidate.normalised
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT phone FROM users) taken WHERE taken.phone = candidate.normalised
);

ALTER TABLE users ADD CONSTRAINT uk_user_phone UNIQUE (phone);
