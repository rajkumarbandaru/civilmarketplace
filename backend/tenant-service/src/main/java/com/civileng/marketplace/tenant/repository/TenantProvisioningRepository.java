package com.civileng.marketplace.tenant.repository;

import com.civileng.marketplace.tenant.model.TenantProvisioning;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TenantProvisioningRepository extends JpaRepository<TenantProvisioning, String> {
    List<TenantProvisioning> findByStepIn(Collection<TenantProvisioning.Step> steps);
}
