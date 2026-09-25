package com.civileng.marketplace.procurement.repository;

import com.civileng.marketplace.procurement.model.PriceList;
import com.civileng.marketplace.procurement.model.PriceListStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PriceListRepository extends JpaRepository<PriceList, Long> {

    /** Catalogues and contracts the given organizations are party to, as supplier or buyer. */
    @Query("select p from PriceList p where p.supplierOrgId in :orgs or p.buyerOrgId in :orgs order by p.id desc")
    List<PriceList> involving(@Param("orgs") Collection<Long> orgs);

    List<PriceList> findBySupplierOrgIdAndBuyerOrgIdAndStatus(Long supplierOrgId, Long buyerOrgId, PriceListStatus status);

    List<PriceList> findBySupplierOrgIdAndBuyerOrgIdIsNullAndStatus(Long supplierOrgId, PriceListStatus status);

    List<PriceList> findByBuyerOrgIdAndStatus(Long buyerOrgId, PriceListStatus status);
}
