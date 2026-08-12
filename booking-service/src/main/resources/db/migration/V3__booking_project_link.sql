-- Links a Booking to the Project it was booked against (SRS ENT·01 FR-03/FR-04).
--
-- Nullable on purpose: a Customer's single-trade booking made without a Project is still valid,
-- and every booking that already exists predates Projects entirely. No FK constraint — projects
-- live in a different service and therefore a different schema, so referential integrity is the
-- application's job, not the database's.
ALTER TABLE bookings ADD COLUMN project_id BIGINT NULL;
ALTER TABLE bookings ADD COLUMN milestone_id BIGINT NULL;

CREATE INDEX idx_booking_project ON bookings (project_id);
