-- Pay-now vs pay-later, and the once-only arrival alert.
--
-- Two additions serving the customer-facing notifications:
--
-- 1. `payment_preference` records the choice made at booking time. PREPAID means the booking is
--    only confirmed once payment succeeds; POSTPAID means the work happens first and an invoice
--    with a pay link goes out with the thank-you mail when the job completes.
--
-- 2. `arrival_notified_at` stamps the moment the "your professional is nearly here" mail was sent.
--    The worker's device pings every few seconds, so without a stamp the customer would be mailed
--    on every ping for the whole last kilometre.

ALTER TABLE bookings
    ADD COLUMN payment_preference VARCHAR(20) NULL DEFAULT 'PREPAID';

-- Existing rows predate the choice. They are marked PREPAID rather than left null so the column
-- reads the same for every booking, and because nothing exists to invoice them with.
UPDATE bookings SET payment_preference = 'PREPAID' WHERE payment_preference IS NULL;

ALTER TABLE booking_tracking
    ADD COLUMN arrival_notified_at DATETIME NULL;
