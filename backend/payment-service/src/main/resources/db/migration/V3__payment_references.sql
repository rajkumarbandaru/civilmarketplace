-- Payments that are not for a booking: a supplier invoice paid through procurement-service is the
-- first. booking_id stays for bookings; everything else names what it pays by type and id.
ALTER TABLE payments
    MODIFY COLUMN booking_id BIGINT NULL,
    ADD COLUMN reference_type VARCHAR(30) NOT NULL DEFAULT 'BOOKING' AFTER booking_id,
    ADD COLUMN reference_id BIGINT NULL AFTER reference_type,
    ADD INDEX idx_payment_reference (reference_type, reference_id);

UPDATE payments SET reference_id = booking_id WHERE reference_type = 'BOOKING';
