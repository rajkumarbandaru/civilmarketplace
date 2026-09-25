package com.civileng.marketplace.procurement.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
import java.util.List;

/** The material rates a supplier already published in user-service: the seed of its catalogue. */
@FeignClient(name = "user-service", contextId = "procurementMaterialPrices", path = "/api/v1/users/internal")
public interface MaterialPricesClient {

    @GetMapping("/material-prices/{supplierUserId}")
    List<MaterialPrice> pricesOf(@PathVariable("supplierUserId") Long supplierUserId);

    record MaterialPrice(String material, String unit, BigDecimal price, String brand) { }
}
