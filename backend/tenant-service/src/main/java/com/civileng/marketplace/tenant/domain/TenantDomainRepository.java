package com.civileng.marketplace.tenant.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TenantDomainRepository extends JpaRepository<TenantDomain, Long> {

    List<TenantDomain> findByTenantKeyAndStatusNotOrderByIdAsc(String tenantKey, TenantDomain.Status status);

    List<TenantDomain> findByStatusIn(Collection<TenantDomain.Status> statuses);

    Optional<TenantDomain> findFirstByHostAndStatusIn(String host, Collection<TenantDomain.Status> statuses);

    boolean existsByHostAndStatusNot(String host, TenantDomain.Status status);
}
