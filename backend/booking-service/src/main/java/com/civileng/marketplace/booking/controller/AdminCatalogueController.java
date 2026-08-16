package com.civileng.marketplace.booking.controller;

import com.civileng.marketplace.booking.model.ServiceOffering;
import com.civileng.marketplace.booking.repository.ServiceOfferingRepository;
import com.civileng.marketplace.booking.service.CatalogueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Map;

/**
 * Admin CRUD over the catalogue items themselves — the rows the Services page shows.
 *
 * Category management lives next door in {@link AdminBookingController}; this covers the individual
 * offerings under those categories, which until now were a hard-coded frontend array with no way in
 * at all.
 */
@RestController
@RequestMapping("/api/v1/bookings/admin/services")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Catalogue Management", description = "CRUD for the public service catalogue")
public class AdminCatalogueController {

    private final ServiceOfferingRepository offeringRepository;
    private final CatalogueService catalogueService;

    @GetMapping
    @Operation(summary = "Get every catalogue item, active or not")
    public ResponseEntity<Map<String, Object>> getAll() {
        return ResponseEntity.ok(Map.of("success", true, "data", catalogueService.allOfferings()));
    }

    @PostMapping
    @Operation(summary = "Create a catalogue item")
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody OfferingRequest request) {
        String slug = slugOf(request);
        if (offeringRepository.existsBySlug(slug)) {
            throw new IllegalArgumentException("A service with this name already exists: " + slug);
        }

        ServiceOffering offering = ServiceOffering.builder()
                .slug(slug)
                .title(request.getTitle().trim())
                .category(request.getCategory().trim())
                .icon(blankToNull(request.getIcon()))
                .price(blankToNull(request.getPrice()))
                .mediaUrl(blankToNull(request.getMediaUrl()))
                .mediaType(normaliseMediaType(request.getMediaType(), request.getMediaUrl()))
                .rating(request.getRating() == null ? 0.0 : request.getRating())
                .reviews(request.getReviews() == null ? 0 : request.getReviews())
                .aliases(blankToNull(request.getAliases()))
                .sortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder())
                .isActive(request.getActive() == null || request.getActive())
                .build();

        ServiceOffering saved = offeringRepository.save(offering);
        log.info("Admin created catalogue item: {}", saved.getSlug());
        return ResponseEntity.ok(Map.of("success", true, "message", "Service created",
                "data", catalogueService.toAdminOfferingMap(saved)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a catalogue item")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id,
                                                      @Valid @RequestBody OfferingRequest request) {
        ServiceOffering offering = offeringRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Service not found with id: " + id));

        // The slug is only recomputed when one was explicitly supplied. Renaming a service must not
        // silently change its URL and break links that are already out there; an admin who does want
        // the new URL can edit the slug field itself.
        if (request.getSlug() != null && !request.getSlug().isBlank()) {
            String slug = slugify(request.getSlug());
            if (!slug.equals(offering.getSlug()) && offeringRepository.existsBySlug(slug)) {
                throw new IllegalArgumentException("A service with this slug already exists: " + slug);
            }
            offering.setSlug(slug);
        }

        offering.setTitle(request.getTitle().trim());
        offering.setCategory(request.getCategory().trim());
        offering.setIcon(blankToNull(request.getIcon()));
        offering.setPrice(blankToNull(request.getPrice()));
        offering.setMediaUrl(blankToNull(request.getMediaUrl()));
        offering.setMediaType(normaliseMediaType(request.getMediaType(), request.getMediaUrl()));
        if (request.getRating() != null) offering.setRating(request.getRating());
        if (request.getReviews() != null) offering.setReviews(request.getReviews());
        offering.setAliases(blankToNull(request.getAliases()));
        if (request.getSortOrder() != null) offering.setSortOrder(request.getSortOrder());
        if (request.getActive() != null) offering.setIsActive(request.getActive());

        ServiceOffering saved = offeringRepository.save(offering);
        log.info("Admin updated catalogue item: {}", saved.getSlug());
        return ResponseEntity.ok(Map.of("success", true, "message", "Service updated",
                "data", catalogueService.toAdminOfferingMap(saved)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a catalogue item")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        ServiceOffering offering = offeringRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Service not found with id: " + id));
        offeringRepository.delete(offering);
        log.info("Admin deleted catalogue item: {}", offering.getSlug());
        return ResponseEntity.ok(Map.of("success", true, "message", "Service deleted"));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Enable or disable a catalogue item")
    public ResponseEntity<Map<String, Object>> toggleStatus(@PathVariable Long id) {
        ServiceOffering offering = offeringRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Service not found with id: " + id));
        offering.setIsActive(!Boolean.TRUE.equals(offering.getIsActive()));
        ServiceOffering saved = offeringRepository.save(offering);
        log.info("Admin set catalogue item {} active={}", saved.getSlug(), saved.getIsActive());
        return ResponseEntity.ok(Map.of("success", true,
                "message", "Service " + (saved.getIsActive() ? "enabled" : "disabled"),
                "data", catalogueService.toAdminOfferingMap(saved)));
    }

    /** Falls back to the title when no slug was given, which is how the seeded rows were built. */
    private String slugOf(OfferingRequest request) {
        String source = request.getSlug() != null && !request.getSlug().isBlank()
                ? request.getSlug() : request.getTitle();
        return slugify(source);
    }

    /** Mirrors the frontend's `slugify`, so a slug generated either side comes out the same. */
    static String slugify(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace("&", "and")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }

    /**
     * Keeps the pair coherent: a media type without a URL means nothing, and a URL with no type
     * given is guessed from its extension so an admin pasting a link does not have to classify it.
     */
    private String normaliseMediaType(String mediaType, String mediaUrl) {
        if (mediaUrl == null || mediaUrl.isBlank()) return null;
        if (mediaType != null && !mediaType.isBlank()) {
            String upper = mediaType.trim().toUpperCase(Locale.ROOT);
            if (upper.equals("IMAGE") || upper.equals("VIDEO") || upper.equals("ANIMATION")) return upper;
            throw new IllegalArgumentException("Media type must be IMAGE, VIDEO or ANIMATION");
        }
        String lower = mediaUrl.toLowerCase(Locale.ROOT);
        if (lower.matches(".*\\.(mp4|webm|mov|m4v)(\\?.*)?$")) return "VIDEO";
        if (lower.matches(".*\\.(gif|apng|json|lottie)(\\?.*)?$")) return "ANIMATION";
        return "IMAGE";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Data
    public static class OfferingRequest {
        @NotBlank(message = "Title is required")
        private String title;

        @NotBlank(message = "Category is required")
        private String category;

        /** Optional on create (derived from the title) and on update (keeps the existing URL). */
        private String slug;

        private String icon;
        private String price;

        /** Photo, video or animation shown on the card in place of the icon. */
        private String mediaUrl;
        /** IMAGE | VIDEO | ANIMATION. Inferred from the URL when left blank. */
        private String mediaType;

        @DecimalMin(value = "0.0", message = "Rating cannot be negative")
        @DecimalMax(value = "5.0", message = "Rating cannot exceed 5")
        private Double rating;

        @PositiveOrZero(message = "Review count cannot be negative")
        private Integer reviews;

        private String aliases;
        private Integer sortOrder;
        private Boolean active;
    }
}
