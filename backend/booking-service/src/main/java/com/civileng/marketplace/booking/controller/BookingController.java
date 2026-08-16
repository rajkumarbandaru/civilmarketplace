package com.civileng.marketplace.booking.controller;

import com.civileng.marketplace.booking.dto.CreateBookingRequest;
import com.civileng.marketplace.booking.model.Booking;
import com.civileng.marketplace.booking.model.BookingStatus;
import com.civileng.marketplace.booking.model.BookingType;
import com.civileng.marketplace.booking.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@Tag(name = "Booking Management", description = "Booking creation and management APIs")
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @Operation(summary = "Create a new booking")
    public ResponseEntity<Booking> createBooking(
            @RequestHeader("X-User-Id") Long customerId,
            @Valid @RequestBody CreateBookingRequest request) {

        Booking booking = Booking.builder()
                .customerId(customerId)
                .serviceCategory(request.getServiceCategory())
                .serviceName(request.getServiceName())
                .bookingType(BookingType.valueOf(request.getBookingType().toUpperCase()))
                .description(request.getDescription())
                .projectId(request.getProjectId())
                .milestoneId(request.getMilestoneId())
                .addressId(request.getAddressId())
                .locationLat(request.getLocationLat())
                .locationLng(request.getLocationLng())
                .addressLine(request.getAddressLine())
                .city(request.getCity())
                .scheduledDate(request.getScheduledDate())
                .estimatedDurationMinutes(request.getEstimatedDurationMinutes())
                .estimatedCost(request.getEstimatedCost())
                .isEmergency(request.getIsEmergency() != null && request.getIsEmergency())
                .isRecurring(request.getIsRecurring() != null && request.getIsRecurring())
                .recurringFrequency(request.getRecurringFrequency())
                // Normalised here so an unrecognised value cannot quietly create a booking nobody
                // will ever invoice: anything that is not POSTPAID is treated as pay-now.
                .paymentPreference("POSTPAID".equalsIgnoreCase(
                        String.valueOf(request.getPaymentPreference())) ? "POSTPAID" : "PREPAID")
                .build();

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bookingService.createBooking(booking));
    }

    @GetMapping("/{bookingId}")
    @Operation(summary = "Get booking by ID")
    public ResponseEntity<Booking> getBooking(@PathVariable Long bookingId) {
        return ResponseEntity.ok(bookingService.getBooking(bookingId));
    }

    /**
     * Every booking scoped to a Project — the source of project-service's budget-vs-actual
     * rollup and of its "no non-terminal booking" completion guard. Two segments, so it does not
     * collide with {@code /{bookingId}}.
     */
    @GetMapping("/project/{projectId}")
    @Operation(summary = "Get all bookings for a project")
    public ResponseEntity<java.util.List<Booking>> getProjectBookings(@PathVariable Long projectId) {
        return ResponseEntity.ok(bookingService.getBookingsForProject(projectId));
    }

    @GetMapping("/code/{bookingCode}")
    @Operation(summary = "Get booking by code")
    public ResponseEntity<Booking> getBookingByCode(
            @PathVariable String bookingCode) {
        return ResponseEntity.ok(bookingService.getBookingByCode(bookingCode));
    }

    @GetMapping("/customer")
    @Operation(summary = "Get customer bookings")
    public ResponseEntity<Page<Booking>> getCustomerBookings(
            @RequestHeader("X-User-Id") Long customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(bookingService.getCustomerBookings(
                customerId, PageRequest.of(page, size,
                        Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @GetMapping("/worker")
    @Operation(summary = "Get worker bookings")
    public ResponseEntity<Page<Booking>> getWorkerBookings(
            @RequestHeader("X-User-Id") Long workerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(bookingService.getWorkerBookings(
                workerId, PageRequest.of(page, size,
                        Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @PostMapping("/{bookingId}/assign/{workerId}")
    @Operation(summary = "Assign worker to booking")
    public ResponseEntity<Booking> assignWorker(
            @PathVariable Long bookingId,
            @PathVariable Long workerId) {
        return ResponseEntity.ok(
                bookingService.assignWorker(bookingId, workerId));
    }

    @PutMapping("/{bookingId}/status/{status}")
    @Operation(summary = "Update booking status")
    public ResponseEntity<Booking> updateStatus(
            @PathVariable Long bookingId,
            @PathVariable BookingStatus status) {
        return ResponseEntity.ok(
                bookingService.updateStatus(bookingId, status));
    }

    @PostMapping("/{bookingId}/cancel")
    @Operation(summary = "Cancel booking")
    public ResponseEntity<Booking> cancelBooking(
            @PathVariable Long bookingId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody Map<String, String> request) {
        return ResponseEntity.ok(bookingService.cancelBooking(
                bookingId, userId,
                request.getOrDefault("reason", "Cancelled by user")));
    }

    @PostMapping("/{bookingId}/complete")
    @Operation(summary = "Complete booking with final cost")
    public ResponseEntity<Booking> completeBooking(
            @PathVariable Long bookingId,
            @RequestBody Map<String, BigDecimal> request) {
        return ResponseEntity.ok(bookingService.completeBooking(
                bookingId, request.get("finalCost")));
    }

    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "service", "booking-service",
                "status", "UP",
                "timestamp", System.currentTimeMillis()
        ));
    }
}
