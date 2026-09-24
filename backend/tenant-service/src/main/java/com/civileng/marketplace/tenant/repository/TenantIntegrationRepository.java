package com.civileng.marketplace.tenant.repository;

import com.civileng.marketplace.tenant.model.TenantIntegrationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TenantIntegrationRepository
        extends JpaRepository<TenantIntegrationEntity, TenantIntegrationEntity.Key> {

    List<TenantIntegrationEntity> findByIdTenantKeyOrderByIdCapabilityAsc(String tenantKey);
}
