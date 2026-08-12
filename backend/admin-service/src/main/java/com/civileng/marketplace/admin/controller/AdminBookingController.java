package com.civileng.marketplace.admin.controller;

import com.civileng.marketplace.admin.dto.BookingDTO;
import com.civileng.marketplace.admin.service.AdminBookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/bookings")
@RequiredArgsConstructor
@Tag(name = "Admin Booking Management", description = "Admin booking lifecycle management APIs")
public class AdminBookingController {

    private final AdminBookingService adminBookingService;

    @GetMapping
    @Operation(summary = "Get paginated bookings with filters")
    public ResponseEntity<Map<String, Object>> getBookings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String paymentStatus) {
        return ResponseEntity.ok(adminBookingService.getBookings(page, size, search, status, paymentStatus));
    }

    @GetMapping("/{bookingId}")
    @Operation(summary = "Get booking details by ID")
    public ResponseEntity<Map<String, Object>> getBookingDetail(@PathVariable Long bookingId) {
        return ResponseEntity.ok(adminBookingService.getBookingDetail(bookingId));
    }

    @PutMapping("/{bookingId}/status")
    @Operation(summary = "Update booking status")
    public ResponseEntity<Map<String, Object>> updateBookingStatus(
            @PathVariable Long bookingId,
            @Valid @RequestBody BookingDTO.UpdateBookingStatusRequest request) {
        return ResponseEntity.ok(adminBookingService.updateBookingStatus(bookingId, request));
    }

    @PostMapping("/{bookingId}/complete")
    @Operation(summary = "Mark booking as completed with final cost")
    public ResponseEntity<Map<String, Object>> completeBooking(
            @PathVariable Long bookingId,
            @Valid @RequestBody BookingDTO.CompleteBookingRequest request) {
        return ResponseEntity.ok(adminBookingService.completeBooking(bookingId, request));
    }

    @PostMapping("/{bookingId}/cancel")
    @Operation(summary = "Cancel a booking with reason")
    public ResponseEntity<Map<String, Object>> cancelBooking(
            @PathVariable Long bookingId,
            @RequestBody Map<String, String> request) {
        return ResponseEntity.ok(adminBookingService.cancelBooking(
                bookingId, request.getOrDefault("reason", "Cancelled by admin")));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get booking statistics summary")
    public ResponseEntity<Map<String, Object>> getBookingStats() {
        return ResponseEntity.ok(adminBookingService.getBookingStats());
    }
}
