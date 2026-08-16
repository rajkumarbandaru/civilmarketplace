package com.civileng.marketplace.user.repository;

import com.civileng.marketplace.user.model.MaterialItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MaterialItemRepository extends JpaRepository<MaterialItem, Long> {

    Optional<MaterialItem> findBySlug(String slug);

    List<MaterialItem> findByIsActiveTrueOrderByCategoryAscNameAsc();

    List<MaterialItem> findByCategoryAndIsActiveTrueOrderByNameAsc(String category);
}
