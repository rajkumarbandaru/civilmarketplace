package com.civileng.marketplace.booking.repository;

import com.civileng.marketplace.booking.model.ServiceOffering;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, Long> {

    Optional<ServiceOffering> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<ServiceOffering> findByIsActiveTrueOrderByTitleAsc();

    List<ServiceOffering> findAllByOrderByTitleAsc();

    long countByCategoryAndIsActiveTrue(String category);

    long countByCategory(String category);

    /**
     * Follows a category rename across the offerings that carry its old name. Done in one statement
     * rather than by loading every row: a rename touches up to a hundred offerings and none of them
     * need any other field looked at.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ServiceOffering o set o.category = :newName where o.category = :oldName")
    int renameCategory(@Param("oldName") String oldName, @Param("newName") String newName);
}
