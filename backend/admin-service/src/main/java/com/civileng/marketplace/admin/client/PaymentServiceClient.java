package com.civileng.marketplace.admin.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "payment-service", path = "/api/v1/payments")
public interface PaymentServiceClient {

    @GetMapping("/admin/revenue/summary")
    ResponseEntity<Map<String, Object>> getRevenueSummary();

    @GetMapping("/admin/revenue/monthly")
    ResponseEntity<Map<String, Object>> getMonthlyRevenue();

    @GetMapping("/admin/revenue/breakdown")
    ResponseEntity<Map<String, Object>> getRevenueBreakdown();

    @GetMapping("/admin/revenue/transactions")
    ResponseEntity<Map<String, Object>> getRecentTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size);

    @GetMapping("/admin/analytics/growth")
    ResponseEntity<Map<String, Object>> getGrowthMetrics();

    @GetMapping("/admin/analytics/trends")
    ResponseEntity<Map<String, Object>> getMonthlyTrends();
}
