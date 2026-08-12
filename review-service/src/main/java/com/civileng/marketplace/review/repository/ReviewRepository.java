package com.civileng.marketplace.review.repository;

import com.civileng.marketplace.review.model.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    Page<Review> findByRevieweeIdAndIsHiddenFalseOrderByCreatedAtDesc(Long revieweeId, Pageable pageable);

    List<Review> findByRevieweeIdAndIsHiddenFalse(Long revieweeId);

    boolean existsByBookingIdAndReviewerId(Long bookingId, Long reviewerId);

    Optional<Review> findByIdAndReviewerId(Long id, Long reviewerId);

    Page<Review> findByIsHiddenFalseOrderByCreatedAtDesc(Pageable pageable);
}
