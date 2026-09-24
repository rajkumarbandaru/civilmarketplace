package com.civileng.marketplace.tenant.entitlement;

import com.civileng.marketplace.tenant.model.Tenant;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

/**
 * The entitlement evaluator (architecture 08 §4): what a tenant MAY use, derived from its plan
 * version, add-ons and unexpired grants — never stored, always computed, so there is one answer.
 *
 * <pre>
 * entitled(f) = plan(f) ∨ addOn(f) ∨ grant(f)
 * limit(f)    = max(plan, grant) + Σ addOn increments     (absent anywhere = unlimited)
 * running     = tenant's choices ∩ (base ∪ entitled)
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class EntitlementService {

    private static final String OPERATOR = "platform";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final PlanRepository plans;
    private final TenantSubscriptionRepository subscriptions;
    private final TenantGrantRepository grants;
    private final Clock clock;

    public record GrantView(Long id, String feature, Long limitValue, LocalDateTime expiresAt, String reason,
                            String grantedBy, boolean active) { }

    public record Entitlements(String tenantKey, String planKey, int planVersion, String planName, String status,
                               List<String> addOns, SortedSet<String> features, Map<String, Long> limits,
                               List<GrantView> grants) {
        public boolean has(String feature) {
            return features.contains(feature);
        }
    }

    public record PlanView(String key, int version, String name, List<String> features, Map<String, Long> limits) { }

    @Transactional(readOnly = true)
    public Entitlements of(String tenantKey) {
        if (OPERATOR.equals(tenantKey)) {
            SortedSet<String> all = new TreeSet<>(FeatureCatalog.BASE);
            plans.findByStatusOrderByIdPlanKeyAsc("ACTIVE").forEach(p -> all.addAll(split(p.getFeatures())));
            all.add(FeatureCatalog.OPERATOR_ONLY);
            return new Entitlements(tenantKey, "operator", 0, "Operator", "ACTIVE", List.of(), all, Map.of(), List.of());
        }
        TenantSubscription sub = subscriptions.findById(tenantKey)
                .orElseThrow(() -> new IllegalStateException("Tenant '" + tenantKey + "' has no subscription"));
        return evaluate(tenantKey, sub.getPlanKey(), sub.getPlanVersion(), sub.getStatus().name(), split(sub.getAddOns()));
    }

    /** What the tenant would be entitled to on another plan and add-ons (for the change preview). */
    @Transactional(readOnly = true)
    public Entitlements hypothetical(String tenantKey, String planKey, List<String> addOns) {
        Plan plan = latest(planKey);
        return evaluate(tenantKey, planKey, plan.getId().getVersion(), "ACTIVE", addOns);
    }

    Entitlements evaluate(String tenantKey, String planKey, int version, String status, List<String> addOns) {
        Plan plan = plans.findById(new Plan.Id(planKey, version))
                .orElseThrow(() -> new IllegalStateException("No plan " + planKey + " v" + version));
        LocalDateTime now = LocalDateTime.now(clock);

        SortedSet<String> features = new TreeSet<>(FeatureCatalog.BASE);
        features.addAll(split(plan.getFeatures()));
        Map<String, Long> limits = new TreeMap<>(readLimits(plan.getLimits()));

        List<GrantView> grantViews = new ArrayList<>();
        for (TenantGrant g : grants.findByTenantKeyOrderByIdDesc(tenantKey)) {
            boolean active = g.activeAt(now);
            grantViews.add(new GrantView(g.getId(), g.getFeature(), g.getLimitValue(), g.getExpiresAt(), g.getReason(),
                    g.getGrantedBy(), active));
            if (!active) continue;
            if (g.getLimitValue() == null) {
                features.add(g.getFeature());
            } else if (limits.containsKey(g.getFeature())) {
                limits.put(g.getFeature(), Math.max(limits.get(g.getFeature()), g.getLimitValue()));
            }
        }
        for (String key : addOns) {
            FeatureCatalog.AddOn addOn = FeatureCatalog.ADD_ONS.get(key);
            if (addOn == null) continue;
            features.addAll(addOn.features());
            addOn.increments().forEach((limit, inc) -> limits.computeIfPresent(limit, (k, v) -> v + inc));
        }
        return new Entitlements(tenantKey, planKey, version, plan.getName(), status, List.copyOf(addOns), features,
                limits, grantViews);
    }

    /** The modules a tenant runs: its own choices, within what it is entitled to. */
    public static Set<String> running(Set<String> chosen, Entitlements e) {
        Set<String> out = new LinkedHashSet<>();
        for (String m : chosen) {
            if (e.has(m)) out.add(m);
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Set<String> runningModules(Tenant tenant) {
        return running(tenant.moduleKeys(), of(tenant.getTenantKey()));
    }

    @Transactional(readOnly = true)
    public List<PlanView> catalog() {
        Map<String, Plan> latest = new LinkedHashMap<>();
        for (Plan p : plans.findByStatusOrderByIdPlanKeyAsc("ACTIVE")) {
            latest.merge(p.getId().getPlanKey(), p, (a, b) -> a.getId().getVersion() >= b.getId().getVersion() ? a : b);
        }
        return latest.values().stream().map(p -> new PlanView(p.getId().getPlanKey(), p.getId().getVersion(),
                p.getName(), split(p.getFeatures()), readLimits(p.getLimits()))).toList();
    }

    /** The newest version of an active plan. */
    public Plan latest(String planKey) {
        return plans.findFirstByIdPlanKeyAndStatusOrderByIdVersionDesc(planKey, "ACTIVE")
                .orElseThrow(() -> new IllegalArgumentException("Unknown plan '" + planKey + "'"));
    }

    static List<String> split(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    static Map<String, Long> readLimits(String json) {
        try {
            return JSON.readValue(json == null || json.isBlank() ? "{}" : json, new TypeReference<Map<String, Long>>() { });
        } catch (Exception e) {
            throw new IllegalStateException("Malformed plan limits", e);
        }
    }
}
