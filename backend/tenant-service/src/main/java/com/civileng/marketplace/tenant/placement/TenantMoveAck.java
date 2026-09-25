package com.civileng.marketplace.tenant.placement;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/** A service's acknowledgement that it routes the moved tenant to the new cluster. */
@Entity
@Table(name = "tenant_move_acks")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TenantMoveAck {

    @EmbeddedId
    private Key id;

    @Column(nullable = false)
    private long epoch;

    @Column(name = "acked_at", nullable = false)
    private LocalDateTime ackedAt;

    @Embeddable
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        @Column(name = "move_id")
        private Long moveId;
        @Column(name = "service", length = 60)
        private String service;

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && k.moveId.equals(moveId) && k.service.equals(service);
        }

        @Override
        public int hashCode() {
            return moveId.hashCode() * 31 + service.hashCode();
        }
    }
}
