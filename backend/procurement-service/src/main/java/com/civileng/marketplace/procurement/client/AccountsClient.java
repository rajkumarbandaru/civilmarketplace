package com.civileng.marketplace.procurement.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * auth-service's staff user listing — internal-only at the gateway, and staff-only in auth-service,
 * so it works only on behalf of a staff caller, whose identity the signed context carries.
 */
@FeignClient(name = "auth-service", contextId = "procurementAccounts", path = "/api/v1/auth/admin")
public interface AccountsClient {

    @GetMapping("/users")
    Map<String, Object> users(@RequestParam("role") String role, @RequestParam("page") int page,
                              @RequestParam("size") int size);

    /** The fields of one listed user that the migration reads. */
    record Account(Long id, String email, String name, String role, String status) { }
}
