package com.civileng.marketplace.admin.uiconfig.repository;

import com.civileng.marketplace.admin.uiconfig.model.UserAppearance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Keyed by user id, which is the entity's own primary key — so {@code findById} is the lookup. */
@Repository
public interface UserAppearanceRepository extends JpaRepository<UserAppearance, Long> {
}
