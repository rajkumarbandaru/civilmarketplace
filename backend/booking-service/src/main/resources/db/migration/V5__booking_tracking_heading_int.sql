-- Widen booking_tracking.heading_deg from SMALLINT to INT.
--
-- V4 declared it SMALLINT because a compass bearing is 0-359 and two bytes are plenty. The entity
-- maps it to a Java Integer, and Hibernate's schema validation compares the *declared* SQL type
-- rather than the value range — so it refused to start:
--
--   wrong column type encountered in column [heading_deg] in table [booking_tracking];
--   found [smallint], but expecting [integer]
--
-- That is a hard startup failure, not a warning: booking-service exited on boot and stayed down.
-- The column is widened rather than the entity narrowed to Short, because Integer is the natural
-- type at the API boundary and every caller already treats it as one; changing the field would
-- push the workaround into the DTO and the client for the sake of two bytes a row.

ALTER TABLE booking_tracking MODIFY COLUMN heading_deg INT NULL;
