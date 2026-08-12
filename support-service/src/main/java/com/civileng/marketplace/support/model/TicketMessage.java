package com.civileng.marketplace.support.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ticket_messages", indexes = {
        @Index(name = "idx_ticket_message_ticket", columnList = "ticket_id, id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @NotBlank
    @Column(name = "body", nullable = false, length = 4000)
    private String body;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
