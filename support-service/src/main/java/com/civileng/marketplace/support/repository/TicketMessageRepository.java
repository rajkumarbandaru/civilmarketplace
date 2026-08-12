package com.civileng.marketplace.support.repository;

import com.civileng.marketplace.support.model.TicketMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketMessageRepository extends JpaRepository<TicketMessage, Long> {

    List<TicketMessage> findByTicketIdOrderByIdAsc(Long ticketId);
}
