package com.civileng.marketplace.tenant.model;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/** One service's report that it has provisioned (or failed to provision) a tenant. */
@Entity
@Table(name = "tenant_service_acks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TenantServiceAck {

    @EmbeddedId
    private Key id;

    @Column(nullable = false)
    private boolean ok;

    @Column(length = 1000)
    private String error;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Embeddable
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        @Column(name = "tenant_key", length = 31)
        private String tenantKey;
        @Column(length = 60)
        private String service;
    }
}
