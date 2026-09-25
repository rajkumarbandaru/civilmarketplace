package com.civileng.marketplace.user.controller;

import com.civileng.marketplace.user.model.SupplierMaterialPrice;
import com.civileng.marketplace.user.repository.SupplierMaterialPriceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A supplier's published material rates, for procurement-service seeding the catalogue of the
 * organization the supplier's account becomes. Internal-only at the gateway: rates are public on
 * the rates page anyway, but this lookup by account id is not something a browser needs.
 */
@RestController
@RequestMapping("/api/v1/users/internal")
@RequiredArgsConstructor
@Tag(name = "Internal material prices", description = "Service-to-service reads of supplier rates")
public class InternalMaterialPriceController {

    private final SupplierMaterialPriceRepository prices;

    public record MaterialPrice(String material, String unit, BigDecimal price, String brand) { }

    @GetMapping("/material-prices/{supplierUserId}")
    @Transactional(readOnly = true)
    @Operation(summary = "A supplier's active, unexpired rates")
    public List<MaterialPrice> pricesOf(@PathVariable Long supplierUserId) {
        LocalDateTime now = LocalDateTime.now();
        return prices.findBySupplierUserIdOrderByUpdatedAtDesc(supplierUserId).stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .filter(p -> p.getValidUntil() == null || p.getValidUntil().isAfter(now))
                .map(InternalMaterialPriceController::view)
                .toList();
    }

    private static MaterialPrice view(SupplierMaterialPrice p) {
        return new MaterialPrice(p.getMaterialItem().getName(), p.getMaterialItem().getUnit().getLabel(), p.getPrice(),
                p.getBrand());
    }
}
