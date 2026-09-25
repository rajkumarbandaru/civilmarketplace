package com.civileng.marketplace.procurement.repository;

import com.civileng.marketplace.procurement.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    @Query("select p from PurchaseOrder p where p.buyerOrgId in :orgs or p.supplierOrgId in :orgs order by p.id desc")
    List<PurchaseOrder> visibleTo(@Param("orgs") Collection<Long> orgs);

    Optional<PurchaseOrder> findByRfqId(Long rfqId);

    List<PurchaseOrder> findByBuyerOrgIdAndSupplierOrgId(Long buyerOrgId, Long supplierOrgId);
}
