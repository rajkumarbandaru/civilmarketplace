package com.civileng.marketplace.user.repository;

import com.civileng.marketplace.user.model.WorkerPortfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkerPortfolioRepository extends JpaRepository<WorkerPortfolio, Long> {

    List<WorkerPortfolio> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserId(Long userId);
}
