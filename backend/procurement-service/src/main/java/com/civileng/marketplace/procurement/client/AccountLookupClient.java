package com.civileng.marketplace.procurement.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Which account, in the caller's workspace, an email belongs to (404 when none). */
@FeignClient(name = "auth-service", contextId = "procurementAccountLookup", path = "/api/v1/auth/internal")
public interface AccountLookupClient {

    @GetMapping("/accounts/by-email")
    AccountRef byEmail(@RequestParam("email") String email);

    record AccountRef(Long id, String email, String name) { }
}
