package com.civileng.marketplace.admin.controller;

import com.civileng.marketplace.admin.service.AdminAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/analytics")
@RequiredArgsConstructor
@Tag(name = "Admin Analytics", description = "Admin analytics and performance metrics APIs")
public class AdminAnalyticsController {

    private final AdminAnalyticsService adminAnalyticsService;

    @GetMapping
    @Operation(summary = "Get comprehensive analytics dashboard data")
    public ResponseEntity<Map<String, Object>> getAnalytics() {
        return ResponseEntity.ok(Map.of("success", true, "data", adminAnalyticsService.getAnalytics()));
    }

    @GetMapping("/growth")
    @Operation(summary = "Get growth metrics (users, bookings, revenue)")
    public ResponseEntity<Map<String, Object>> getGrowthMetrics() {
        return ResponseEntity.ok(adminAnalyticsService.getGrowthMetrics());
    }

    @GetMapping("/revenue-trend")
    @Operation(summary = "Get monthly revenue trends for the year")
    public ResponseEntity<Map<String, Object>> getMonthlyTrends() {
        return ResponseEntity.ok(adminAnalyticsService.getMonthlyTrends());
    }

    @GetMapping("/categories")
    @Operation(summary = "Get top service categories by bookings")
    public ResponseEntity<Map<String, Object>> getTopCategories() {
        return ResponseEntity.ok(adminAnalyticsService.getTopCategories());
    }

    @GetMapping("/cities")
    @Operation(summary = "Get city-wise performance data")
    public ResponseEntity<Map<String, Object>> getCityPerformance() {
        return ResponseEntity.ok(adminAnalyticsService.getCityPerformance());
    }
}
