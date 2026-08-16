package com.civileng.marketplace.user.geo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/**
 * Country, state and city lists for the address pickers.
 *
 * <p>Three endpoints rather than one tree, because the client only ever needs the next level down
 * and the whole tree is far larger than any one screen uses. Cached hard: this data changes when a
 * country redraws its states, not between page loads.
 */
@RestController
@RequestMapping("/api/v1/geo")
@RequiredArgsConstructor
@Tag(name = "Geography", description = "Country, state and city reference data for address forms")
public class GeoController {

    private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofDays(7)).cachePublic();

    private final GeoService geoService;

    @GetMapping("/countries")
    @Operation(summary = "Every country the platform serves")
    public ResponseEntity<List<GeoService.CountrySummary>> countries() {
        return ResponseEntity.ok().cacheControl(CACHE).body(geoService.countries());
    }

    @GetMapping("/countries/{countryCode}/states")
    @Operation(summary = "States or provinces of one country")
    public ResponseEntity<List<GeoService.StateSummary>> states(@PathVariable String countryCode) {
        return ResponseEntity.ok().cacheControl(CACHE).body(geoService.states(countryCode));
    }

    /** {@code state} accepts either the state code or its full name. */
    @GetMapping("/countries/{countryCode}/cities")
    @Operation(summary = "Cities of one state")
    public ResponseEntity<List<String>> cities(@PathVariable String countryCode,
                                               @RequestParam String state) {
        return ResponseEntity.ok().cacheControl(CACHE).body(geoService.cities(countryCode, state));
    }
}
