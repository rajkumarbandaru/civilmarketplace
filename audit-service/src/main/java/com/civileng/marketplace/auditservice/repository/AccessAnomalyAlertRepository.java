package com.civileng.marketplace.auditservice.repository;

import com.civileng.marketplace.auditservice.model.AccessAnomalyAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface AccessAnomalyAlertRepository extends JpaRepository<AccessAnomalyAlert, Long> {

    Page<AccessAnomalyAlert> findByAcknowledgedFalseOrderByCreatedAtDesc(Pageable pageable);

    boolean existsByActorIdAndEntityTypeAndCreatedAtAfter(
            Long actorId, String entityType, LocalDateTime after);
}
