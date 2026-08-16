package com.civileng.marketplace.admin.client;

import com.civileng.marketplace.admin.dto.BookingDTO;
import com.civileng.marketplace.admin.dto.CategoryDTO;
import com.civileng.marketplace.admin.dto.ServiceOfferingDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(name = "booking-service", path = "/api/v1/bookings")
public interface BookingServiceClient {

    @GetMapping("/admin/all")
    ResponseEntity<Map<String, Object>> getAllBookings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) String city);

    @GetMapping("/admin/{bookingId}")
    ResponseEntity<Map<String, Object>> getBookingDetail(@PathVariable Long bookingId);

    @PutMapping("/admin/{bookingId}/status")
    ResponseEntity<Map<String, Object>> updateBookingStatus(
            @PathVariable Long bookingId,
            @RequestBody BookingDTO.UpdateBookingStatusRequest request);

    @PostMapping("/admin/{bookingId}/complete")
    ResponseEntity<Map<String, Object>> completeBooking(
            @PathVariable Long bookingId,
            @RequestBody BookingDTO.CompleteBookingRequest request);

    @PostMapping("/admin/{bookingId}/cancel")
    ResponseEntity<Map<String, Object>> cancelBooking(
            @PathVariable Long bookingId,
            @RequestBody Map<String, String> request);

    @GetMapping("/admin/stats")
    ResponseEntity<Map<String, Object>> getBookingStats();

    @GetMapping("/admin/categories")
    ResponseEntity<Map<String, Object>> getAllCategories();

    @PostMapping("/admin/categories")
    ResponseEntity<Map<String, Object>> createCategory(
            @RequestBody CategoryDTO.CreateCategoryRequest request);

    @PutMapping("/admin/categories/{categoryId}")
    ResponseEntity<Map<String, Object>> updateCategory(
            @PathVariable Long categoryId,
            @RequestBody CategoryDTO.UpdateCategoryRequest request);

    @DeleteMapping("/admin/categories/{categoryId}")
    ResponseEntity<Map<String, Object>> deleteCategory(@PathVariable Long categoryId);

    @PutMapping("/admin/categories/{categoryId}/status")
    ResponseEntity<Map<String, Object>> toggleCategoryStatus(@PathVariable Long categoryId);

    // ===== Catalogue items (the rows the public Services page shows) =====

    @GetMapping("/admin/services")
    ResponseEntity<Map<String, Object>> getAllServices();

    @PostMapping("/admin/services")
    ResponseEntity<Map<String, Object>> createService(@RequestBody ServiceOfferingDTO.OfferingRequest request);

    @PutMapping("/admin/services/{serviceId}")
    ResponseEntity<Map<String, Object>> updateService(@PathVariable Long serviceId,
                                                      @RequestBody ServiceOfferingDTO.OfferingRequest request);

    @DeleteMapping("/admin/services/{serviceId}")
    ResponseEntity<Map<String, Object>> deleteService(@PathVariable Long serviceId);

    @PutMapping("/admin/services/{serviceId}/status")
    ResponseEntity<Map<String, Object>> toggleServiceStatus(@PathVariable Long serviceId);
}
