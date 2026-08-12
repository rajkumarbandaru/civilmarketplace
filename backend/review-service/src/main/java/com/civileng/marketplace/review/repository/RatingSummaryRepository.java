package com.civileng.marketplace.review.repository;

import com.civileng.marketplace.review.model.RatingSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RatingSummaryRepository extends JpaRepository<RatingSummary, Long> {

    Optional<RatingSummary> findByUserId(Long userId);
}
