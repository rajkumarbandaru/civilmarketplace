package com.civileng.marketplace.admin.settings.repository;

import com.civileng.marketplace.admin.settings.model.PlatformSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformSettingRepository extends JpaRepository<PlatformSetting, String> {
}
