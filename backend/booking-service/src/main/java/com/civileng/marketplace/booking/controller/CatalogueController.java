package com.civileng.marketplace.booking.controller;

import com.civileng.marketplace.booking.service.CatalogueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

/**
 * The catalogue the public site renders: categories and bookable items, active ones only.
 *
 * Deliberately outside {@code /api/v1/bookings/**}: that prefix is behind the gateway's JwtAuth
 * filter, and the services list is the first thing a signed-out visitor sees. It is routed as
 * {@code /api/v1/catalogue/**} with no auth filter, and exposes nothing a visitor could not already
 * read off the page.
 */
@RestController
@RequestMapping("/api/v1/catalogue")
@RequiredArgsConstructor
@Tag(name = "Public Catalogue", description = "Service catalogue for the public site")
public class CatalogueController {

    private final CatalogueService catalogueService;

    @GetMapping
    @Operation(summary = "Active service categories and offerings")
    public ResponseEntity<Map<String, Object>> getCatalogue() {
        // Short cache: the list changes only when an admin edits it, and every page load needs it.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(Map.of("success", true, "data", catalogueService.publicCatalogue()));
    }
}
