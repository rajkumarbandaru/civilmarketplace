package com.civileng.marketplace.messaging.repository;

import com.civileng.marketplace.messaging.model.MessageThread;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MessageThreadRepository extends JpaRepository<MessageThread, Long> {

    Optional<MessageThread> findByBookingId(Long bookingId);

    @Query("SELECT t FROM MessageThread t WHERE t.customerId = :userId OR t.workerId = :userId " +
           "ORDER BY t.lastMessageAt DESC NULLS LAST, t.createdAt DESC")
    Page<MessageThread> findByParticipant(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(CASE WHEN t.customerId = :userId THEN t.customerUnreadCount " +
           "ELSE t.workerUnreadCount END), 0) FROM MessageThread t " +
           "WHERE t.customerId = :userId OR t.workerId = :userId")
    long totalUnreadForUser(@Param("userId") Long userId);
}
