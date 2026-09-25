package com.civileng.marketplace.tenant.placement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TenantMoveAckRepository extends JpaRepository<TenantMoveAck, TenantMoveAck.Key> {

    List<TenantMoveAck> findByIdMoveId(Long moveId);
}
