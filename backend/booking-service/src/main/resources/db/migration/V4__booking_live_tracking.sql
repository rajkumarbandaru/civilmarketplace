-- Live position of the assigned worker (or vehicle) while they travel to a booking.
--
-- One row per booking, overwritten in place rather than appended to: this answers "where are they
-- now", and a customer watching a plumber approach has no use for the breadcrumb trail. Keeping
-- only the latest fix also means a job that pings every ten seconds for two hours costs one row
-- instead of seven hundred, and there is no history to retain, expire or hand over on request.
--
-- If a location *trail* is ever needed (dispute resolution, distance billing), it belongs in a
-- separate append-only table with its own retention rule — not by relaxing this one.

CREATE TABLE IF NOT EXISTS booking_tracking (
    booking_id   BIGINT       NOT NULL PRIMARY KEY,
    worker_id    BIGINT       NULL,
    -- Same precision as bookings.location_lat/lng, so a comparison between the two cannot lose
    -- digits on one side.
    worker_lat   DECIMAL(10,8) NOT NULL,
    worker_lng   DECIMAL(11,8) NOT NULL,
    -- Degrees from north, and km/h. Both optional: a phone that reports a fix without a fix on
    -- movement is normal, and the UI treats them as unknown rather than as zero.
    heading_deg  SMALLINT     NULL,
    speed_kph    DECIMAL(5,1) NULL,
    -- Free text the worker's app can set ("On the way", "Collecting materials"). Not an enum:
    -- the booking's own status is the authoritative state, and this is only colour on top of it.
    note         VARCHAR(120) NULL,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
