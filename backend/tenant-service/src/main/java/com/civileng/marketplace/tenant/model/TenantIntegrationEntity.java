package com.civileng.marketplace.tenant.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * One tenant's account with one provider. The secrets column is ciphertext; see V5 for why.
 *
 * <p>Named …Entity to leave {@code TenantIntegration} to tenant-common's decrypted read shape.
 */
@Entity
@Table(name = "tenant_integrations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TenantIntegrationEntity {

    @EmbeddedId
    private Key id;

    /** {@code BYO} or {@code PLATFORM_SHARED}. */
    @Column(nullable = false, length = 20)
    private String mode;

    @Column(length = 40)
    private String provider;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "settings_json", columnDefinition = "TEXT")
    private String settingsJson;

    @ToString.Exclude
    @Column(name = "secrets_ciphertext", columnDefinition = "TEXT")
    private String secretsCiphertext;

    @Column(name = "secret_hints_json", columnDefinition = "TEXT")
    private String secretHintsJson;

    @ToString.Exclude
    @Column(name = "webhook_token", length = 64, unique = true)
    private String webhookToken;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public TenantIntegrationEntity(String tenantKey, String capability) {
        this.id = new Key(tenantKey, capability);
        this.enabled = true;
    }

    @Embeddable
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {

        @Column(name = "tenant_key", length = 31)
        private String tenantKey;

        /** {@code IntegrationCapability} name, e.g. {@code PAYMENT}. */
        @Column(length = 20)
        private String capability;
    }
}
