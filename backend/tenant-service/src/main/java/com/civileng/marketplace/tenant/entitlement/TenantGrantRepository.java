package com.civileng.marketplace.tenant.entitlement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface TenantGrantRepository extends JpaRepository<TenantGrant, Long> {

    List<TenantGrant> findByTenantKeyOrderByIdDesc(String tenantKey);

    /** Grants that have lapsed but whose tenant has not been told yet. */
    List<TenantGrant> findByExpiresAtBeforeAndExpiryAnnouncedFalseAndRevokedAtIsNull(LocalDateTime now);
}
