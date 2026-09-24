package com.civileng.marketplace.procurement.repository;

import com.civileng.marketplace.procurement.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RfqRepository extends JpaRepository<Rfq, Long> {

    @Query("select distinct r from Rfq r left join r.invitedSupplierIds s "
            + "where r.buyerOrgId in :orgs or s in :orgs order by r.id desc")
    List<Rfq> visibleTo(@Param("orgs") Collection<Long> orgs);
}
