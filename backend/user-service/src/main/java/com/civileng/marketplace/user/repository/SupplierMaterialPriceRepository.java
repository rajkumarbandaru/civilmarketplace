package com.civileng.marketplace.user.repository;

import com.civileng.marketplace.user.model.SupplierMaterialPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SupplierMaterialPriceRepository extends JpaRepository<SupplierMaterialPrice, Long> {

    List<SupplierMaterialPrice> findBySupplierUserIdOrderByUpdatedAtDesc(Long supplierUserId);

    Optional<SupplierMaterialPrice> findByIdAndSupplierUserId(Long id, Long supplierUserId);

    boolean existsBySupplierUserIdAndMaterialItemIdAndCity(Long supplierUserId, Long materialItemId, String city);

    /**
     * Every rate the estimator is allowed to quote: active, not expired, and — when a city is
     * given — local to it. Expired rows are dropped here rather than filtered later, so no caller
     * can accidentally range over a stale price.
     */
    @Query("""
            SELECT p FROM SupplierMaterialPrice p
            JOIN FETCH p.materialItem m
            WHERE p.isActive = true
              AND m.isActive = true
              AND (p.validUntil IS NULL OR p.validUntil > :now)
              AND (:city IS NULL OR LOWER(p.city) = LOWER(:city))
            ORDER BY m.category ASC, m.name ASC, p.price ASC
            """)
    List<SupplierMaterialPrice> findQuotableRates(@Param("city") String city,
                                                  @Param("now") LocalDateTime now);

    long countBySupplierUserId(Long supplierUserId);
}
