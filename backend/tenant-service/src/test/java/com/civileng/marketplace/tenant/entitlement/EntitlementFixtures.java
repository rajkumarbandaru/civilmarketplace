package com.civileng.marketplace.tenant.entitlement;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The three seeded plans and in-memory subscription/grant tables behind mocked repositories. */
class EntitlementFixtures {

    final PlanRepository plans = mock(PlanRepository.class);
    final TenantSubscriptionRepository subscriptions = mock(TenantSubscriptionRepository.class);
    final TenantGrantRepository grants = mock(TenantGrantRepository.class);
    final Map<String, TenantSubscription> subRows = new HashMap<>();
    final List<TenantGrant> grantRows = new ArrayList<>();

    static final Plan STARTER = new Plan(new Plan.Id("starter", 1), "Starter",
            "bookings,reviews,search", "{\"staff.seats\":5,\"bookings.monthly\":500}", "ACTIVE", null);
    static final Plan PROFESSIONAL = new Plan(new Plan.Id("professional", 1), "Professional",
            "bookings,reviews,search,projects", "{\"staff.seats\":50,\"bookings.monthly\":5000}", "ACTIVE", null);
    static final Plan ENTERPRISE = new Plan(new Plan.Id("enterprise", 1), "Enterprise",
            "bookings,reviews,search,projects,landrecords", "{}", "ACTIVE", null);

    EntitlementFixtures() {
        Map<Plan.Id, Plan> all = new HashMap<>();
        for (Plan p : List.of(STARTER, PROFESSIONAL, ENTERPRISE)) all.put(p.getId(), p);
        when(plans.findById(any())).thenAnswer(inv -> Optional.ofNullable(all.get(inv.<Plan.Id>getArgument(0))));
        when(plans.findFirstByIdPlanKeyAndStatusOrderByIdVersionDesc(anyString(), eq("ACTIVE"))).thenAnswer(inv ->
                all.values().stream().filter(p -> p.getId().getPlanKey().equals(inv.getArgument(0))).findFirst());
        when(plans.findByStatusOrderByIdPlanKeyAsc("ACTIVE")).thenReturn(List.of(ENTERPRISE, PROFESSIONAL, STARTER));
        when(subscriptions.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(subRows.get(inv.<String>getArgument(0))));
        when(subscriptions.save(any())).thenAnswer(inv -> {
            TenantSubscription s = inv.getArgument(0);
            subRows.put(s.getTenantKey(), s);
            return s;
        });
        when(grants.findByTenantKeyOrderByIdDesc(anyString())).thenAnswer(inv -> grantRows.stream()
                .filter(g -> g.getTenantKey().equals(inv.getArgument(0))).toList());
        when(grants.save(any())).thenAnswer(inv -> {
            TenantGrant g = inv.getArgument(0);
            if (g.getId() == null) {
                g.setId((long) grantRows.size() + 1);
                grantRows.add(g);
            }
            return g;
        });
        when(grants.findById(anyLong())).thenAnswer(inv -> grantRows.stream()
                .filter(g -> g.getId().equals(inv.getArgument(0))).findFirst());
    }

    void subscribe(String tenant, String plan, String addOns) {
        subRows.put(tenant, TenantSubscription.builder().tenantKey(tenant).planKey(plan).planVersion(1)
                .status(TenantSubscription.Status.ACTIVE).addOns(addOns).build());
    }
}
