package com.civileng.marketplace.booking.repository;

import com.civileng.marketplace.booking.model.BookingTracking;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingTrackingRepository extends JpaRepository<BookingTracking, Long> {
}
