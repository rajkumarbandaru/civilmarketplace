package com.civileng.marketplace.booking.repository;

import com.civileng.marketplace.booking.model.Booking;
import com.civileng.marketplace.booking.model.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByBookingCode(String bookingCode);

    Page<Booking> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    Page<Booking> findByWorkerIdOrderByCreatedAtDesc(Long workerId, Pageable pageable);

    Page<Booking> findByStatusOrderByCreatedAtDesc(BookingStatus status, Pageable pageable);

    Page<Booking> findByCity(String city, Pageable pageable);

    List<Booking> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    List<Booking> findByWorkerIdAndStatusIn(Long workerId, List<BookingStatus> statuses);

    List<Booking> findByCustomerIdAndStatusIn(Long customerId, List<BookingStatus> statuses);

    // Admin: DB-level filtered query with pagination (fixes pagination metadata bug)
    @Query("SELECT b FROM Booking b WHERE " +
           "(:search IS NULL OR LOWER(b.bookingCode) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(b.serviceName) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
           "(:status IS NULL OR b.status = :status) AND " +
           "(:paymentStatus IS NULL OR b.paymentStatus = :paymentStatus) AND " +
           "(:city IS NULL OR b.city = :city)")
    Page<Booking> findAdminBookings(@Param("search") String search,
                                    @Param("status") BookingStatus status,
                                    @Param("paymentStatus") String paymentStatus,
                                    @Param("city") String city,
                                    Pageable pageable);

    // Admin stats
    long countByStatus(BookingStatus status);

    @Query("SELECT b FROM Booking b WHERE b.scheduledDate BETWEEN :start AND :end " +
           "AND b.status = :status")
    List<Booking> findByScheduledDateBetweenAndStatus(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("status") BookingStatus status);

    @Query("SELECT COUNT(b) FROM Booking b WHERE b.workerId = :workerId " +
           "AND b.status = 'IN_PROGRESS'")
    long countActiveBookingsByWorker(@Param("workerId") Long workerId);

    Page<Booking> findByCityAndStatus(String city, BookingStatus status, Pageable pageable);
}
