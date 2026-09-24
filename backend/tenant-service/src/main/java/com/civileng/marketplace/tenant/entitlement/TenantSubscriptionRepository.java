package com.civileng.marketplace.tenant.entitlement;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantSubscriptionRepository extends JpaRepository<TenantSubscription, String> {
}
