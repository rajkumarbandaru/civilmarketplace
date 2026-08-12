package com.civileng.marketplace.support.repository;

import com.civileng.marketplace.support.model.SupportTicket;
import com.civileng.marketplace.support.model.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    Page<SupportTicket> findByReporterIdOrderByCreatedAtDesc(Long reporterId, Pageable pageable);

    Page<SupportTicket> findByReporterIdAndStatusOrderByCreatedAtDesc(
            Long reporterId, TicketStatus status, Pageable pageable);

    Page<SupportTicket> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<SupportTicket> findByStatusOrderByCreatedAtDesc(TicketStatus status, Pageable pageable);
}
