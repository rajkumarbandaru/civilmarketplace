package com.civileng.marketplace.support.service;

import com.civileng.marketplace.support.client.SearchServiceClient;
import com.civileng.marketplace.support.client.UserServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Builds the "what this site actually charges" half of an estimate.
 *
 * <p>The assistant is asked to quote two things for every rate: what registered providers and
 * suppliers on this platform charge, and what the open market charges. Only the first can be known
 * — it is in the profile index and the supplier price list — so it is computed here and handed to
 * the model as fact, rather than left to a language model that would otherwise produce a plausible
 * number with a made-up user ID attached to it.
 *
 * <p>Two cards come out: service rates grouped by provider role, and material rates per catalogue
 * item. Each row carries the user IDs of the cheapest and dearest behind its range, so a quoted
 * figure can be traced back to a real listing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SiteRateService {

    private final SearchServiceClient searchClient;
    private final UserServiceClient userClient;

    /**
     * Roles that are admin or demand side. They have no service rate to publish, and including
     * them would put an "ADMIN" line in a BOQ.
     */
    private static final List<String> NON_SUPPLY_ROLES = List.of(
            "SUPER_ADMIN", "ADMIN", "SUB_ADMIN", "REGIONAL_ADMIN", "CITY_MANAGER", "CUSTOMER");

    /** Page size search-service caps at, and how many pages are walked at most. */
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 5;

    @Value("${app.ai.site-rates.enabled:true}")
    private boolean enabled;

    /**
     * The card changes only when providers sign up or edit a rate, so it is rebuilt on a timer
     * rather than per question — otherwise every message would fan out five search calls while
     * someone waits on a "Thinking…" line.
     */
    @Value("${app.ai.site-rates.cache-seconds:600}")
    private long cacheSeconds;

    private record Cached(String card, long builtAt) {}

    private final AtomicReference<Cached> cache = new AtomicReference<>();

    /**
     * @return a Markdown rate card to ground the model, or null when there is nothing to ground it
     *         with — no providers indexed, the feature switched off, or search-service unreachable
     */
    public String rateCard() {
        if (!enabled) return null;

        Cached current = cache.get();
        if (current != null && System.currentTimeMillis() - current.builtAt() < cacheSeconds * 1000) {
            return current.card();
        }

        String card = build();
        // Null is cached too: a platform with no indexed providers should not re-walk five pages
        // of search results on every question to rediscover that.
        cache.set(new Cached(card, System.currentTimeMillis()));
        return card;
    }

    private String build() {
        String labour = buildLabourCard();
        String materials = buildMaterialCard();
        if (labour == null && materials == null) return null;

        StringBuilder card = new StringBuilder();
        if (labour != null) card.append(labour);
        if (materials != null) {
            if (labour != null) card.append('\n');
            card.append(materials);
        }
        if (labour == null) {
            card.append("\nNo registered provider service rates are available; price labour and ")
                .append("works on the ACTUAL side only.\n");
        }
        if (materials == null) {
            card.append("\nNo supplier material rates are published for any material; price ")
                .append("materials on the ACTUAL side only.\n");
        }
        return card.toString();
    }

    /** Provider service rates, grouped by role. */
    private String buildLabourCard() {
        List<Map<String, Object>> profiles = fetchProfiles();
        if (profiles.isEmpty()) return null;

        // TreeMap so roles come out in a stable alphabetical order; the model reproduces the table
        // it is given, and a card that reshuffles between questions reads as changing data.
        Map<String, List<Map<String, Object>>> byRole = new TreeMap<>();
        for (Map<String, Object> profile : profiles) {
            Object role = profile.get("role");
            Double rate = asDouble(profile.get("hourlyRate"));
            if (role == null || rate == null || rate <= 0) continue;
            if (NON_SUPPLY_ROLES.contains(role.toString())) continue;
            byRole.computeIfAbsent(role.toString(), key -> new ArrayList<>()).add(profile);
        }
        if (byRole.isEmpty()) return null;

        StringBuilder card = new StringBuilder();
        card.append("SITE RATE CARD — registered, currently available providers on this platform.\n")
            .append("Rates are each provider's own published hourly rate, in INR/hour. ")
            .append("The reference IDs are provider user IDs on this site.\n\n")
            .append("| Role | Providers | Site Low | Site High | Site Median | Ref ID (low) | Ref ID (high) |\n")
            .append("| --- | --- | --- | --- | --- | --- | --- |\n");

        byRole.forEach((role, list) -> {
            list.sort(Comparator.comparingDouble(p -> asDouble(p.get("hourlyRate"))));
            Map<String, Object> low = list.get(0);
            Map<String, Object> high = list.get(list.size() - 1);
            card.append(String.format("| %s | %d | %s | %s | %s | %s | %s |%n",
                    role,
                    list.size(),
                    money(asDouble(low.get("hourlyRate"))),
                    money(asDouble(high.get("hourlyRate"))),
                    money(median(list)),
                    reference(low),
                    reference(high)));
        });

        return card.toString();
    }

    /**
     * Supplier-published material rates. Separate from the labour card because the two are read
     * differently: a provider rate is per hour and needs a productivity assumption to reach a BOQ
     * line, whereas a material rate is already per unit of the thing being estimated.
     */
    private String buildMaterialCard() {
        List<Map<String, Object>> rates = fetchMaterialRates();
        if (rates.isEmpty()) return null;

        StringBuilder card = new StringBuilder();
        card.append("SITE MATERIAL RATE CARD — rates published by registered suppliers on this ")
            .append("platform. The reference IDs are supplier user IDs on this site.\n\n")
            .append("| Material | Category | Unit | Suppliers | Site Low | Site High | Site Median | ")
            .append("Ref ID (low) | Ref ID (high) | City (low/high) | Last updated |\n")
            .append("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");

        for (Map<String, Object> rate : rates) {
            card.append(String.format("| %s | %s | %s | %s | %s | %s | %s | user #%s | user #%s | %s / %s | %s |%n",
                    text(rate.get("material")),
                    text(rate.get("category")),
                    text(rate.get("unit")),
                    text(rate.get("supplierCount")),
                    money(asDouble(rate.get("low"))),
                    money(asDouble(rate.get("high"))),
                    money(asDouble(rate.get("median"))),
                    text(rate.get("lowSupplierUserId")),
                    text(rate.get("highSupplierUserId")),
                    text(rate.get("lowCity")),
                    text(rate.get("highCity")),
                    date(rate.get("lastUpdated"))));
        }

        card.append("\nMaterials absent from this table have no supplier rate on this platform. ")
            .append("Price those on the ACTUAL side and say the site rate is unavailable.\n");
        return card.toString();
    }

    /** The aggregate is computed in user-service; this only shapes it into the card. */
    private List<Map<String, Object>> fetchMaterialRates() {
        try {
            Map<String, Object> response = userClient.materialRates();
            if (!(response.get("data") instanceof List<?> data)) return List.of();
            List<Map<String, Object>> rates = new ArrayList<>();
            for (Object item : data) {
                if (item instanceof Map<?, ?> rate) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typed = (Map<String, Object>) rate;
                    rates.add(typed);
                }
            }
            return rates;
        } catch (Exception e) {
            log.warn("[CivilAI] Could not read supplier material rates: {}", e.getMessage());
            return List.of();
        }
    }

    private String text(Object value) {
        return value == null ? "n/a" : value.toString();
    }

    /** Trims a timestamp to its date: the estimate cares how stale a rate is, not what minute. */
    private String date(Object value) {
        if (value == null) return "unknown";
        String raw = value.toString();
        int splitAt = raw.indexOf('T');
        return splitAt > 0 ? raw.substring(0, splitAt) : raw;
    }

    /** Walks the profile index in rate order, stopping early once a short page comes back. */
    private List<Map<String, Object>> fetchProfiles() {
        List<Map<String, Object>> all = new ArrayList<>();
        try {
            for (int page = 0; page < MAX_PAGES; page++) {
                Map<String, Object> response =
                        searchClient.searchProfiles(true, "price_low", page, PAGE_SIZE);
                if (!(response.get("data") instanceof List<?> data) || data.isEmpty()) break;
                for (Object item : data) {
                    if (item instanceof Map<?, ?> profile) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> typed = (Map<String, Object>) profile;
                        all.add(typed);
                    }
                }
                if (data.size() < PAGE_SIZE) break;
            }
        } catch (Exception e) {
            // The fallback factory covers Feign's own failures; this catches a malformed response
            // body, which would otherwise take down an answer that is fine without the card.
            log.warn("[CivilAI] Could not build the site rate card: {}", e.getMessage());
        }
        return all;
    }

    /** Sorted list in, middle value out — the mean would be dragged by a single outlier rate. */
    private double median(List<Map<String, Object>> sorted) {
        int size = sorted.size();
        double middle = asDouble(sorted.get(size / 2).get("hourlyRate"));
        if (size % 2 != 0) return middle;
        return (asDouble(sorted.get(size / 2 - 1).get("hourlyRate")) + middle) / 2;
    }

    /**
     * The reference a quoted rate can be traced to. Verified providers are marked, because "the
     * cheapest on the site" and "the cheapest verified provider" are different recommendations.
     */
    private String reference(Map<String, Object> profile) {
        Object userId = profile.get("userId");
        if (userId == null) return "n/a";
        return "user #" + userId + (Boolean.TRUE.equals(profile.get("isVerified")) ? " (verified)" : "");
    }

    private String money(Double value) {
        return value == null ? "n/a" : String.format("%,.0f", value);
    }

    private Double asDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    /** Exposed for the status endpoint, so the panel can say whether site rates are in play. */
    public Map<String, Object> status() {
        String card = rateCard();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("siteRatesAvailable", card != null);
        return status;
    }
}
