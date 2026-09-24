package com.civileng.marketplace.tenant.repository;

import com.civileng.marketplace.tenant.model.TenantStatusChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TenantStatusChangeRepository extends JpaRepository<TenantStatusChange, Long> {
    List<TenantStatusChange> findByTenantKeyOrderByIdDesc(String tenantKey);
}
