package com.civileng.marketplace.search.service;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.json.JsonData;
import com.civileng.marketplace.search.config.TenantIndex;
import com.civileng.marketplace.search.document.ProfileDocument;
import com.civileng.marketplace.search.document.ServiceDocument;
import com.civileng.marketplace.search.dto.ProfileSearchRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.NoSuchIndexException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the search indices for the tenant bound to the current request.
 *
 * <p>Every query names its index explicitly through {@link TenantIndex}. That is the isolation
 * boundary for this service — it has no relational store, so it gets none of the schema-per-tenant
 * routing the rest of the platform relies on, and a tenant filter inside the query would put the
 * boundary at the mercy of every future edit to these builders.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SearchService {

    private final ElasticsearchTemplate elasticsearchTemplate;
    private final TenantIndex tenantIndex;

    public Map<String, Object> searchProfiles(ProfileSearchRequest request) {
        List<Query> must = new ArrayList<>();
        List<Query> filter = new ArrayList<>();

        if (request.getQ() != null && !request.getQ().isBlank()) {
            // Name matters most, then the free-text trade description, then location.
            must.add(Query.of(q -> q.multiMatch(m -> m
                    .query(request.getQ())
                    .fields("name^3", "bio^2", "city", "state", "languages", "role")
                    .fuzziness("AUTO"))));
        } else {
            must.add(Query.of(q -> q.matchAll(m -> m)));
        }

        if (request.getRole() != null && !request.getRole().isBlank()) {
            filter.add(Query.of(q -> q.term(t -> t
                    .field("role").value(request.getRole().toUpperCase()))));
        }
        if (request.getCity() != null && !request.getCity().isBlank()) {
            filter.add(Query.of(q -> q.term(t -> t
                    .field("cityKeyword").value(request.getCity().toLowerCase()))));
        }
        if (request.getMinRating() != null) {
            filter.add(Query.of(q -> q.range(r -> r
                    .field("averageRating").gte(JsonData.of(request.getMinRating())))));
        }
        if (request.getMinPrice() != null || request.getMaxPrice() != null) {
            filter.add(Query.of(q -> q.range(r -> {
                r.field("hourlyRate");
                if (request.getMinPrice() != null) r.gte(JsonData.of(request.getMinPrice()));
                if (request.getMaxPrice() != null) r.lte(JsonData.of(request.getMaxPrice()));
                return r;
            })));
        }
        if (Boolean.TRUE.equals(request.getVerifiedOnly())) {
            filter.add(Query.of(q -> q.term(t -> t.field("isVerified").value(true))));
        }
        if (Boolean.TRUE.equals(request.getAvailableOnly())) {
            filter.add(Query.of(q -> q.term(t -> t.field("isAvailable").value(true))));
        }

        Query bool = Query.of(q -> q.bool(b -> b.must(must).filter(filter)));

        NativeQueryBuilder builder = NativeQuery.builder()
                .withQuery(bool)
                .withPageable(PageRequest.of(request.getPage(), request.getSize()));

        applySort(builder, request.getSort());

        SearchHits<ProfileDocument> hits;
        try {
            hits = elasticsearchTemplate.search(
                    builder.build(), ProfileDocument.class, tenantIndex.profilesIndex());
        } catch (NoSuchIndexException e) {
            return notYetIndexed(tenantIndex.profiles(), request.getPage(), request.getSize());
        }

        return toResponse(hits, request.getPage(), request.getSize());
    }

    /**
     * Relevance is the default. The explicit sorts are secondary orderings a user picks in the
     * UI; each falls back to rating so ties do not come back in arbitrary index order.
     */
    private void applySort(NativeQueryBuilder builder, String sort) {
        if (sort == null || sort.isBlank() || "relevance".equalsIgnoreCase(sort)) {
            builder.withSort(s -> s.score(sc -> sc.order(SortOrder.Desc)));
            builder.withSort(s -> s.field(f -> f.field("averageRating").order(SortOrder.Desc)));
            return;
        }
        switch (sort.toLowerCase()) {
            case "rating" -> {
                builder.withSort(s -> s.field(f -> f.field("averageRating").order(SortOrder.Desc)));
                builder.withSort(s -> s.field(f -> f.field("totalReviews").order(SortOrder.Desc)));
            }
            case "price_low" ->
                    builder.withSort(s -> s.field(f -> f.field("hourlyRate").order(SortOrder.Asc)));
            case "price_high" ->
                    builder.withSort(s -> s.field(f -> f.field("hourlyRate").order(SortOrder.Desc)));
            case "experience" ->
                    builder.withSort(s -> s.field(f -> f.field("experienceYears").order(SortOrder.Desc)));
            default -> builder.withSort(s -> s.score(sc -> sc.order(SortOrder.Desc)));
        }
    }

    public Map<String, Object> searchServices(String q, int page, int size) {
        Query query;
        if (q == null || q.isBlank()) {
            query = Query.of(qq -> qq.term(t -> t.field("active").value(true)));
        } else {
            query = Query.of(qq -> qq.bool(b -> b
                    .must(m -> m.multiMatch(mm -> mm
                            .query(q).fields("name^3", "description", "slug").fuzziness("AUTO")))
                    .filter(f -> f.term(t -> t.field("active").value(true)))));
        }

        SearchHits<ServiceDocument> hits;
        try {
            hits = elasticsearchTemplate.search(
                    NativeQuery.builder()
                            .withQuery(query)
                            .withPageable(PageRequest.of(page, size))
                            .build(),
                    ServiceDocument.class, tenantIndex.servicesIndex());
        } catch (NoSuchIndexException e) {
            return notYetIndexed(tenantIndex.services(), page, size);
        }

        return toResponse(hits, page, size);
    }

    /**
     * A tenant whose first reindex has not run yet has no index. That is an empty result set, not a
     * failure — returning 500 until the next sweep would make a newly onboarded tenant look broken.
     * Logged at warn because the same shape would also be how a wrongly-deleted index presents.
     */
    private Map<String, Object> notYetIndexed(String index, int page, int size) {
        log.warn("Index '{}' does not exist yet; serving an empty result set", index);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", List.of());
        response.put("page", page);
        response.put("size", size);
        response.put("totalElements", 0L);
        response.put("totalPages", 0);
        return response;
    }

    private <T> Map<String, Object> toResponse(SearchHits<T> hits, int page, int size) {
        List<T> content = hits.getSearchHits().stream().map(SearchHit::getContent).toList();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", content);
        response.put("page", page);
        response.put("size", size);
        response.put("totalElements", hits.getTotalHits());
        response.put("totalPages", size == 0 ? 0 : (int) Math.ceil((double) hits.getTotalHits() / size));
        return response;
    }
}
