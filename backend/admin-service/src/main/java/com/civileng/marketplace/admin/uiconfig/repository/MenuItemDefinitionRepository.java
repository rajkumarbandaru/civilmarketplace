package com.civileng.marketplace.admin.uiconfig.repository;

import com.civileng.marketplace.admin.uiconfig.model.MenuItemDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MenuItemDefinitionRepository extends JpaRepository<MenuItemDefinition, Long> {

    List<MenuItemDefinition> findAllByOrderBySortOrderAsc();

    Optional<MenuItemDefinition> findByItemKey(String itemKey);
}
