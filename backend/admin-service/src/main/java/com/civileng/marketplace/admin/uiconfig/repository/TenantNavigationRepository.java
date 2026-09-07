package com.civileng.marketplace.admin.uiconfig.repository;

import com.civileng.marketplace.admin.uiconfig.model.TenantNavigation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantNavigationRepository extends JpaRepository<TenantNavigation, String> {
}
