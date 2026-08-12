package com.civileng.marketplace.booking.controller;

import com.civileng.marketplace.booking.model.Booking;
import com.civileng.marketplace.booking.model.BookingStatus;
import com.civileng.marketplace.booking.model.ServiceCategory;
import com.civileng.marketplace.booking.repository.BookingRepository;
import com.civileng.marketplace.booking.repository.ServiceCategoryRepository;
import com.civileng.marketplace.booking.service.BookingService;
import com.civileng.marketplace.booking.service.UserNameResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/bookings/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Booking Management (Booking)", description = "Admin endpoints for booking lifecycle and category management")
public class AdminBookingController {

    private final BookingRepository bookingRepository;
    private final ServiceCategoryRepository serviceCategoryRepository;
    private final BookingService bookingService;
    private final UserNameResolver userNameResolver;

    @GetMapping("/all")
    @Operation(summary = "Get all bookings with pagination and filters")
    public ResponseEntity<Map<String, Object>> getAllBookings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) String city) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        // Convert string params to typed values for the DB-level query
        String searchParam = (search != null && !search.isBlank()) ? search.trim() : null;
        BookingStatus statusParam = null;
        if (status != null && !status.isBlank()) {
            try {
                statusParam = BookingStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                // Invalid status filter, treat as no filter
            }
        }
        String paymentParam = (paymentStatus != null && !paymentStatus.isBlank()) ? paymentStatus.trim().toUpperCase() : null;
        String cityParam = (city != null && !city.isBlank()) ? city.trim() : null;

        // Use single DB-level query with all filters — pagination metadata is now correct
        Page<Booking> bookingPage = bookingRepository.findAdminBookings(
                searchParam, statusParam, paymentParam, cityParam, pageable);

        var bookings = bookingPage.getContent().stream()
                .map(this::toBookingMap)
                .toList();

        return ResponseEntity.ok(Map.of(
                "success", true, "data", bookings,
                "page", bookingPage.getNumber(),
                "size", bookingPage.getSize(),
                "totalElements", bookingPage.getTotalElements(),
                "totalPages", bookingPage.getTotalPages()
        ));
    }

    @GetMapping("/{bookingId}")
    @Operation(summary = "Get booking details by ID")
    public ResponseEntity<Map<String, Object>> getBookingDetail(@PathVariable Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found with id: " + bookingId));
        return ResponseEntity.ok(Map.of("success", true, "data", toBookingMap(booking)));
    }

    @PutMapping("/{bookingId}/status")
    @Operation(summary = "Update booking status")
    public ResponseEntity<Map<String, Object>> updateBookingStatus(
            @PathVariable Long bookingId,
            @Valid @RequestBody UpdateBookingStatusRequest request) {
        BookingStatus newStatus = BookingStatus.valueOf(request.getStatus().toUpperCase());
        Booking booking = bookingService.updateStatus(bookingId, newStatus);
        log.info("Admin updated booking {} status to {}", bookingId, newStatus);
        return ResponseEntity.ok(Map.of("success", true, "message", "Booking status updated", "data", toBookingMap(booking)));
    }

    @PostMapping("/{bookingId}/complete")
    @Operation(summary = "Complete a booking with final cost")
    public ResponseEntity<Map<String, Object>> completeBooking(
            @PathVariable Long bookingId,
            @Valid @RequestBody CompleteBookingRequest request) {
        Booking booking = bookingService.completeBooking(bookingId, request.getFinalCost());
        log.info("Admin completed booking {} with cost {}", bookingId, request.getFinalCost());
        return ResponseEntity.ok(Map.of("success", true, "message", "Booking completed", "data", toBookingMap(booking)));
    }

    @PostMapping("/{bookingId}/cancel")
    @Operation(summary = "Cancel a booking with reason")
    public ResponseEntity<Map<String, Object>> cancelBooking(
            @PathVariable Long bookingId,
            @RequestBody Map<String, String> request) {
        Booking booking = bookingService.cancelBooking(
                bookingId, 0L,
                request.getOrDefault("reason", "Cancelled by admin"));
        log.info("Admin cancelled booking {}", bookingId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Booking cancelled", "data", toBookingMap(booking)));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get booking statistics summary")
    public ResponseEntity<Map<String, Object>> getBookingStats() {
        long totalBookings = bookingRepository.count();
        long activeBookings = bookingRepository.countByStatus(BookingStatus.IN_PROGRESS);
        long pendingCount = bookingRepository.countByStatus(BookingStatus.PENDING)
                + bookingRepository.countByStatus(BookingStatus.AWAITING_PAYMENT);
        long completedCount = bookingRepository.countByStatus(BookingStatus.COMPLETED);
        long disputedCount = bookingRepository.countByStatus(BookingStatus.DISPUTED);
        long cancelledCount = bookingRepository.countByStatus(BookingStatus.CANCELLED);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "totalBookings", totalBookings,
                "activeBookings", activeBookings,
                "pendingCount", pendingCount,
                "completedCount", completedCount,
                "disputedCount", disputedCount,
                "cancelledCount", cancelledCount
        ));
    }

    // ===== Category Management =====

    @GetMapping("/categories")
    @Operation(summary = "Get all service categories")
    public ResponseEntity<Map<String, Object>> getAllCategories() {
        List<ServiceCategory> categories = serviceCategoryRepository.findAll();
        var categoryList = categories.stream().map(this::toCategoryMap).toList();
        return ResponseEntity.ok(Map.of("success", true, "data", categoryList));
    }

    @PostMapping("/categories")
    @Operation(summary = "Create a new service category")
    public ResponseEntity<Map<String, Object>> createCategory(
            @Valid @RequestBody CreateCategoryRequest request) {
        if (serviceCategoryRepository.existsBySlug(request.getSlug())) {
            throw new IllegalArgumentException("Category slug already exists: " + request.getSlug());
        }

        ServiceCategory category = ServiceCategory.builder()
                .name(request.getName())
                .slug(request.getSlug())
                .description(request.getDescription())
                .icon(request.getIcon())
                .image(request.getImage())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .isActive(true)
                .build();

        if (request.getParentId() != null) {
            ServiceCategory parent = serviceCategoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException("Parent category not found"));
            category.setParent(parent);
        }

        ServiceCategory saved = serviceCategoryRepository.save(category);
        log.info("Admin created category: {}", saved.getName());
        return ResponseEntity.ok(Map.of("success", true, "message", "Category created", "data", toCategoryMap(saved)));
    }

    @PutMapping("/categories/{categoryId}")
    @Operation(summary = "Update an existing category")
    public ResponseEntity<Map<String, Object>> updateCategory(
            @PathVariable Long categoryId,
            @Valid @RequestBody UpdateCategoryRequest request) {
        ServiceCategory category = serviceCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found with id: " + categoryId));

        if (request.getName() != null) category.setName(request.getName());
        if (request.getSlug() != null) {
            if (!request.getSlug().equals(category.getSlug()) &&
                    serviceCategoryRepository.existsBySlug(request.getSlug())) {
                throw new IllegalArgumentException("Category slug already exists: " + request.getSlug());
            }
            category.setSlug(request.getSlug());
        }
        if (request.getDescription() != null) category.setDescription(request.getDescription());
        if (request.getIcon() != null) category.setIcon(request.getIcon());
        if (request.getImage() != null) category.setImage(request.getImage());
        if (request.getSortOrder() != null) category.setSortOrder(request.getSortOrder());
        if (request.getParentId() != null) {
            if (request.getParentId().equals(categoryId)) {
                throw new IllegalArgumentException("Category cannot be its own parent");
            }
            ServiceCategory parent = serviceCategoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException("Parent category not found"));
            category.setParent(parent);
        }
        if (request.getActive() != null) category.setIsActive(request.getActive());

        ServiceCategory saved = serviceCategoryRepository.save(category);
        log.info("Admin updated category: {}", saved.getId());
        return ResponseEntity.ok(Map.of("success", true, "message", "Category updated", "data", toCategoryMap(saved)));
    }

    @DeleteMapping("/categories/{categoryId}")
    @Operation(summary = "Delete a category")
    public ResponseEntity<Map<String, Object>> deleteCategory(@PathVariable Long categoryId) {
        ServiceCategory category = serviceCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found with id: " + categoryId));
        serviceCategoryRepository.delete(category);
        log.info("Admin deleted category: {}", categoryId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Category deleted successfully"));
    }

    @PutMapping("/categories/{categoryId}/status")
    @Operation(summary = "Toggle category active status")
    public ResponseEntity<Map<String, Object>> toggleCategoryStatus(@PathVariable Long categoryId) {
        ServiceCategory category = serviceCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found with id: " + categoryId));
        category.setIsActive(!category.getIsActive());
        serviceCategoryRepository.save(category);
        log.info("Admin toggled category {} status to {}", categoryId, category.getIsActive());
        return ResponseEntity.ok(Map.of("success", true, "message",
                "Category " + (category.getIsActive() ? "activated" : "deactivated")));
    }

    private Map<String, Object> toBookingMap(Booking b) {
        // Resolve real names from auth-service with caching
        var customer = userNameResolver.resolve(b.getCustomerId());
        var worker = b.getWorkerId() != null ? userNameResolver.resolve(b.getWorkerId()) : null;

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", b.getId());
        map.put("bookingCode", b.getBookingCode());
        map.put("customerName", customer.name());
        map.put("customerEmail", customer.email());
        map.put("customerId", b.getCustomerId());
        map.put("workerName", worker != null ? worker.name() : null);
        map.put("workerEmail", worker != null ? worker.email() : null);
        map.put("workerId", b.getWorkerId());
        map.put("serviceName", b.getServiceName());
        map.put("serviceCategory", b.getServiceCategory());
        map.put("status", b.getStatus().name());
        map.put("amount", b.getEstimatedCost() != null ? b.getEstimatedCost().doubleValue() : 0);
        map.put("totalAmount", b.getTotalAmount() != null ? b.getTotalAmount().doubleValue() : 0);
        map.put("city", b.getCity());
        map.put("description", b.getDescription());
        map.put("cancellationReason", b.getCancellationReason());
        map.put("paymentStatus", b.getPaymentStatus());
        map.put("scheduledDate", b.getScheduledDate() != null ? b.getScheduledDate().toString() : null);
        map.put("completedAt", b.getCompletedAt() != null ? b.getCompletedAt().toString() : null);
        map.put("createdAt", b.getCreatedAt() != null ? b.getCreatedAt().toString() : null);
        return map;
    }

    private Map<String, Object> toCategoryMap(ServiceCategory c) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", c.getId());
        map.put("name", c.getName());
        map.put("slug", c.getSlug());
        map.put("description", c.getDescription());
        map.put("icon", c.getIcon());
        map.put("image", c.getImage());
        map.put("sortOrder", c.getSortOrder());
        map.put("active", c.getIsActive());
        map.put("servicesCount", 0);
        if (c.getParent() != null) {
            map.put("parentId", c.getParent().getId());
            map.put("parentName", c.getParent().getName());
        }
        return map;
    }

    @Data
    public static class UpdateBookingStatusRequest {
        @NotBlank(message = "Status is required")
        private String status;
        private String reason;
    }

    @Data
    public static class CompleteBookingRequest {
        @NotNull(message = "Final cost is required")
        private BigDecimal finalCost;
    }

    @Data
    public static class CreateCategoryRequest {
        @NotBlank(message = "Name is required")
        private String name;
        @NotBlank(message = "Slug is required")
        private String slug;
        private String description;
        private String icon;
        private String image;
        private Long parentId;
        private Integer sortOrder;
    }

    @Data
    public static class UpdateCategoryRequest {
        private String name;
        private String slug;
        private String description;
        private String icon;
        private String image;
        private Long parentId;
        private Integer sortOrder;
        private Boolean active;
    }
}
