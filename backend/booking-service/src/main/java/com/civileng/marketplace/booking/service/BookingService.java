package com.civileng.marketplace.booking.service;

import com.civileng.marketplace.booking.event.BookingEventPublisher;
import com.civileng.marketplace.booking.model.Booking;
import com.civileng.marketplace.booking.model.BookingStatus;
import com.civileng.marketplace.booking.model.BookingType;
import com.civileng.marketplace.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingEventPublisher events;

    private static final BigDecimal PLATFORM_FEE_PERCENTAGE = new BigDecimal("5.00");
    private static final BigDecimal GST_PERCENTAGE = new BigDecimal("18.00");
    private static final int MAX_ACTIVE_BOOKINGS_PER_WORKER = 5;

    @Transactional
    public Booking createBooking(Booking booking) {
        booking.setBookingCode(generateBookingCode());

        if (booking.getEstimatedCost() != null) {
            booking.setPlatformFee(calculatePlatformFee(booking.getEstimatedCost()));
            booking.setGstAmount(calculateGst(booking.getEstimatedCost()
                    .add(booking.getPlatformFee())));
            booking.setTotalAmount(booking.getEstimatedCost()
                    .add(booking.getPlatformFee())
                    .add(booking.getGstAmount()));
        }

        booking.setStatus(booking.getBookingType() == BookingType.INSTANT
                ? BookingStatus.PENDING : BookingStatus.QUOTATION_PENDING);

        Booking saved = bookingRepository.save(booking);
        log.info("Booking created: {} with code {}", saved.getId(), saved.getBookingCode());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Booking> getBookingsForProject(Long projectId) {
        return bookingRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    @Transactional
    public Booking assignWorker(Long bookingId, Long workerId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));

        if (booking.getStatus() != BookingStatus.PENDING &&
                booking.getStatus() != BookingStatus.QUOTATION_ACCEPTED) {
            throw new IllegalStateException("Booking cannot be assigned in current status");
        }

        long activeBookings = bookingRepository
                .countActiveBookingsByWorker(workerId);
        if (activeBookings >= MAX_ACTIVE_BOOKINGS_PER_WORKER) {
            throw new IllegalStateException(
                    "Worker has reached maximum active bookings limit");
        }

        booking.setWorkerId(workerId);
        booking.setStatus(BookingStatus.ASSIGNED);
        booking.setStartedAt(LocalDateTime.now());

        Booking saved = bookingRepository.save(booking);
        log.info("Worker {} assigned to booking {}", workerId, bookingId);
        return saved;
    }

    @Transactional
    public Booking updateStatus(Long bookingId, BookingStatus newStatus) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));

        validateStatusTransition(booking.getStatus(), newStatus);
        booking.setStatus(newStatus);

        if (newStatus == BookingStatus.IN_PROGRESS) {
            booking.setStartedAt(LocalDateTime.now());
        } else if (newStatus == BookingStatus.COMPLETED) {
            booking.setCompletedAt(LocalDateTime.now());
            if (booking.getFinalCost() == null) {
                booking.setFinalCost(booking.getEstimatedCost());
            }
        } else if (newStatus == BookingStatus.CANCELLED) {
            booking.setCancelledAt(LocalDateTime.now());
        }

        Booking saved = bookingRepository.save(booking);
        log.info("Booking {} status updated to {}", bookingId, newStatus);
        // A job can be finished from here as well as through completeBooking, and a customer whose
        // booking was closed by the second route is owed the same thank-you, rating request and
        // (for pay-later) invoice as one closed by the first.
        if (newStatus == BookingStatus.COMPLETED) {
            events.publishCompleted(saved);
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public Booking getBooking(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));
    }

    @Transactional(readOnly = true)
    public Booking getBookingByCode(String bookingCode) {
        return bookingRepository.findByBookingCode(bookingCode)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));
    }

    @Transactional(readOnly = true)
    public Page<Booking> getCustomerBookings(Long customerId, Pageable pageable) {
        return bookingRepository
                .findByCustomerIdOrderByCreatedAtDesc(customerId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Booking> getWorkerBookings(Long workerId, Pageable pageable) {
        return bookingRepository
                .findByWorkerIdOrderByCreatedAtDesc(workerId, pageable);
    }

    @Transactional
    public Booking cancelBooking(Long bookingId, Long userId, String reason) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));

        if (booking.getStatus() == BookingStatus.COMPLETED ||
                booking.getStatus() == BookingStatus.CANCELLED) {
            throw new IllegalStateException("Booking cannot be cancelled");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancellationReason(reason);
        booking.setCancelledBy(userId);
        booking.setCancelledAt(LocalDateTime.now());

        Booking saved = bookingRepository.save(booking);
        log.info("Booking {} cancelled by user {}", bookingId, userId);
        return saved;
    }

    @Transactional
    public Booking completeBooking(Long bookingId, BigDecimal finalCost) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));

        if (booking.getStatus() != BookingStatus.IN_PROGRESS) {
            throw new IllegalStateException("Booking must be in progress to complete");
        }

        booking.setFinalCost(finalCost);
        booking.setPlatformFee(calculatePlatformFee(finalCost));
        booking.setGstAmount(calculateGst(finalCost.add(booking.getPlatformFee())));
        booking.setTotalAmount(finalCost
                .add(booking.getPlatformFee())
                .add(booking.getGstAmount()));
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setCompletedAt(LocalDateTime.now());

        Booking saved = bookingRepository.save(booking);
        log.info("Booking {} completed with final cost: {}", bookingId, finalCost);
        // Sends the thank-you and the rating request — and, on a pay-later booking, the invoice
        // for the work that has just been done.
        events.publishCompleted(saved);
        return saved;
    }

    private void validateStatusTransition(BookingStatus current, BookingStatus next) {
        boolean valid = switch (current) {
            case PENDING -> next == BookingStatus.ASSIGNED ||
                    next == BookingStatus.CANCELLED;
            case QUOTATION_PENDING -> next == BookingStatus.QUOTATION_SENT ||
                    next == BookingStatus.CANCELLED;
            case QUOTATION_SENT -> next == BookingStatus.QUOTATION_ACCEPTED ||
                    next == BookingStatus.QUOTATION_REJECTED ||
                    next == BookingStatus.CANCELLED;
            case QUOTATION_ACCEPTED -> next == BookingStatus.AWAITING_PAYMENT ||
                    next == BookingStatus.CANCELLED;
            case AWAITING_PAYMENT -> next == BookingStatus.CONFIRMED ||
                    next == BookingStatus.CANCELLED;
            case CONFIRMED -> next == BookingStatus.ASSIGNED ||
                    next == BookingStatus.CANCELLED;
            case ASSIGNED -> next == BookingStatus.IN_PROGRESS ||
                    next == BookingStatus.CANCELLED;
            case IN_PROGRESS -> next == BookingStatus.COMPLETED ||
                    next == BookingStatus.CANCELLED;
            case COMPLETED -> next == BookingStatus.REFUNDED ||
                    next == BookingStatus.DISPUTED;
            default -> false;
        };

        if (!valid) {
            throw new IllegalStateException(
                    "Cannot transition from " + current + " to " + next);
        }
    }

    private String generateBookingCode() {
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String random = String.format("%04d", new Random().nextInt(10000));
        return "BK-" + timestamp + "-" + random;
    }

    private BigDecimal calculatePlatformFee(BigDecimal amount) {
        return amount.multiply(PLATFORM_FEE_PERCENTAGE)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateGst(BigDecimal amount) {
        return amount.multiply(GST_PERCENTAGE)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }
}
