-- generateBookingCode() emits "BK-yyyyMMddHHmmss-nnnn" = 22 chars, overflowing the
-- original VARCHAR(20) and failing every insert with a data-truncation error.
ALTER TABLE bookings MODIFY COLUMN booking_code VARCHAR(30) NOT NULL;
