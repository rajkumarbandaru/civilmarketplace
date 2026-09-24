package com.civileng.marketplace.tenant.service;

import com.civileng.marketplace.tenant.model.Tenant;
import com.civileng.marketplace.tenant.model.TenantStatus;
import com.civileng.marketplace.tenant.model.TenantStatusChange;
import com.civileng.marketplace.tenant.repository.TenantStatusChangeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.civileng.marketplace.tenant.model.TenantStatus.*;

/**
 * The tenant state machine (architecture 03 §4): the only place a tenant's status changes, and
 * every change is recorded with who made it and why.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TenantLifecycle {

    static final Map<TenantStatus, Set<TenantStatus>> ALLOWED = new EnumMap<>(Map.of(
            DRAFT, EnumSet.of(PROVISIONING),
            PROVISIONING, EnumSet.of(ACTIVE, PROVISIONING_FAILED),
            PROVISIONING_FAILED, EnumSet.of(PROVISIONING, DRAFT),
            ACTIVE, EnumSet.of(SUSPENDED, ARCHIVED),
            SUSPENDED, EnumSet.of(ACTIVE, ARCHIVED),
            ARCHIVED, EnumSet.of(ACTIVE)));

    /** What an operator may pick by hand; the rest belong to the publish and provisioning flow. */
    public static final Set<TenantStatus> OPERATOR_SETTABLE = EnumSet.of(ACTIVE, SUSPENDED, ARCHIVED);

    private final TenantStatusChangeRepository history;

    public static boolean allowed(TenantStatus from, TenantStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public void transition(Tenant tenant, TenantStatus to, String actor, String reason) {
        TenantStatus from = tenant.getStatus();
        if (from == to) return;
        if (!allowed(from, to)) {
            throw new IllegalArgumentException("A " + from + " tenant cannot become " + to);
        }
        if ("platform".equals(tenant.getTenantKey()) && to != ACTIVE) {
            // Suspending the operator tenant would lock every admin out of the console that is
            // the only way to un-suspend it.
            throw new IllegalArgumentException("The operator tenant cannot be suspended");
        }
        tenant.setStatus(to);
        history.save(TenantStatusChange.builder().tenantKey(tenant.getTenantKey())
                .fromStatus(from == null ? null : from.name()).toStatus(to.name())
                .actor(actor).reason(reason).build());
        log.info("Tenant '{}' {} -> {} by {}{}", tenant.getTenantKey(), from, to, actor,
                reason == null ? "" : " (" + reason + ")");
    }

    /** First status of a new tenant, recorded as history too. */
    public void born(Tenant tenant, String actor) {
        tenant.setStatus(DRAFT);
        history.save(TenantStatusChange.builder().tenantKey(tenant.getTenantKey())
                .toStatus(DRAFT.name()).actor(actor).reason("Created from the wizard").build());
    }
}
