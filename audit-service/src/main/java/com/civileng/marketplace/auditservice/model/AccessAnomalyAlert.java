package com.civileng.marketplace.auditservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "access_anomaly_alerts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessAnomalyAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "entity_type", nullable = false, length = 80)
    private String entityType;

    @Column(name = "records_accessed", nullable = false)
    private Integer recordsAccessed;

    @Column(name = "window_minutes", nullable = false)
    private Integer windowMinutes;

    @Column(name = "detail", length = 500)
    private String detail;

    @Column(name = "acknowledged", nullable = false)
    @Builder.Default
    private Boolean acknowledged = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
