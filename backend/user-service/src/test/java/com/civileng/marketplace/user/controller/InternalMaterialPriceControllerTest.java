package com.civileng.marketplace.user.controller;

import com.civileng.marketplace.user.model.MaterialItem;
import com.civileng.marketplace.user.model.MaterialUnit;
import com.civileng.marketplace.user.model.SupplierMaterialPrice;
import com.civileng.marketplace.user.repository.SupplierMaterialPriceRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InternalMaterialPriceControllerTest {

    private final SupplierMaterialPriceRepository repo = mock(SupplierMaterialPriceRepository.class);
    private final InternalMaterialPriceController controller = new InternalMaterialPriceController(repo);

    private static SupplierMaterialPrice price(String name, String amount, boolean active, LocalDateTime validUntil) {
        MaterialItem item = MaterialItem.builder().name(name).unit(MaterialUnit.BAG).build();
        return SupplierMaterialPrice.builder().supplierUserId(20L).materialItem(item).price(new BigDecimal(amount))
                .brand("UltraTech").isActive(active).validUntil(validUntil).build();
    }

    @Test
    void onlyActiveUnexpiredRatesAreSeedMaterial() {
        when(repo.findBySupplierUserIdOrderByUpdatedAtDesc(20L)).thenReturn(List.of(
                price("OPC 53 Cement", "395", true, null),
                price("PPC Cement", "360", false, null),
                price("Old Cement", "300", true, LocalDateTime.now().minusDays(1))));
        assertThat(controller.pricesOf(20L)).containsExactly(
                new InternalMaterialPriceController.MaterialPrice("OPC 53 Cement", "bag", new BigDecimal("395"), "UltraTech"));
    }
}
