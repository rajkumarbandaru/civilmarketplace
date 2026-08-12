package com.civileng.marketplace.review.controller;

import com.civileng.marketplace.review.dto.SubmitReviewRequest;
import com.civileng.marketplace.review.model.RatingSummary;
import com.civileng.marketplace.review.model.Review;
import com.civileng.marketplace.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@Tag(name = "Reviews", description = "Ratings and reviews APIs")
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/api/v1/bookings/{bookingId}/reviews")
    @Operation(summary = "Submit a review for a completed booking")
    public ResponseEntity<Review> submitReview(
            @RequestHeader("X-User-Id") Long reviewerId,
            @PathVariable Long bookingId,
            @Valid @RequestBody SubmitReviewRequest request) {
        request.setBookingId(bookingId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reviewService.submitReview(reviewerId, request));
    }

    @GetMapping("/api/v1/profiles/{userId}/reviews")
    @Operation(summary = "Get reviews for a profile")
    public ResponseEntity<Page<Review>> getReviews(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(reviewService.getReviewsForUser(userId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @GetMapping("/api/v1/profiles/{userId}/rating-summary")
    @Operation(summary = "Get rating summary for a profile")
    public ResponseEntity<RatingSummary> getSummary(@PathVariable Long userId) {
        return ResponseEntity.ok(reviewService.getSummary(userId));
    }

    @PostMapping("/api/v1/reviews/{reviewId}/response")
    @Operation(summary = "Respond to a review (reviewed party only)")
    public ResponseEntity<Review> respond(
            @RequestHeader("X-User-Id") Long responderId,
            @PathVariable Long reviewId,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(reviewService.respond(reviewId, responderId, body.get("responseText")));
    }

    @GetMapping("/api/v1/reviews/health")
    @Operation(summary = "Health check")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "service", "review-service",
                "status", "UP",
                "timestamp", System.currentTimeMillis()
        ));
    }
}
