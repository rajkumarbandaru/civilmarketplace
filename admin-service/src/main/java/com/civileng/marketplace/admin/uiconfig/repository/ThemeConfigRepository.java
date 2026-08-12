package com.civileng.marketplace.admin.uiconfig.repository;

import com.civileng.marketplace.admin.uiconfig.model.ThemeConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Keyed by {@code scopeKey} — {@code PLATFORM} or a role name. */
@Repository
public interface ThemeConfigRepository extends JpaRepository<ThemeConfig, String> {
}
