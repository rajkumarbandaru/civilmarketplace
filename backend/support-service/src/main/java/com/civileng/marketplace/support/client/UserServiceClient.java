package com.civileng.marketplace.support.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

/**
 * Reads the supplier price list, so a material line in an estimate can quote what suppliers on
 * this platform actually charge instead of a market guess.
 *
 * <p>The aggregate endpoint is used rather than the raw rows: the low, high and median per
 * material — and the supplier behind each end — are computed where the data lives, so the
 * assistant is handed a range it cannot get wrong.
 */
@FeignClient(name = "user-service", contextId = "aiUserClient",
        path = "/api/v1/users/materials", fallbackFactory = UserServiceClientFallbackFactory.class)
public interface UserServiceClient {

    @GetMapping("/rates")
    Map<String, Object> materialRates();
}
