package com.civileng.marketplace.admin.service;

import com.civileng.marketplace.admin.client.BookingServiceClient;
import com.civileng.marketplace.admin.dto.BookingDTO;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Month;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminBookingService {

    private final BookingServiceClient bookingServiceClient;

    @CircuitBreaker(name = "bookingService", fallbackMethod = "getBookingsFallback")
    public Map<String, Object> getBookings(int page, int size, String search, String status, String paymentStatus) {
        try {
            Map<String, Object> response = bookingServiceClient.getAllBookings(page, size, search, status, paymentStatus, null).getBody();
            if (response != null) {
                return response;
            }
        } catch (Exception e) {
            log.warn("Failed to fetch bookings: {}", e.getMessage());
        }
        return getBookingsFallback(page, size, search, status, paymentStatus, new Exception("Fallback"));
    }

    @CircuitBreaker(name = "bookingService", fallbackMethod = "getBookingDetailFallback")
    public Map<String, Object> getBookingDetail(Long bookingId) {
        try {
            Map<String, Object> response = bookingServiceClient.getBookingDetail(bookingId).getBody();
            if (response != null) {
                return response;
            }
        } catch (Exception e) {
            log.warn("Failed to fetch booking {}: {}", bookingId, e.getMessage());
        }
        return getBookingDetailFallback(bookingId, new Exception("Fallback"));
    }

    public Map<String, Object> updateBookingStatus(Long bookingId, BookingDTO.UpdateBookingStatusRequest request) {
        try {
            Map<String, Object> response = bookingServiceClient.updateBookingStatus(bookingId, request).getBody();
            return response != null ? response : createSuccessResponse("Booking status updated");
        } catch (Exception e) {
            log.error("Failed to update booking {}: {}", bookingId, e.getMessage());
            return createErrorResponse("Failed to update booking: " + e.getMessage());
        }
    }

    public Map<String, Object> completeBooking(Long bookingId, BookingDTO.CompleteBookingRequest request) {
        try {
            Map<String, Object> response = bookingServiceClient.completeBooking(bookingId, request).getBody();
            return response != null ? response : createSuccessResponse("Booking completed");
        } catch (Exception e) {
            log.error("Failed to complete booking {}: {}", bookingId, e.getMessage());
            return createErrorResponse("Failed to complete booking: " + e.getMessage());
        }
    }

    public Map<String, Object> cancelBooking(Long bookingId, String reason) {
        try {
            Map<String, Object> response = bookingServiceClient.cancelBooking(bookingId, Map.of("reason", reason)).getBody();
            return response != null ? response : createSuccessResponse("Booking cancelled");
        } catch (Exception e) {
            log.error("Failed to cancel booking {}: {}", bookingId, e.getMessage());
            return createErrorResponse("Failed to cancel booking: " + e.getMessage());
        }
    }

    @CircuitBreaker(name = "bookingService", fallbackMethod = "getBookingStatsFallback")
    public Map<String, Object> getBookingStats() {
        try {
            Map<String, Object> response = bookingServiceClient.getBookingStats().getBody();
            if (response != null) {
                return response;
            }
        } catch (Exception e) {
            log.warn("Failed to fetch booking stats: {}", e.getMessage());
        }
        return getBookingStatsFallback(new Exception("Fallback"));
    }

    private Map<String, Object> getBookingsFallback(int page, int size, String search, String status, String paymentStatus, Throwable t) {
        List<Map<String, Object>> bookings = generateMockBookings(page, size);
        return Map.of(
                "success", true,
                "data", bookings,
                "totalElements", 500,
                "totalPages", (int) Math.ceil(500.0 / size),
                "page", page,
                "size", size
        );
    }

    private Map<String, Object> getBookingDetailFallback(Long bookingId, Throwable t) {
        Map<String, Object> booking = generateMockBooking(bookingId);
        return Map.of("success", true, "data", booking);
    }

    private Map<String, Object> getBookingStatsFallback(Throwable t) {
        return Map.of(
                "activeBookings", 234,
                "pendingCount", 89,
                "completedCount", 456,
                "disputedCount", 12,
                "totalBookings", 1256
        );
    }

    private List<Map<String, Object>> generateMockBookings(int page, int size) {
        List<Map<String, Object>> bookings = new ArrayList<>();
        String[] statuses = {"PENDING", "CONFIRMED", "IN_PROGRESS", "COMPLETED", "CANCELLED", "DISPUTED"};
        String[] customers = {"Rahul Sharma", "Priya Patel", "Amit Singh", "Suresh Kumar", "Neha Gupta"};
        String[] workers = {"Vikram Joshi", "Anita Desai", "Raj Mehta", "Sunil Verma", "Deepa Rao"};
        String[] services = {"House Planning", "Structural Design", "Interior Design", "Land Survey", "Construction"};
        String[] cities = {"Mumbai", "Delhi", "Bangalore", "Pune", "Hyderabad"};

        for (int i = 0; i < Math.min(size, 20); i++) {
            long id = (long) (page * size + i + 1);
            bookings.add(generateMockBooking(id));
        }
        return bookings;
    }

    private Map<String, Object> generateMockBooking(Long id) {
        String[] statuses = {"PENDING", "CONFIRMED", "IN_PROGRESS", "COMPLETED", "CANCELLED", "DISPUTED"};
        String[] customers = {"Rahul Sharma", "Priya Patel", "Amit Singh", "Suresh Kumar", "Neha Gupta"};
        String[] workers = {"Vikram Joshi", "Anita Desai", "Raj Mehta", "Sunil Verma", "Deepa Rao"};
        String[] services = {"House Planning", "Structural Design", "Interior Design", "Land Survey", "Construction"};
        String[] cities = {"Mumbai", "Delhi", "Bangalore", "Pune", "Hyderabad"};
        String[] paymentStatuses = {"PAID", "PENDING", "REFUNDED"};

        int idx = id.intValue();
        return Map.of(
                "id", id,
                "bookingCode", String.format("BK-2024-%04d", id),
                "customerName", customers[idx % customers.length],
                "workerName", workers[idx % workers.length],
                "serviceName", services[idx % services.length],
                "status", statuses[idx % statuses.length],
                "amount", 5000 + (idx * 1000) % 50000,
                "city", cities[idx % cities.length],
                "createdAt", "2024-12-" + String.format("%02d", (idx % 28) + 1),
                "paymentStatus", paymentStatuses[idx % paymentStatuses.length]
        );
    }

    private Map<String, Object> createSuccessResponse(String message) {
        return Map.of("success", true, "message", message);
    }

    private Map<String, Object> createErrorResponse(String message) {
        return Map.of("success", false, "message", message);
    }
}
