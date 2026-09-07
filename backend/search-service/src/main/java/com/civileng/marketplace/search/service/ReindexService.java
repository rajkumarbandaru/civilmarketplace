package com.civileng.marketplace.search.service;

import com.civileng.marketplace.web.common.client.UserDirectoryClient;
import com.civileng.marketplace.search.client.*;
import com.civileng.marketplace.search.config.TenantIndex;
import com.civileng.marketplace.search.document.ProfileDocument;
import com.civileng.marketplace.search.document.ServiceDocument;
import com.civileng.marketplace.tenant.common.CrossTenantRunner;
import com.civileng.marketplace.tenant.common.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Rebuilds the Elasticsearch read model from the owning services, one tenant at a time.
 *
 * <p>There is no change-event stream from user/auth/review yet, so this pulls on a schedule.
 * It is deliberately full-rebuild rather than incremental: the dataset is small, and a full
 * rebuild cannot drift the way a partially-failed incremental sync can.
 *
 * <p>Every read here is a Feign call into a schema-per-tenant service, and every write lands in a
 * per-tenant index, so all of it has to run with a tenant bound — the scheduled sweep walks the
 * registry and rebuilds each tenant under {@code runAs}. Before that, this class called
 * auth-service with no tenant header at all, which those services correctly answer with 400; the
 * index it left behind was pre-tenancy data that every tenant then searched.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReindexService {

    private static final int PAGE_SIZE = 100;
    /** Roles that are demand-side or staff — never surfaced as bookable supply in search. */
    private static final Set<String> NON_SUPPLY_ROLES = Set.of(
            "CUSTOMER", "SUPER_ADMIN", "ADMIN", "SUB_ADMIN", "REGIONAL_ADMIN", "CITY_MANAGER");
    /** Only these account states may appear in results (FR-10: exclude suspended/pending). */
    private static final Set<String> SEARCHABLE_STATUSES = Set.of("ACTIVE");

    private final UserDirectoryClient userDirectoryClient;
    private final UserProfileClient userProfileClient;
    private final ServiceCatalogueClient serviceCatalogueClient;
    private final ReviewServiceClient reviewServiceClient;
    private final ElasticsearchOperations elasticsearch;
    private final TenantIndex tenantIndex;
    private final CrossTenantRunner crossTenantRunner;
    private final IndexPruner indexPruner;

    @Value("${search.reindex-on-startup:true}")
    private boolean reindexOnStartup;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (reindexOnStartup) {
            reindexAllTenants();
        }
    }

    @Scheduled(cron = "${search.reindex-cron:0 */5 * * * *}")
    public void scheduledReindex() {
        reindexAllTenants();
    }

    /**
     * Rebuilds every active tenant's indices. One tenant failing is logged and the sweep continues,
     * so a single unreachable tenant cannot leave the others on a stale index. Indices no active
     * tenant owns are pruned at the end — see {@link IndexPruner}.
     */
    public void reindexAllTenants() {
        crossTenantRunner.forEachTenant("search-reindex", tenantKey -> {
            Map<String, Object> result = reindexCurrentTenant();
            log.info("Reindexed tenant '{}': {}", tenantKey, result);
        });
        // After, not before: the sweep is what defines which indices are current, so pruning what
        // it did not touch is the same pass rather than a guess made ahead of it.
        indexPruner.prune();
    }

    /**
     * Rebuilds the indices of the tenant bound to this thread. This is the unit of work: it is what
     * the operator's reindex endpoint runs for their own tenant, and what the sweep runs per tenant.
     */
    public Map<String, Object> reindexCurrentTenant() {
        String tenantKey = TenantContext.require();
        long start = System.currentTimeMillis();
        int profiles = reindexProfiles();
        int services = reindexServices();
        long tookMs = System.currentTimeMillis() - start;
        log.info("Reindex complete for '{}': {} profiles, {} services in {} ms",
                tenantKey, profiles, services, tookMs);
        return Map.of("tenant", tenantKey, "profilesIndexed", profiles,
                "servicesIndexed", services, "tookMs", tookMs);
    }

    private int reindexProfiles() {
        Map<Long, Map<String, Object>> users = fetchAllUsers();
        Map<Long, Map<String, Object>> profilesByUserId = fetchAllProfiles();

        List<ProfileDocument> docs = new ArrayList<>();
        for (Map.Entry<Long, Map<String, Object>> entry : users.entrySet()) {
            Long userId = entry.getKey();
            Map<String, Object> user = entry.getValue();

            String role = str(user.get("role"));
            String status = str(user.get("status"));
            if (role == null || NON_SUPPLY_ROLES.contains(role)) continue;
            if (status == null || !SEARCHABLE_STATUSES.contains(status)) continue;

            Map<String, Object> profile = profilesByUserId.getOrDefault(userId, Map.of());
            Map<String, Object> rating = safeRating(userId);

            String city = firstNonBlank(str(profile.get("city")), str(user.get("city")));

            docs.add(ProfileDocument.builder()
                    .id(String.valueOf(userId))
                    .userId(userId)
                    .name(str(user.get("name")))
                    .role(role)
                    .city(city)
                    .cityKeyword(city == null ? null : city.toLowerCase())
                    .state(str(profile.get("state")))
                    .bio(str(profile.get("bio")))
                    .languages(str(profile.get("languages")))
                    .experienceYears(toInt(profile.get("experienceYears")))
                    .hourlyRate(toDouble(profile.get("hourlyRate")))
                    .averageRating(toDouble(rating.get("averageRating")))
                    .totalReviews(toInt(rating.get("totalReviews")))
                    .isVerified(toBool(profile.get("isVerified")))
                    .isAvailable(profile.isEmpty() || toBool(profile.get("isAvailable")))
                    .build());
        }

        replaceContents(ProfileDocument.class, docs, tenantIndex.profilesIndex());
        return docs.size();
    }

    private int reindexServices() {
        Map<String, Object> response = serviceCatalogueClient.getCategories();
        List<Map<String, Object>> categories = asList(response.get("data"));

        List<ServiceDocument> docs = categories.stream()
                .map(c -> ServiceDocument.builder()
                        .id(String.valueOf(c.get("id")))
                        .categoryId(toLong(c.get("id")))
                        .name(str(c.get("name")))
                        .slug(str(c.get("slug")))
                        .description(str(c.get("description")))
                        .sortOrder(toInt(c.get("sortOrder")))
                        .active(toBool(c.get("active")))
                        .build())
                .toList();

        replaceContents(ServiceDocument.class, docs, tenantIndex.servicesIndex());
        return docs.size();
    }

    /**
     * Swaps an index's whole contents for {@code docs}, against explicitly named coordinates.
     *
     * <p>The coordinates are passed rather than inferred so the tenant a write lands in is visible
     * at the call site. Refreshed at the end because a reindex is only useful once it is
     * searchable, and the operator endpoint reports a count the very next request should see.
     */
    private <T> void replaceContents(Class<T> type, List<T> docs,
                                     org.springframework.data.elasticsearch.core.mapping
                                             .IndexCoordinates coordinates) {
        IndexOperations indexOps = elasticsearch.indexOps(type);
        if (!indexOps.exists()) {
            // createWithMapping, not create: an index ES invents from the first document indexed
            // gets text/keyword guesses that break the term filters the search queries rely on.
            indexOps.createWithMapping();
        }
        elasticsearch.delete(Query.findAll(), type, coordinates);
        if (!docs.isEmpty()) {
            elasticsearch.save(docs, coordinates);
        }
        indexOps.refresh();
    }

    private Map<Long, Map<String, Object>> fetchAllUsers() {
        Map<Long, Map<String, Object>> out = new LinkedHashMap<>();
        for (int page = 0; ; page++) {
            Map<String, Object> resp = userDirectoryClient.getUsers(page, PAGE_SIZE, null, null);
            List<Map<String, Object>> data = asList(resp.get("data"));
            if (data.isEmpty()) break;
            data.forEach(u -> {
                Long id = toLong(u.get("id"));
                if (id != null) out.put(id, u);
            });
            if (page + 1 >= toInt(resp.getOrDefault("totalPages", 1))) break;
        }
        return out;
    }

    private Map<Long, Map<String, Object>> fetchAllProfiles() {
        Map<Long, Map<String, Object>> out = new LinkedHashMap<>();
        for (int page = 0; ; page++) {
            Map<String, Object> resp = userProfileClient.getProfiles(page, PAGE_SIZE);
            List<Map<String, Object>> data = asList(resp.get("data"));
            if (data.isEmpty()) break;
            data.forEach(p -> {
                Long id = toLong(p.get("userId"));
                if (id != null) out.put(id, p);
            });
            if (page + 1 >= toInt(resp.getOrDefault("totalPages", 1))) break;
        }
        return out;
    }

    /** A missing rating must not fail the whole reindex — an unrated profile is still findable. */
    private Map<String, Object> safeRating(Long userId) {
        try {
            Map<String, Object> r = reviewServiceClient.getRatingSummary(userId);
            return r == null ? Map.of() : r;
        } catch (Exception e) {
            log.debug("No rating summary for user {}: {}", userId, e.getMessage());
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asList(Object o) {
        return o instanceof List ? (List<Map<String, Object>>) o : List.of();
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o);
        return s.isBlank() ? null : s;
    }

    private static String firstNonBlank(String a, String b) {
        return a != null ? a : b;
    }

    private static Long toLong(Object o) {
        return o instanceof Number n ? n.longValue() : null;
    }

    private static Integer toInt(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    private static Double toDouble(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0.0;
    }

    private static Boolean toBool(Object o) {
        return o instanceof Boolean b ? b : Boolean.FALSE;
    }
}
