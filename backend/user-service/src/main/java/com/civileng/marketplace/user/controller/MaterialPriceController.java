package com.civileng.marketplace.user.controller;

import com.civileng.marketplace.user.model.MaterialItem;
import com.civileng.marketplace.user.model.SupplierMaterialPrice;
import com.civileng.marketplace.user.service.MaterialPriceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Supplier price list APIs.
 *
 * <p>The write endpoints are the supplier's own list and take the owner from {@code X-User-Id},
 * which the gateway sets from a verified token — a supplier ID in a request body would be a
 * supplier ID an attacker chooses.
 *
 * <p>{@code /rates} is the read side, open to any signed-in caller, and is what the Civil AI
 * Assistant quotes when an estimate needs a material price.
 */
@RestController
@RequestMapping("/api/v1/users/materials")
@RequiredArgsConstructor
@Tag(name = "Material Price List", description = "Supplier-published material rates")
public class MaterialPriceController {

    /**
     * Roles allowed to publish a material rate. Anyone may read the ranges; only the supply side
     * may claim to sell at one.
     */
    private static final List<String> SUPPLIER_ROLES =
            List.of("MATERIAL_SUPPLIER", "EQUIPMENT_RENTAL", "ADMIN", "SUPER_ADMIN");

    private final MaterialPriceService materialPrices;

    @GetMapping("/catalogue")
    @Operation(summary = "Materials a rate can be published against")
    public ResponseEntity<List<MaterialItem>> catalogue() {
        return ResponseEntity.ok(materialPrices.catalogue());
    }

    @GetMapping("/rates")
    @Operation(summary = "Published rate ranges per material, with the supplier behind each end")
    public ResponseEntity<Map<String, Object>> rates(
            @RequestParam(required = false) String city) {
        List<Map<String, Object>> ranges = materialPrices.rateRanges(city);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "city", city == null ? "" : city,
                "data", ranges,
                "totalElements", ranges.size()));
    }

    @GetMapping("/my-prices")
    @Operation(summary = "The calling supplier's own price list")
    public ResponseEntity<List<SupplierMaterialPrice>> myPrices(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(materialPrices.listForSupplier(userId));
    }

    @PostMapping("/my-prices")
    @Operation(summary = "Publish a rate for a material")
    public ResponseEntity<SupplierMaterialPrice> create(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody SupplierMaterialPrice price) {
        requireSupplier(role);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(materialPrices.create(userId, price));
    }

    @PutMapping("/my-prices/{id}")
    @Operation(summary = "Update one of the calling supplier's rates")
    public ResponseEntity<SupplierMaterialPrice> update(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable Long id,
            @RequestBody SupplierMaterialPrice changes) {
        requireSupplier(role);
        return ResponseEntity.ok(materialPrices.update(userId, id, changes));
    }

    @DeleteMapping("/my-prices/{id}")
    @Operation(summary = "Remove one of the calling supplier's rates")
    public ResponseEntity<Map<String, Object>> delete(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable Long id) {
        requireSupplier(role);
        materialPrices.delete(userId, id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private void requireSupplier(String role) {
        if (role == null || !SUPPLIER_ROLES.contains(role.toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only registered material suppliers can publish material rates");
        }
    }
}
