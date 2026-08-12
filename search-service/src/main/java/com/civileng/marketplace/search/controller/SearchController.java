package com.civileng.marketplace.search.controller;

import com.civileng.marketplace.search.dto.ProfileSearchRequest;
import com.civileng.marketplace.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Search and discovery APIs")
public class SearchController {

    private final SearchService searchService;

    @GetMapping("/profiles")
    @Operation(summary = "Search supply-side profiles")
    public ResponseEntity<Map<String, Object>> searchProfiles(
            @ModelAttribute ProfileSearchRequest request) {
        if (request.getSize() > 100) request.setSize(100);
        return ResponseEntity.ok(searchService.searchProfiles(request));
    }

    @GetMapping("/services")
    @Operation(summary = "Search the service catalogue")
    public ResponseEntity<Map<String, Object>> searchServices(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(searchService.searchServices(q, page, Math.min(size, 100)));
    }

    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "success", true, "service", "search-service",
                "status", "UP", "timestamp", System.currentTimeMillis()));
    }
}
