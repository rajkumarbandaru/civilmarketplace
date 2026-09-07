package com.civileng.marketplace.admin.uiconfig.repository;

import com.civileng.marketplace.admin.uiconfig.model.TenantModule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantModuleRepository extends JpaRepository<TenantModule, String> {
}
