package com.civileng.marketplace.tenant.placement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TenantMoveRepository extends JpaRepository<TenantMove, Long> {

    List<TenantMove> findByStepIn(Collection<TenantMove.Step> steps);

    List<TenantMove> findByTenantKeyOrderByIdDesc(String tenantKey);
}
