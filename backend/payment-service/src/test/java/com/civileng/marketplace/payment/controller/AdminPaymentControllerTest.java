package com.civileng.marketplace.payment.controller;

import com.civileng.marketplace.payment.model.Payment;
import com.civileng.marketplace.payment.model.PaymentMethod;
import com.civileng.marketplace.payment.model.PaymentStatus;
import com.civileng.marketplace.payment.repository.PaymentRepository;
import com.civileng.marketplace.payment.service.UserNameResolver;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminPaymentController.class)
@DisplayName("AdminPaymentController - revenue & analytics endpoints")
@TestPropertySource(properties = {
    "spring.cloud.config.enabled=false",
    "spring.cloud.config.fail-fast=false",
    "spring.config.import=",
    "eureka.client.enabled=false"
})
class AdminPaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentRepository paymentRepository;

    @MockBean
    private UserNameResolver userNameResolver;

    private Payment samplePayment;

    @BeforeEach
    void setUp() {
        samplePayment = Payment.builder()
                .id(1L)
                .paymentCode("PAY-20241201-0001")
                .bookingId(100L)
                .userId(42L)
                .amount(BigDecimal.valueOf(15000))
                .platformFee(BigDecimal.valueOf(750))
                .gstAmount(BigDecimal.valueOf(135))
                .totalAmount(BigDecimal.valueOf(15885))
                .paymentMethod(PaymentMethod.RAZORPAY_PAYMENT)
                .paymentStatus(PaymentStatus.COMPLETED)
                .currency("INR")
                .paidAt(LocalDateTime.of(2024, 12, 1, 10, 30))
                .createdAt(LocalDateTime.of(2024, 12, 1, 10, 25))
                .build();

        when(userNameResolver.resolve(42L))
                .thenReturn(new UserNameResolver.ResolvedUser("Rahul Sharma", "rahul@example.com", "CUSTOMER", true));
    }

    @Nested
    @DisplayName("GET /admin/revenue/summary")
    class RevenueSummary {

        @Test
        @DisplayName("Returns revenue summary with MTD data")
        void getRevenueSummary_ReturnsSummary() throws Exception {
            when(paymentRepository.findByCreatedAtBetweenAndPaymentStatus(any(), any(), eq(PaymentStatus.COMPLETED)))
                    .thenReturn(List.of(samplePayment));
            when(paymentRepository.findByCreatedAtBetweenAndPaymentStatus(any(), any(), eq(PaymentStatus.REFUNDED)))
                    .thenReturn(List.of());
            when(paymentRepository.findByPaymentStatus(PaymentStatus.COMPLETED))
                    .thenReturn(List.of(samplePayment));

            mockMvc.perform(get("/api/v1/payments/admin/revenue/summary"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.totalRevenueMtd").isNumber())
                    .andExpect(jsonPath("$.data.platformFees").isNumber())
                    .andExpect(jsonPath("$.data.refundsMtd").isNumber())
                    .andExpect(jsonPath("$.data.revenueChange").isString())
                    .andExpect(jsonPath("$.data.pendingPayoutWorkers").value(12));
        }
    }

    @Nested
    @DisplayName("GET /admin/revenue/monthly")
    class MonthlyRevenue {

        @Test
        @DisplayName("Returns 12 months of revenue data")
        void getMonthlyRevenue_Returns12Months() throws Exception {
            when(paymentRepository.findByCreatedAtAfterAndPaymentStatus(any(), eq(PaymentStatus.COMPLETED)))
                    .thenReturn(List.of(samplePayment));

            mockMvc.perform(get("/api/v1/payments/admin/revenue/monthly"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(12));
        }

        @Test
        @DisplayName("Each month entry has all required fields")
        void getMonthlyRevenue_HasRequiredFields() throws Exception {
            when(paymentRepository.findByCreatedAtAfterAndPaymentStatus(any(), eq(PaymentStatus.COMPLETED)))
                    .thenReturn(List.of());

            mockMvc.perform(get("/api/v1/payments/admin/revenue/monthly"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].month").isString())
                    .andExpect(jsonPath("$.data[0].revenue").isNumber())
                    .andExpect(jsonPath("$.data[0].fees").isNumber())
                    .andExpect(jsonPath("$.data[0].payouts").isNumber())
                    .andExpect(jsonPath("$.data[0].profit").isNumber());
        }
    }

    @Nested
    @DisplayName("GET /admin/revenue/breakdown")
    class RevenueBreakdown {

        @Test
        @DisplayName("Returns revenue breakdown items")
        void getRevenueBreakdown_ReturnsItems() throws Exception {
            when(paymentRepository.findByPaymentStatus(PaymentStatus.COMPLETED))
                    .thenReturn(List.of(samplePayment));

            mockMvc.perform(get("/api/v1/payments/admin/revenue/breakdown"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.items").isArray())
                    .andExpect(jsonPath("$.data.items[0].label").isString())
                    .andExpect(jsonPath("$.data.items[0].percentage").isNumber())
                    .andExpect(jsonPath("$.data.items[0].color").isString());
        }
    }

    @Nested
    @DisplayName("GET /admin/revenue/transactions")
    class RecentTransactions {

        @Test
        @DisplayName("Returns paginated transactions with resolved names")
        void getRecentTransactions_ReturnsPage() throws Exception {
            Page<Payment> page = new PageImpl<>(List.of(samplePayment));
            when(paymentRepository.findAll(any(PageRequest.class))).thenReturn(page);

            mockMvc.perform(get("/api/v1/payments/admin/revenue/transactions")
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].customerName").value("Rahul Sharma"))
                    .andExpect(jsonPath("$.data[0].transactionId").value("PAY-20241201-0001"))
                    .andExpect(jsonPath("$.data[0].amount").isNumber())
                    .andExpect(jsonPath("$.totalElements").isNumber());
        }
    }

    @Nested
    @DisplayName("GET /admin/analytics/growth")
    class GrowthMetrics {

        @Test
        @DisplayName("Returns growth metrics array")
        void getGrowthMetrics_ReturnsMetrics() throws Exception {
            when(paymentRepository.countByCreatedAtAfterAndPaymentStatus(any(), eq(PaymentStatus.COMPLETED)))
                    .thenReturn(5L);

            mockMvc.perform(get("/api/v1/payments/admin/analytics/growth"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].label").isString())
                    .andExpect(jsonPath("$.data[0].value").isString())
                    .andExpect(jsonPath("$.data[0].trend").isString());
        }
    }

    @Nested
    @DisplayName("GET /admin/analytics/trends")
    class MonthlyTrends {

        @Test
        @DisplayName("Returns 12 months of trend data")
        void getMonthlyTrends_Returns12Months() throws Exception {
            when(paymentRepository.findByCreatedAtAfterAndPaymentStatus(any(), eq(PaymentStatus.COMPLETED)))
                    .thenReturn(List.of());

            mockMvc.perform(get("/api/v1/payments/admin/analytics/trends"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(12))
                    .andExpect(jsonPath("$.data[0].month").isString())
                    .andExpect(jsonPath("$.data[0].users").isNumber())
                    .andExpect(jsonPath("$.data[0].bookings").isNumber())
                    .andExpect(jsonPath("$.data[0].revenue").isNumber());
        }
    }
}
