package com.civileng.marketplace.notification.repository;

import com.civileng.marketplace.notification.model.EmailLog;
import com.civileng.marketplace.notification.model.EmailStatus;
import com.civileng.marketplace.notification.model.NotificationChannel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {

    /**
     * The console's one list query. Every filter is optional and a null means "don't filter",
     * which keeps the three dropdowns and the search box on a single endpoint instead of a
     * combinatorial set of finder methods.
     */
    @Query("""
            SELECT l FROM EmailLog l
            WHERE (:status IS NULL OR l.status = :status)
              AND (:channel IS NULL OR l.channel = :channel)
              AND (:templateKey IS NULL OR l.templateKey = :templateKey)
              AND (:search IS NULL OR LOWER(l.recipient) LIKE LOWER(CONCAT('%', :search, '%'))
                                   OR LOWER(l.subject) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<EmailLog> search(@Param("status") EmailStatus status,
                          @Param("channel") NotificationChannel channel,
                          @Param("templateKey") String templateKey,
                          @Param("search") String search,
                          Pageable pageable);

    Optional<EmailLog> findFirstByProviderMessageIdOrderByIdDesc(String providerMessageId);

    /** Powers the status tiles above the list. */
    @Query("SELECT l.status, COUNT(l) FROM EmailLog l GROUP BY l.status")
    List<Object[]> countByStatus();

    /** Counts per channel, for the Type breakdown beside the status tiles. */
    @Query("SELECT l.channel, COUNT(l) FROM EmailLog l GROUP BY l.channel")
    List<Object[]> countByChannel();

    /** Template keys that actually appear in the log, for the filter dropdown. */
    @Query("SELECT DISTINCT l.templateKey FROM EmailLog l ORDER BY l.templateKey")
    List<String> findDistinctTemplateKeys();
}
