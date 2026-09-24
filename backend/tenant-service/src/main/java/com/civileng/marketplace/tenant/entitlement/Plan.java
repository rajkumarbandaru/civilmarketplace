package com.civileng.marketplace.tenant.entitlement;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/** One immutable version of a plan: its features and its limits (JSON, absent = unlimited). */
@Entity
@Table(name = "plans")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Plan {

    @EmbeddedId
    private Id id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String features;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String limits;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Embeddable
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Id implements Serializable {
        @Column(name = "plan_key", length = 40)
        private String planKey;
        private int version;
    }
}
