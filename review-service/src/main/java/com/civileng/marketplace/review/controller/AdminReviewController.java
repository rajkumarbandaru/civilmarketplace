package com.civileng.marketplace.review.controller;

import com.civileng.marketplace.review.exception.AccessDeniedException;
import com.civileng.marketplace.review.model.Review;
import com.civileng.marketplace.review.repository.ReviewRepository;
import com.civileng.marketplace.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin/reviews")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Review Moderation", description = "Admin endpoints for review moderation")
public class AdminReviewController {

    private static final Set<String> ADMIN_ROLES =
            Set.of("SUPER_ADMIN", "ADMIN", "SUB_ADMIN", "REGIONAL_ADMIN");

    private final ReviewService reviewService;
    private final ReviewRepository reviewRepository;

    @GetMapping
    @Operation(summary = "List all reviews")
    public ResponseEntity<Page<Review>> getAll(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAdmin(role);
        return ResponseEntity.ok(reviewRepository.findByIsHiddenFalseOrderByCreatedAtDesc(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @DeleteMapping("/{reviewId}")
    @Operation(summary = "Hide a review with an audit reason")
    public ResponseEntity<Review> hide(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable Long reviewId,
            @RequestBody Map<String, String> body) {
        requireAdmin(role);
        return ResponseEntity.ok(reviewService.moderate(reviewId, true,
                body.getOrDefault("reason", "Not specified")));
    }

    @PutMapping("/{reviewId}/restore")
    @Operation(summary = "Restore a previously hidden review")
    public ResponseEntity<Review> restore(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable Long reviewId) {
        requireAdmin(role);
        return ResponseEntity.ok(reviewService.moderate(reviewId, false, null));
    }

    private void requireAdmin(String role) {
        if (role == null || !ADMIN_ROLES.contains(role)) {
            throw new AccessDeniedException("Admin role required");
        }
    }
}
