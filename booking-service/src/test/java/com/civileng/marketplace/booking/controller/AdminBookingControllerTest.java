package com.civileng.marketplace.booking.controller;

import com.civileng.marketplace.booking.model.Booking;
import com.civileng.marketplace.booking.model.BookingStatus;
import com.civileng.marketplace.booking.model.BookingType;
import com.civileng.marketplace.booking.model.ServiceCategory;
import com.civileng.marketplace.booking.repository.BookingRepository;
import com.civileng.marketplace.booking.repository.ServiceCategoryRepository;
import com.civileng.marketplace.booking.service.BookingService;
import com.civileng.marketplace.booking.service.UserNameResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.TestPropertySource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminBookingController.class)
@DisplayName("AdminBookingController - admin endpoints for booking & category management")
@TestPropertySource(properties = {
    "spring.cloud.config.enabled=false",
    "spring.cloud.config.fail-fast=false",
    "spring.config.import=",
    "eureka.client.enabled=false",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
})
class AdminBookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @MockBean
    private BookingRepository bookingRepository;

    @MockBean
    private ServiceCategoryRepository serviceCategoryRepository;

    @MockBean
    private BookingService bookingService;

    @MockBean
    private UserNameResolver userNameResolver;

    private Booking sampleBooking;
    private ServiceCategory sampleCategory;

    @BeforeEach
    void setUp() {
        sampleBooking = Booking.builder()
                .id(1L)
                .bookingCode("BK-2024-0001")
                .customerId(42L)
                .serviceName("House Planning")
                .serviceCategory("ARCHITECTURE")
                .status(BookingStatus.PENDING)
                .bookingType(BookingType.INSTANT)
                .estimatedCost(BigDecimal.valueOf(15000))
                .totalAmount(BigDecimal.valueOf(17700))
                .paymentStatus("PENDING")
                .city("Mumbai")
                .createdAt(LocalDateTime.of(2024, 12, 1, 10, 0))
                .build();

        sampleCategory = ServiceCategory.builder()
                .id(1L)
                .name("House Planning")
                .slug("house-planning")
                .description("Residential house planning services")
                .sortOrder(1)
                .isActive(true)
                .createdAt(LocalDateTime.of(2024, 1, 1, 0, 0))
                .build();

        when(userNameResolver.resolve(anyLong()))
                .thenReturn(new UserNameResolver.ResolvedUser("Rahul Sharma", "rahul@example.com", "CUSTOMER", true));
    }

    // ===== GET /admin/all =====

    @Nested
    @DisplayName("GET /admin/all - list bookings")
    class GetAllBookings {

        @Test
        @DisplayName("Returns paginated bookings with no filters")
        void getAllBookings_NoFilters_ReturnsPage() throws Exception {
            Page<Booking> page = new PageImpl<>(List.of(sampleBooking));
            when(bookingRepository.findAdminBookings(isNull(), isNull(), isNull(), isNull(), any(PageRequest.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/api/v1/bookings/admin/all")
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].bookingCode").value("BK-2024-0001"))
                    .andExpect(jsonPath("$.data[0].customerName").value("Rahul Sharma"))
                    .andExpect(jsonPath("$.data[0].serviceName").value("House Planning"))
                    .andExpect(jsonPath("$.data[0].status").value("PENDING"));
        }

        @Test
        @DisplayName("Forwards status filter to repository query")
        void getAllBookings_WithStatusFilter_DelegatesToRepository() throws Exception {
            Page<Booking> emptyPage = Page.empty();
            when(bookingRepository.findAdminBookings(any(), eq(BookingStatus.COMPLETED), any(), any(), any(PageRequest.class)))
                    .thenReturn(emptyPage);

            mockMvc.perform(get("/api/v1/bookings/admin/all")
                            .param("status", "COMPLETED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));

            verify(bookingRepository).findAdminBookings(any(), eq(BookingStatus.COMPLETED), any(), any(), any());
        }

        @Test
        @DisplayName("Handles invalid status gracefully")
        void getAllBookings_InvalidStatus_DefaultsToNoFilter() throws Exception {
            when(bookingRepository.findAdminBookings(any(), isNull(), any(), any(), any(PageRequest.class)))
                    .thenReturn(Page.empty());

            mockMvc.perform(get("/api/v1/bookings/admin/all")
                            .param("status", "INVALID"))
                    .andExpect(status().isOk());

            verify(bookingRepository).findAdminBookings(any(), isNull(), any(), any(), any());
        }
    }

    // ===== GET /admin/{id} =====

    @Nested
    @DisplayName("GET /admin/{bookingId} - booking detail")
    class GetBookingDetail {

        @Test
        @DisplayName("Returns booking with resolved customer name")
        void getBookingDetail_Exists_ReturnsBooking() throws Exception {
            when(bookingRepository.findById(1L)).thenReturn(Optional.of(sampleBooking));

            mockMvc.perform(get("/api/v1/bookings/admin/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.customerName").value("Rahul Sharma"))
                    .andExpect(jsonPath("$.data.bookingCode").value("BK-2024-0001"));
        }

        @Test
        @DisplayName("Returns 400 when booking not found")
        void getBookingDetail_NotFound_ReturnsError() throws Exception {
            when(bookingRepository.findById(999L)).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/v1/bookings/admin/999"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ===== PUT /admin/{id}/status =====

    @Nested
    @DisplayName("PUT /admin/{bookingId}/status - update status")
    class UpdateBookingStatus {

        @Test
        @DisplayName("Updates booking status via BookingService")
        void updateBookingStatus_ValidRequest_UpdatesAndReturns() throws Exception {
            when(bookingService.updateStatus(eq(1L), eq(BookingStatus.CONFIRMED)))
                    .thenReturn(sampleBooking);

            mockMvc.perform(put("/api/v1/bookings/admin/1/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("status", "CONFIRMED"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Booking status updated"));

            verify(bookingService).updateStatus(1L, BookingStatus.CONFIRMED);
        }

        @Test
        @DisplayName("Returns 400 when status is blank")
        void updateBookingStatus_BlankStatus_ReturnsValidationError() throws Exception {
            mockMvc.perform(put("/api/v1/bookings/admin/1/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("status", ""))))
                    .andExpect(status().isBadRequest());
        }
    }

    // ===== POST /admin/{id}/complete =====

    @Nested
    @DisplayName("POST /admin/{bookingId}/complete - complete booking")
    class CompleteBooking {

        @Test
        @DisplayName("Completes booking with final cost")
        void completeBooking_ValidRequest_CompletesBooking() throws Exception {
            when(bookingService.completeBooking(eq(1L), eq(BigDecimal.valueOf(20000))))
                    .thenReturn(sampleBooking);

            mockMvc.perform(post("/api/v1/bookings/admin/1/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("finalCost", 20000))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Booking completed"));

            verify(bookingService).completeBooking(1L, BigDecimal.valueOf(20000));
        }

        @Test
        @DisplayName("Returns 400 when finalCost is null")
        void completeBooking_MissingFinalCost_ReturnsError() throws Exception {
            mockMvc.perform(post("/api/v1/bookings/admin/1/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of())))
                    .andExpect(status().isBadRequest());
        }
    }

    // ===== POST /admin/{id}/cancel =====

    @Nested
    @DisplayName("POST /admin/{bookingId}/cancel - cancel booking")
    class CancelBooking {

        @Test
        @DisplayName("Cancels booking with default reason when not provided")
        void cancelBooking_NoReason_UsesDefault() throws Exception {
            when(bookingService.cancelBooking(eq(1L), eq(0L), anyString()))
                    .thenReturn(sampleBooking);

            mockMvc.perform(post("/api/v1/bookings/admin/1/cancel")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Booking cancelled"));

            verify(bookingService).cancelBooking(1L, 0L, "Cancelled by admin");
        }

        @Test
        @DisplayName("Cancels booking with provided reason")
        void cancelBooking_WithReason_UsesProvided() throws Exception {
            when(bookingService.cancelBooking(eq(1L), eq(0L), eq("Customer requested")))
                    .thenReturn(sampleBooking);

            mockMvc.perform(post("/api/v1/bookings/admin/1/cancel")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("reason", "Customer requested"))))
                    .andExpect(status().isOk());

            verify(bookingService).cancelBooking(1L, 0L, "Customer requested");
        }
    }

    // ===== GET /admin/stats =====

    @Nested
    @DisplayName("GET /admin/stats - booking stats")
    class GetBookingStats {

        @Test
        @DisplayName("Returns aggregated booking counts by status")
        void getBookingStats_ReturnsCounts() throws Exception {
            when(bookingRepository.count()).thenReturn(500L);
            when(bookingRepository.countByStatus(BookingStatus.IN_PROGRESS)).thenReturn(50L);
            when(bookingRepository.countByStatus(BookingStatus.PENDING)).thenReturn(30L);
            when(bookingRepository.countByStatus(BookingStatus.AWAITING_PAYMENT)).thenReturn(10L);
            when(bookingRepository.countByStatus(BookingStatus.COMPLETED)).thenReturn(300L);
            when(bookingRepository.countByStatus(BookingStatus.DISPUTED)).thenReturn(5L);
            when(bookingRepository.countByStatus(BookingStatus.CANCELLED)).thenReturn(100L);

            mockMvc.perform(get("/api/v1/bookings/admin/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalBookings").value(500))
                    .andExpect(jsonPath("$.activeBookings").value(50))
                    .andExpect(jsonPath("$.pendingCount").value(40))
                    .andExpect(jsonPath("$.completedCount").value(300))
                    .andExpect(jsonPath("$.disputedCount").value(5));
        }
    }

    // ===== Category CRUD =====

    @Nested
    @DisplayName("Category management")
    class CategoryManagement {

        @Test
        @DisplayName("GET /admin/categories - returns all categories")
        void getAllCategories_ReturnsList() throws Exception {
            when(serviceCategoryRepository.findAll()).thenReturn(List.of(sampleCategory));

            mockMvc.perform(get("/api/v1/bookings/admin/categories"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[0].name").value("House Planning"))
                    .andExpect(jsonPath("$.data[0].slug").value("house-planning"));
        }

        @Test
        @DisplayName("POST /admin/categories - creates a new category")
        void createCategory_ValidRequest_Creates() throws Exception {
            when(serviceCategoryRepository.existsBySlug("new-category")).thenReturn(false);
            when(serviceCategoryRepository.save(any())).thenReturn(sampleCategory);

            Map<String, Object> request = Map.of(
                    "name", "New Category",
                    "slug", "new-category",
                    "description", "A new test category",
                    "sortOrder", 5
            );

            mockMvc.perform(post("/api/v1/bookings/admin/categories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Category created"));
        }

        @Test
        @DisplayName("POST /admin/categories - rejects duplicate slug")
        void createCategory_DuplicateSlug_ReturnsError() throws Exception {
            when(serviceCategoryRepository.existsBySlug("existing")).thenReturn(true);

            mockMvc.perform(post("/api/v1/bookings/admin/categories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "name", "Existing", "slug", "existing"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("PUT /admin/categories/{id} - updates a category")
        void updateCategory_ValidRequest_Updates() throws Exception {
            when(serviceCategoryRepository.findById(1L)).thenReturn(Optional.of(sampleCategory));
            when(serviceCategoryRepository.save(any())).thenReturn(sampleCategory);

            mockMvc.perform(put("/api/v1/bookings/admin/categories/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "name", "Updated Name", "sortOrder", 2))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Category updated"));
        }

        @Test
        @DisplayName("DELETE /admin/categories/{id} - deletes a category")
        void deleteCategory_Exists_Deletes() throws Exception {
            when(serviceCategoryRepository.findById(1L)).thenReturn(Optional.of(sampleCategory));

            mockMvc.perform(delete("/api/v1/bookings/admin/categories/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Category deleted successfully"));

            verify(serviceCategoryRepository).delete(sampleCategory);
        }

        @Test
        @DisplayName("PUT /admin/categories/{id}/status - toggles active status")
        void toggleCategoryStatus_Active_BecomesInactive() throws Exception {
            when(serviceCategoryRepository.findById(1L)).thenReturn(Optional.of(sampleCategory));
            when(serviceCategoryRepository.save(any())).thenReturn(sampleCategory);

            mockMvc.perform(put("/api/v1/bookings/admin/categories/1/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Category deactivated"));

            verify(serviceCategoryRepository).save(argThat(c -> !c.getIsActive()));
        }
    }
}
