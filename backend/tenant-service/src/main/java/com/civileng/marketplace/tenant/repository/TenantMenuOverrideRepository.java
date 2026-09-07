package com.civileng.marketplace.tenant.repository;

import com.civileng.marketplace.tenant.model.TenantMenuOverrideEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface TenantMenuOverrideRepository
        extends JpaRepository<TenantMenuOverrideEntity, TenantMenuOverrideEntity.Key> {

    List<TenantMenuOverrideEntity> findByIdTenantKeyOrderByIdItemKeyAsc(String tenantKey);

    @Transactional
    void deleteByIdTenantKey(String tenantKey);
}
