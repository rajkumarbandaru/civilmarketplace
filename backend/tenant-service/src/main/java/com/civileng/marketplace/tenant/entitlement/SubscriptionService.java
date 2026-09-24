package com.civileng.marketplace.tenant.entitlement;

import com.civileng.marketplace.tenant.model.Tenant;
import com.civileng.marketplace.tenant.model.TenantStatus;
import com.civileng.marketplace.tenant.service.TenantLifecycle;
import com.civileng.marketplace.tenant.service.TenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Changing what a tenant is entitled to: plan and add-ons, subscription status, grants. Every
 * change re-announces the tenant, so the gateway and every service see its new running modules.
 * Nothing is ever deleted when entitlement is lost — the modules stop running, their data stays.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SubscriptionService {

    static final int MAX_GRANT_MONTHS = 12;

    private final EntitlementService entitlements;
    private final TenantSubscriptionRepository subscriptions;
    private final TenantGrantRepository grants;
    private final TenantService tenantService;
    private final TenantLifecycle lifecycle;
    private final Clock clock;

    public record Impact(String fromPlan, String toPlan, List<String> modulesStopping, List<String> modulesResuming,
                         Map<String, Long[]> limitChanges) { }

    /** What changing plan or add-ons would do, before doing it. */
    @Transactional(readOnly = true)
    public Impact preview(String tenantKey, String planKey, List<String> addOns) {
        Tenant tenant = tenantService.byKey(tenantKey);
        EntitlementService.Entitlements now = entitlements.of(tenantKey);
        EntitlementService.Entitlements next = entitlements.hypothetical(tenantKey, planKey, validAddOns(addOns));
        Set<String> runningNow = EntitlementService.running(tenant.moduleKeys(), now);
        Set<String> runningNext = EntitlementService.running(tenant.moduleKeys(), next);
        List<String> stopping = runningNow.stream().filter(m -> !runningNext.contains(m)).sorted().toList();
        List<String> resuming = runningNext.stream().filter(m -> !runningNow.contains(m)).sorted().toList();
        Map<String, Long[]> limits = new TreeMap<>();
        for (String limit : FeatureCatalog.LIMITS.keySet()) {
            Long a = now.limits().get(limit), b = next.limits().get(limit);
            if (!Objects.equals(a, b)) limits.put(limit, new Long[]{a, b});
        }
        return new Impact(now.planName(), next.planName(), stopping, resuming, limits);
    }

    @Transactional
    public EntitlementService.Entitlements change(String tenantKey, String planKey, List<String> addOns, String actor) {
        Tenant tenant = tenantService.byKey(tenantKey);
        if ("platform".equals(tenantKey)) throw new IllegalArgumentException("The operator tenant has no plan");
        Plan plan = entitlements.latest(planKey);
        TenantSubscription sub = subscriptions.findById(tenantKey).orElseThrow();
        String before = sub.getPlanKey() + " v" + sub.getPlanVersion() + " +" + sub.getAddOns();
        sub.setPlanKey(plan.getId().getPlanKey());
        sub.setPlanVersion(plan.getId().getVersion());
        sub.setAddOns(String.join(",", validAddOns(addOns)));
        sub.setUpdatedBy(actor);
        subscriptions.save(sub);
        tenantService.announce(tenant, false);
        log.info("Tenant '{}' subscription {} -> {} v{} +{} by {}", tenantKey, before, plan.getId().getPlanKey(),
                plan.getId().getVersion(), sub.getAddOns(), actor);
        return entitlements.of(tenantKey);
    }

    /**
     * Subscription standing. SUSPENDED (non-payment past grace) suspends the tenant too; PAST_DUE
     * only warns. Reinstating a suspended tenant stays an operator's deliberate act.
     */
    @Transactional
    public EntitlementService.Entitlements setStatus(String tenantKey, TenantSubscription.Status status, String actor) {
        TenantSubscription sub = subscriptions.findById(tenantKey).orElseThrow(() -> new NoSuchElementException("No subscription"));
        sub.setStatus(status);
        sub.setUpdatedBy(actor);
        subscriptions.save(sub);
        if (status == TenantSubscription.Status.SUSPENDED || status == TenantSubscription.Status.CANCELED) {
            Tenant tenant = tenantService.byKey(tenantKey);
            if (tenant.getStatus() == TenantStatus.ACTIVE) {
                lifecycle.transition(tenant, TenantStatus.SUSPENDED, actor, "Subscription " + status);
                tenantService.announce(tenant, false);
            }
        }
        return entitlements.of(tenantKey);
    }

    @Transactional
    public EntitlementService.Entitlements grant(String tenantKey, String feature, Long limitValue,
                                                  LocalDateTime expiresAt, String reason, String actor) {
        Tenant tenant = tenantService.byKey(tenantKey);
        LocalDateTime now = LocalDateTime.now(clock);
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("A grant needs a reason");
        if (expiresAt == null || !expiresAt.isAfter(now)) throw new IllegalArgumentException("A grant needs an expiry in the future");
        if (expiresAt.isAfter(now.plusMonths(MAX_GRANT_MONTHS))) {
            throw new IllegalArgumentException("A grant can last at most " + MAX_GRANT_MONTHS + " months");
        }
        boolean isLimit = FeatureCatalog.isLimit(feature);
        if (isLimit && (limitValue == null || limitValue < 0)) throw new IllegalArgumentException("A limit grant needs a value");
        if (!isLimit) {
            if (limitValue != null) throw new IllegalArgumentException(feature + " is a feature, not a limit");
            if (FeatureCatalog.OPERATOR_ONLY.equals(feature) || !knownModuleFeature(feature)) {
                throw new IllegalArgumentException("'" + feature + "' cannot be granted");
            }
        }
        grants.save(TenantGrant.builder().tenantKey(tenantKey).feature(feature).limitValue(isLimit ? limitValue : null)
                .expiresAt(expiresAt).reason(reason.trim()).grantedBy(actor).build());
        tenantService.announce(tenant, false);
        log.info("Grant on '{}': {}{} until {} by {} ({})", tenantKey, feature, isLimit ? "=" + limitValue : "",
                expiresAt, actor, reason);
        return entitlements.of(tenantKey);
    }

    @Transactional
    public EntitlementService.Entitlements revokeGrant(String tenantKey, Long grantId, String actor) {
        TenantGrant g = grants.findById(grantId).filter(x -> x.getTenantKey().equals(tenantKey))
                .orElseThrow(() -> new NoSuchElementException("No such grant"));
        if (g.getRevokedAt() == null) {
            g.setRevokedAt(LocalDateTime.now(clock));
            g.setRevokedBy(actor);
            grants.save(g);
            tenantService.announce(tenantService.byKey(tenantKey), false);
        }
        return entitlements.of(tenantKey);
    }

    /** A lapsed grant takes its feature away: re-announce the tenant once it has expired. */
    @Scheduled(fixedDelayString = "${platform.entitlements.grant-sweep-ms:60000}", initialDelay = 30_000)
    @Transactional
    public void announceExpiredGrants() {
        for (TenantGrant g : grants.findByExpiresAtBeforeAndExpiryAnnouncedFalseAndRevokedAtIsNull(LocalDateTime.now(clock))) {
            g.setExpiryAnnounced(true);
            grants.save(g);
            try {
                tenantService.announce(tenantService.byKey(g.getTenantKey()), false);
                log.info("Grant {} ({}) for '{}' expired", g.getId(), g.getFeature(), g.getTenantKey());
            } catch (RuntimeException e) {
                log.warn("Could not re-announce '{}' after grant {} expired", g.getTenantKey(), g.getId(), e);
            }
        }
    }

    private static boolean knownModuleFeature(String feature) {
        for (var m : com.civileng.marketplace.tenant.model.PlatformModule.values()) {
            if (m.key().equals(feature)) return !FeatureCatalog.BASE.contains(feature);
        }
        return false;
    }

    private static List<String> validAddOns(List<String> addOns) {
        if (addOns == null) return List.of();
        List<String> out = new ArrayList<>();
        for (String a : addOns) {
            if (a == null || a.isBlank()) continue;
            if (!FeatureCatalog.ADD_ONS.containsKey(a.trim())) throw new IllegalArgumentException("Unknown add-on '" + a + "'");
            if (!out.contains(a.trim())) out.add(a.trim());
        }
        return out;
    }
}
