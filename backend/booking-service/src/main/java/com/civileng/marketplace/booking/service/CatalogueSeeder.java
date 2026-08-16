package com.civileng.marketplace.booking.service;

import com.civileng.marketplace.booking.model.ServiceCategory;
import com.civileng.marketplace.booking.model.ServiceOffering;
import com.civileng.marketplace.booking.repository.ServiceCategoryRepository;
import com.civileng.marketplace.booking.repository.ServiceOfferingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;

/**
 * Loads the catalogue the frontend used to hard-code into the database, once.
 *
 * Seeding is per-row, not all-or-nothing on an empty table: an admin who deletes an item must not
 * have it reappear on the next restart, and a release that adds new stock items should still be
 * able to introduce them. Existing slugs are therefore left completely untouched — edits made in
 * the admin console always win over the seed file.
 *
 * Runs on {@link ApplicationReadyEvent} rather than as an {@link ApplicationRunner} so it happens
 * after Hibernate has created the tables.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CatalogueSeeder {

    private static final String SEED_FILE = "catalogue-seed.json";

    private final ServiceCategoryRepository categoryRepository;
    private final ServiceOfferingRepository offeringRepository;
    private final ObjectMapper objectMapper;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        JsonNode root;
        try (InputStream in = new ClassPathResource(SEED_FILE).getInputStream()) {
            root = objectMapper.readTree(in);
        } catch (Exception e) {
            log.error("Catalogue seed file {} could not be read; catalogue left as-is: {}", SEED_FILE, e.getMessage());
            return;
        }

        int categories = 0;
        for (JsonNode node : root.path("categories")) {
            String slug = node.path("slug").asText();
            if (slug.isBlank() || categoryRepository.existsBySlug(slug)) continue;
            categoryRepository.save(ServiceCategory.builder()
                    .name(node.path("name").asText())
                    .slug(slug)
                    .icon(text(node, "icon"))
                    .description(text(node, "description"))
                    .sortOrder(node.path("sortOrder").asInt(0))
                    .isActive(true)
                    .build());
            categories++;
        }

        int offerings = 0;
        for (JsonNode node : root.path("services")) {
            String slug = node.path("slug").asText();
            if (slug.isBlank() || offeringRepository.existsBySlug(slug)) continue;
            offeringRepository.save(ServiceOffering.builder()
                    .slug(slug)
                    .title(node.path("title").asText())
                    .category(node.path("category").asText())
                    .icon(text(node, "icon"))
                    .price(text(node, "price"))
                    .rating(node.path("rating").asDouble(0))
                    .reviews(node.path("reviews").asInt(0))
                    .aliases(text(node, "aliases"))
                    .sortOrder(node.path("sortOrder").asInt(0))
                    .isActive(true)
                    .build());
            offerings++;
        }

        if (categories > 0 || offerings > 0) {
            log.info("Catalogue seed applied: {} new categories, {} new offerings", categories, offerings);
        }
    }

    /** Absent and JSON null both mean "no value"; asText() would hand back the string "null". */
    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }
}
