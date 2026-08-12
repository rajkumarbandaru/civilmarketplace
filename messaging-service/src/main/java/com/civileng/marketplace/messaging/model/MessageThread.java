package com.civileng.marketplace.messaging.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "message_threads", indexes = {
        @Index(name = "idx_thread_customer", columnList = "customer_id"),
        @Index(name = "idx_thread_worker", columnList = "worker_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageThread {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false, unique = true)
    private Long bookingId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "worker_id", nullable = false)
    private Long workerId;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @Column(name = "last_message_preview", length = 280)
    private String lastMessagePreview;

    @Column(name = "customer_unread_count", nullable = false)
    @Builder.Default
    private Integer customerUnreadCount = 0;

    @Column(name = "worker_unread_count", nullable = false)
    @Builder.Default
    private Integer workerUnreadCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public boolean involves(Long userId) {
        return userId != null && (userId.equals(customerId) || userId.equals(workerId));
    }

    public boolean isCustomer(Long userId) {
        return customerId.equals(userId);
    }
}
