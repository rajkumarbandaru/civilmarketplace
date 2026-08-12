package com.civileng.marketplace.admin.controller;

import com.civileng.marketplace.admin.service.AdminRevenueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/revenue")
@RequiredArgsConstructor
@Tag(name = "Admin Revenue", description = "Admin revenue, financial reports and transactions APIs")
public class AdminRevenueController {

    private final AdminRevenueService adminRevenueService;

    @GetMapping
    @Operation(summary = "Get comprehensive revenue dashboard data")
    public ResponseEntity<Map<String, Object>> getCombinedRevenue() {
        return ResponseEntity.ok(Map.of("success", true, "data", adminRevenueService.getCombinedRevenueData()));
    }

    @GetMapping("/summary")
    @Operation(summary = "Get revenue summary (MTD totals)")
    public ResponseEntity<Map<String, Object>> getRevenueSummary() {
        return ResponseEntity.ok(adminRevenueService.getRevenueSummary());
    }

    @GetMapping("/monthly")
    @Operation(summary = "Get monthly revenue breakdown")
    public ResponseEntity<Map<String, Object>> getMonthlyRevenue() {
        return ResponseEntity.ok(adminRevenueService.getMonthlyRevenue());
    }

    @GetMapping("/breakdown")
    @Operation(summary = "Get revenue breakdown by category")
    public ResponseEntity<Map<String, Object>> getRevenueBreakdown() {
        return ResponseEntity.ok(adminRevenueService.getRevenueBreakdown());
    }

    @GetMapping("/transactions")
    @Operation(summary = "Get recent transactions with pagination")
    public ResponseEntity<Map<String, Object>> getRecentTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminRevenueService.getRecentTransactions(page, size));
    }
}
