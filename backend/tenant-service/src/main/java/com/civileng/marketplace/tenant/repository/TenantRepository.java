package com.civileng.marketplace.tenant.repository;

import com.civileng.marketplace.tenant.model.Tenant;
import com.civileng.marketplace.tenant.model.TenantStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findByTenantKey(String tenantKey);

    Optional<Tenant> findBySubdomain(String subdomain);

    Optional<Tenant> findByCustomDomain(String customDomain);

    List<Tenant> findByStatusOrderByTenantKey(TenantStatus status);

    boolean existsByTenantKey(String tenantKey);

    boolean existsBySubdomain(String subdomain);
}
