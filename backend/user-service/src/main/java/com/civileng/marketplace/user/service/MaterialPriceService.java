package com.civileng.marketplace.user.service;

import com.civileng.marketplace.user.model.MaterialItem;
import com.civileng.marketplace.user.model.SupplierMaterialPrice;
import com.civileng.marketplace.user.repository.MaterialItemRepository;
import com.civileng.marketplace.user.repository.SupplierMaterialPriceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * The supplier price list: suppliers publish material rates here, and estimates read the ranges
 * back out.
 *
 * <p>Two audiences, deliberately kept apart. A supplier edits only their own rows — ownership is
 * checked on every write against the caller's user ID, never against an ID in the request body.
 * Everyone else sees the aggregate: a low, a high and a median per material, with the supplier
 * user IDs behind the extremes, so a quoted figure can be traced to a real listing rather than
 * being taken on trust.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MaterialPriceService {

    private final SupplierMaterialPriceRepository priceRepository;
    private final MaterialItemRepository materialRepository;

    // ---------------------------------------------------------------- catalogue

    public List<MaterialItem> catalogue() {
        return materialRepository.findByIsActiveTrueOrderByCategoryAscNameAsc();
    }

    // ---------------------------------------------------------------- supplier's own list

    public List<SupplierMaterialPrice> listForSupplier(Long supplierUserId) {
        return priceRepository.findBySupplierUserIdOrderByUpdatedAtDesc(supplierUserId);
    }

    @Transactional
    public SupplierMaterialPrice create(Long supplierUserId, SupplierMaterialPrice price) {
        MaterialItem material = resolveMaterial(price);
        String city = normaliseCity(price.getCity());

        if (priceRepository.existsBySupplierUserIdAndMaterialItemIdAndCity(
                supplierUserId, material.getId(), city)) {
            // Two rows for the same material and city would put one supplier at both ends of a
            // range, which reads as competition where there is none.
            throw new ResponseStatusException(CONFLICT,
                    "You already publish a rate for " + material.getName() + " in " + city
                            + ". Edit that entry instead.");
        }

        price.setId(null);
        price.setSupplierUserId(supplierUserId);
        price.setMaterialItem(material);
        price.setCity(city);
        return priceRepository.save(price);
    }

    @Transactional
    public SupplierMaterialPrice update(Long supplierUserId, Long id, SupplierMaterialPrice changes) {
        SupplierMaterialPrice existing = priceRepository.findByIdAndSupplierUserId(id, supplierUserId)
                // Not-found rather than forbidden: whether a row exists under another supplier is
                // not something a caller should be able to probe for.
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Price entry not found"));

        if (changes.getPrice() != null) existing.setPrice(changes.getPrice());
        if (changes.getBrand() != null) existing.setBrand(changes.getBrand());
        if (changes.getNotes() != null) existing.setNotes(changes.getNotes());
        if (changes.getCurrency() != null) existing.setCurrency(changes.getCurrency());
        if (changes.getMinOrderQuantity() != null) existing.setMinOrderQuantity(changes.getMinOrderQuantity());
        if (changes.getDeliveryIncluded() != null) existing.setDeliveryIncluded(changes.getDeliveryIncluded());
        if (changes.getIsActive() != null) existing.setIsActive(changes.getIsActive());
        // Cleared explicitly when the supplier sends the epoch-less "no expiry" sentinel is not
        // worth a second field: sending validUntil sets it, omitting it leaves it alone.
        if (changes.getValidUntil() != null) existing.setValidUntil(changes.getValidUntil());

        return priceRepository.save(existing);
    }

    @Transactional
    public void delete(Long supplierUserId, Long id) {
        SupplierMaterialPrice existing = priceRepository.findByIdAndSupplierUserId(id, supplierUserId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Price entry not found"));
        priceRepository.delete(existing);
    }

    // ---------------------------------------------------------------- aggregate the estimator reads

    /**
     * Rate ranges per material, optionally narrowed to one city.
     *
     * @return one entry per material that has at least one quotable rate. Materials nobody quotes
     *         are left out entirely rather than returned empty, so a caller cannot mistake
     *         "no supplier" for "free".
     */
    public List<Map<String, Object>> rateRanges(String city) {
        List<SupplierMaterialPrice> quotable =
                priceRepository.findQuotableRates(normaliseCityOrNull(city), LocalDateTime.now());

        Map<Long, List<SupplierMaterialPrice>> byMaterial = new LinkedHashMap<>();
        for (SupplierMaterialPrice price : quotable) {
            byMaterial.computeIfAbsent(price.getMaterialItem().getId(), key -> new ArrayList<>())
                    .add(price);
        }

        List<Map<String, Object>> ranges = new ArrayList<>();
        byMaterial.forEach((materialId, prices) -> {
            prices.sort(Comparator.comparing(SupplierMaterialPrice::getPrice));
            SupplierMaterialPrice low = prices.get(0);
            SupplierMaterialPrice high = prices.get(prices.size() - 1);
            MaterialItem material = low.getMaterialItem();

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("materialId", materialId);
            entry.put("material", material.getName());
            entry.put("category", material.getCategory());
            entry.put("unit", material.getUnit().getLabel());
            entry.put("specification", material.getSpecification());
            entry.put("supplierCount", prices.size());
            entry.put("currency", low.getCurrency());
            entry.put("low", low.getPrice());
            entry.put("high", high.getPrice());
            entry.put("median", median(prices));
            entry.put("lowSupplierUserId", low.getSupplierUserId());
            entry.put("highSupplierUserId", high.getSupplierUserId());
            entry.put("lowCity", low.getCity());
            entry.put("highCity", high.getCity());
            // The freshest quote in the range dates the whole range: a customer reading a low of
            // last week and a high of last year should be able to tell.
            entry.put("lastUpdated", prices.stream()
                    .map(SupplierMaterialPrice::getUpdatedAt)
                    .filter(java.util.Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null));
            ranges.add(entry);
        });

        ranges.sort(Comparator.comparing(
                entry -> String.valueOf(entry.get("category")) + entry.get("material")));
        return ranges;
    }

    // ---------------------------------------------------------------- helpers

    private MaterialItem resolveMaterial(SupplierMaterialPrice price) {
        if (price.getMaterialItem() == null || price.getMaterialItem().getId() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "A material must be chosen from the catalogue");
        }
        return materialRepository.findById(price.getMaterialItem().getId())
                .filter(MaterialItem::getIsActive)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Unknown material"));
    }

    /**
     * Stored in one casing so the unique constraint and the city filter agree. "Chennai" and
     * "chennai" are one place, and letting both through would split a range in two.
     */
    private String normaliseCity(String city) {
        if (city == null || city.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "A city is required for a material rate");
        }
        return city.trim();
    }

    private String normaliseCityOrNull(String city) {
        return city == null || city.isBlank() ? null : city.trim();
    }

    private BigDecimal median(List<SupplierMaterialPrice> sorted) {
        int size = sorted.size();
        BigDecimal middle = sorted.get(size / 2).getPrice();
        if (size % 2 != 0) return middle;
        return middle.add(sorted.get(size / 2 - 1).getPrice())
                .divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP);
    }
}
