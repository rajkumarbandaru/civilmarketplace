package com.civileng.marketplace.admin.uiconfig.repository;

import com.civileng.marketplace.admin.uiconfig.model.TenantMenuOverrideRow;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantMenuOverrideRowRepository
        extends JpaRepository<TenantMenuOverrideRow, String> {
}
