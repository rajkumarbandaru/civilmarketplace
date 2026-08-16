package com.civileng.marketplace.booking.service;

import com.civileng.marketplace.booking.model.ServiceCategory;
import com.civileng.marketplace.booking.model.ServiceOffering;
import com.civileng.marketplace.booking.repository.ServiceCategoryRepository;
import com.civileng.marketplace.booking.repository.ServiceOfferingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads and writes of the public catalogue, shared by the admin console and the public site.
 *
 * Both surfaces go through the same mapping so the shape an admin edits is exactly the shape the
 * site renders — the two used to be different lists entirely (a DB table nobody displayed, and a
 * TypeScript constant nobody could edit).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogueService {

    private final ServiceCategoryRepository categoryRepository;
    private final ServiceOfferingRepository offeringRepository;

    /**
     * The catalogue as the public site needs it: active rows only.
     *
     * Offerings in a deactivated category are dropped too. Disabling a category has to take its
     * items off the site, otherwise "disable" only hides the filter chip and every service under it
     * stays bookable — which is the opposite of what an admin switching it off is asking for.
     */
    public Map<String, Object> publicCatalogue() {
        List<ServiceOffering> liveOfferings = offeringRepository.findByIsActiveTrueOrderByTitleAsc();

        // Categories that nothing is filed under are left out. The table still holds several from an
        // earlier iteration ("Plumbing", "Survey Services") that no offering uses, and publishing
        // them puts filter chips on the services page that can only ever produce an empty result —
        // a category earns a chip by having something in it.
        Set<String> stocked = liveOfferings.stream()
                .map(ServiceOffering::getCategory)
                .collect(Collectors.toSet());

        List<ServiceCategory> categories = categoryRepository.findAll().stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
                .filter(c -> stocked.contains(c.getName()))
                .sorted(Comparator.comparing(c -> c.getSortOrder() == null ? 0 : c.getSortOrder()))
                .toList();

        Set<String> activeNames = categories.stream().map(ServiceCategory::getName).collect(Collectors.toSet());

        List<Map<String, Object>> services = liveOfferings.stream()
                .filter(o -> activeNames.contains(o.getCategory()))
                .map(this::toPublicOfferingMap)
                .toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("categories", categories.stream().map(this::toPublicCategoryMap).toList());
        data.put("services", services);
        return data;
    }

    /** Every offering, active or not — the admin list. */
    public List<Map<String, Object>> allOfferings() {
        return offeringRepository.findAllByOrderByTitleAsc().stream().map(this::toAdminOfferingMap).toList();
    }

    /**
     * Propagates a category rename onto the offerings filed under the old name.
     *
     * Offerings hold the category as text, so without this a rename orphans everything under it:
     * the chip changes, and every service beneath it silently drops off the site because its
     * category no longer matches any active category.
     */
    @Transactional
    public void renameCategory(String oldName, String newName) {
        if (oldName == null || newName == null || oldName.equals(newName)) return;
        int moved = offeringRepository.renameCategory(oldName, newName);
        if (moved > 0) log.info("Category rename {} -> {} moved {} offerings", oldName, newName, moved);
    }

    public long activeCount(String categoryName) {
        return offeringRepository.countByCategoryAndIsActiveTrue(categoryName);
    }

    public long totalCount(String categoryName) {
        return offeringRepository.countByCategory(categoryName);
    }

    private Map<String, Object> toPublicCategoryMap(ServiceCategory c) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", c.getId());
        map.put("name", c.getName());
        map.put("slug", c.getSlug());
        map.put("icon", c.getIcon());
        map.put("description", c.getDescription());
        map.put("sortOrder", c.getSortOrder());
        return map;
    }

    private Map<String, Object> toPublicOfferingMap(ServiceOffering o) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("slug", o.getSlug());
        map.put("title", o.getTitle());
        map.put("category", o.getCategory());
        map.put("icon", o.getIcon());
        map.put("price", o.getPrice());
        map.put("mediaUrl", o.getMediaUrl());
        map.put("mediaType", o.getMediaType());
        map.put("rating", o.getRating());
        map.put("reviews", o.getReviews());
        map.put("aliases", o.getAliases());
        return map;
    }

    public Map<String, Object> toAdminOfferingMap(ServiceOffering o) {
        Map<String, Object> map = new LinkedHashMap<>(toPublicOfferingMap(o));
        map.put("id", o.getId());
        map.put("sortOrder", o.getSortOrder());
        map.put("active", o.getIsActive());
        map.put("createdAt", o.getCreatedAt() != null ? o.getCreatedAt().toString() : null);
        map.put("updatedAt", o.getUpdatedAt() != null ? o.getUpdatedAt().toString() : null);
        return map;
    }
}
