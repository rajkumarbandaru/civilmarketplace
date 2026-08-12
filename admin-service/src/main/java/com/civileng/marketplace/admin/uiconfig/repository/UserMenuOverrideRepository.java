package com.civileng.marketplace.admin.uiconfig.repository;

import com.civileng.marketplace.admin.uiconfig.model.UserMenuOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserMenuOverrideRepository extends JpaRepository<UserMenuOverride, Long> {

    List<UserMenuOverride> findByUserId(Long userId);

    Optional<UserMenuOverride> findByUserIdAndItemKey(Long userId, String itemKey);

    void deleteByUserId(Long userId);
}
