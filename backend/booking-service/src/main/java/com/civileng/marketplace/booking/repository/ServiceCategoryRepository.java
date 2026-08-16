package com.civileng.marketplace.booking.repository;

import com.civileng.marketplace.booking.model.ServiceCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceCategoryRepository extends JpaRepository<ServiceCategory, Long> {

    Optional<ServiceCategory> findBySlug(String slug);

    List<ServiceCategory> findByParentIsNullAndIsActiveTrueOrderBySortOrder();

    List<ServiceCategory> findByParentIdAndIsActiveTrueOrderBySortOrder(Long parentId);

    boolean existsBySlug(String slug);

    /** The admin list: alphabetical, so a category is found by name rather than by scanning. */
    List<ServiceCategory> findAllByOrderByNameAsc();
}
