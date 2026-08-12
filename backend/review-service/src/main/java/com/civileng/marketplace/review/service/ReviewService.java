package com.civileng.marketplace.review.service;

import com.civileng.marketplace.review.client.BookingDto;
import com.civileng.marketplace.review.client.BookingServiceClient;
import com.civileng.marketplace.review.dto.SubmitReviewRequest;
import com.civileng.marketplace.review.model.RatingSummary;
import com.civileng.marketplace.review.model.Review;
import com.civileng.marketplace.review.repository.RatingSummaryRepository;
import com.civileng.marketplace.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final RatingSummaryRepository ratingSummaryRepository;
    private final BookingServiceClient bookingServiceClient;

    @Transactional
    public Review submitReview(Long reviewerId, SubmitReviewRequest request) {
        if (reviewRepository.existsByBookingIdAndReviewerId(request.getBookingId(), reviewerId)) {
            throw new IllegalArgumentException("You have already reviewed this booking");
        }

        BookingDto booking = bookingServiceClient.getBooking(request.getBookingId());
        if (booking == null) {
            throw new IllegalArgumentException("Booking not found or booking-service unavailable");
        }
        if (!booking.isCompleted()) {
            throw new IllegalArgumentException("Only completed bookings can be reviewed");
        }
        if (!booking.involves(reviewerId)) {
            throw new IllegalArgumentException("You are not a party to this booking");
        }

        Long revieweeId = booking.counterpartyOf(reviewerId);
        if (revieweeId == null) {
            throw new IllegalArgumentException("Booking has no counterparty to review yet");
        }

        Review review = Review.builder()
                .bookingId(request.getBookingId())
                .reviewerId(reviewerId)
                .revieweeId(revieweeId)
                .rating(request.getRating())
                .comment(request.getComment())
                .categoryTags(request.getCategoryTags())
                .build();
        Review saved = reviewRepository.save(review);

        recomputeSummary(revieweeId);
        log.info("Review {} submitted by {} for {} (booking {})",
                saved.getId(), reviewerId, revieweeId, request.getBookingId());
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<Review> getReviewsForUser(Long userId, Pageable pageable) {
        return reviewRepository.findByRevieweeIdAndIsHiddenFalseOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public RatingSummary getSummary(Long userId) {
        return ratingSummaryRepository.findByUserId(userId)
                .orElse(RatingSummary.builder()
                        .userId(userId)
                        .averageRating(BigDecimal.ZERO)
                        .totalReviews(0)
                        .build());
    }

    @Transactional
    public Review respond(Long reviewId, Long responderId, String responseText) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found"));
        if (!review.getRevieweeId().equals(responderId)) {
            throw new IllegalArgumentException("Only the reviewed party may respond");
        }
        if (review.getResponseText() != null) {
            throw new IllegalArgumentException("A response has already been submitted for this review");
        }
        review.setResponseText(responseText);
        review.setResponseAt(LocalDateTime.now());
        return reviewRepository.save(review);
    }

    @Transactional
    public Review moderate(Long reviewId, boolean hide, String reason) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found"));
        review.setIsHidden(hide);
        review.setHiddenReason(hide ? reason : null);
        Review saved = reviewRepository.save(review);
        recomputeSummary(review.getRevieweeId());
        log.info("Review {} {} by admin: {}", reviewId, hide ? "hidden" : "unhidden", reason);
        return saved;
    }

    private void recomputeSummary(Long userId) {
        List<Review> visible = reviewRepository.findByRevieweeIdAndIsHiddenFalse(userId);
        RatingSummary summary = ratingSummaryRepository.findByUserId(userId)
                .orElse(RatingSummary.builder().userId(userId).build());

        if (visible.isEmpty()) {
            summary.setAverageRating(BigDecimal.ZERO);
            summary.setTotalReviews(0);
        } else {
            double avg = visible.stream().mapToInt(Review::getRating).average().orElse(0.0);
            summary.setAverageRating(BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
            summary.setTotalReviews(visible.size());
        }
        ratingSummaryRepository.save(summary);
    }
}
