package com.civileng.marketplace.tenant.repository;

import com.civileng.marketplace.tenant.model.TenantServiceAck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TenantServiceAckRepository extends JpaRepository<TenantServiceAck, TenantServiceAck.Key> {
    List<TenantServiceAck> findByIdTenantKey(String tenantKey);

    @Modifying
    @Query("delete from TenantServiceAck a where a.id.tenantKey = ?1")
    void deleteByTenantKey(String tenantKey);
}
