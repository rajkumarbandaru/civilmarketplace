package com.civileng.marketplace.search.service;

import com.civileng.marketplace.search.client.*;
import com.civileng.marketplace.search.document.ProfileDocument;
import com.civileng.marketplace.search.document.ServiceDocument;
import com.civileng.marketplace.search.repository.ProfileSearchRepository;
import com.civileng.marketplace.search.repository.ServiceSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Rebuilds the Elasticsearch read model from the owning services.
 *
 * <p>There is no change-event stream from user/auth/review yet, so this pulls on a schedule.
 * It is deliberately full-rebuild rather than incremental: the dataset is small, and a full
 * rebuild cannot drift the way a partially-failed incremental sync can.
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

    private final AuthServiceClient authServiceClient;
    private final UserServiceClient userServiceClient;
    private final BookingServiceClient bookingServiceClient;
    private final ReviewServiceClient reviewServiceClient;
    private final ProfileSearchRepository profileSearchRepository;
    private final ServiceSearchRepository serviceSearchRepository;

    @Value("${search.reindex-on-startup:true}")
    private boolean reindexOnStartup;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (reindexOnStartup) {
            try {
                reindexAll();
            } catch (Exception e) {
                log.error("Startup reindex failed; search will serve stale/empty results "
                        + "until the next scheduled run", e);
            }
        }
    }

    @Scheduled(cron = "${search.reindex-cron:0 */5 * * * *}")
    public void scheduledReindex() {
        try {
            reindexAll();
        } catch (Exception e) {
            log.error("Scheduled reindex failed", e);
        }
    }

    public Map<String, Object> reindexAll() {
        long start = System.currentTimeMillis();
        int profiles = reindexProfiles();
        int services = reindexServices();
        long tookMs = System.currentTimeMillis() - start;
        log.info("Reindex complete: {} profiles, {} services in {} ms", profiles, services, tookMs);
        return Map.of("profilesIndexed", profiles, "servicesIndexed", services, "tookMs", tookMs);
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

        profileSearchRepository.deleteAll();
        if (!docs.isEmpty()) {
            profileSearchRepository.saveAll(docs);
        }
        return docs.size();
    }

    private int reindexServices() {
        Map<String, Object> response = bookingServiceClient.getCategories();
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

        serviceSearchRepository.deleteAll();
        if (!docs.isEmpty()) {
            serviceSearchRepository.saveAll(docs);
        }
        return docs.size();
    }

    private Map<Long, Map<String, Object>> fetchAllUsers() {
        Map<Long, Map<String, Object>> out = new LinkedHashMap<>();
        for (int page = 0; ; page++) {
            Map<String, Object> resp = authServiceClient.getUsers(page, PAGE_SIZE);
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
            Map<String, Object> resp = userServiceClient.getProfiles(page, PAGE_SIZE);
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
