package com.civileng.marketplace.auditservice.repository;

import com.civileng.marketplace.auditservice.model.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    @Query("""
            SELECT a FROM AuditEvent a WHERE
              (:actorId IS NULL OR a.actorId = :actorId) AND
              (:subjectUserId IS NULL OR a.subjectUserId = :subjectUserId) AND
              (:entityType IS NULL OR a.entityType = :entityType) AND
              (:action IS NULL OR a.action = :action) AND
              (:from IS NULL OR a.occurredAt >= :from) AND
              (:to IS NULL OR a.occurredAt <= :to)
            ORDER BY a.id DESC
            """)
    Page<AuditEvent> search(@Param("actorId") Long actorId,
                            @Param("subjectUserId") Long subjectUserId,
                            @Param("entityType") String entityType,
                            @Param("action") String action,
                            @Param("from") Instant from,
                            @Param("to") Instant to,
                            Pageable pageable);

    List<AuditEvent> findBySubjectUserIdOrderByIdAsc(Long subjectUserId);

    AuditEvent findTopByOrderByIdDesc();

    List<AuditEvent> findTop2000ByOrderByIdAsc();

    @Query("""
            SELECT COALESCE(SUM(COALESCE(a.recordCount, 1)), 0) FROM AuditEvent a
            WHERE a.actorId = :actorId AND a.entityType = :entityType
              AND a.action = 'READ' AND a.occurredAt >= :since
            """)
    long countRecordsReadSince(@Param("actorId") Long actorId,
                               @Param("entityType") String entityType,
                               @Param("since") Instant since);
}
