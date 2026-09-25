package com.civileng.marketplace.tenant.domain;

import com.civileng.marketplace.web.common.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** A tenant's custom domains. The operator tenant's SUPER_ADMINs, like all of tenant administration. */
@RestController
@RequestMapping("/api/v1/tenants/{key}/domains")
@RequiredArgsConstructor
public class DomainController {

    private final DomainService domains;

    public record AddDomainRequest(String host, TenantDomain.Surface surface) { }

    @GetMapping
    public List<DomainService.DomainView> list(@PathVariable String key,
                                               @RequestHeader(value = "X-User-Role", required = false) String role,
                                               @RequestHeader(value = "X-Tenant-Id", required = false) String tenant) {
        requireOperator(role, tenant);
        return domains.list(key);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DomainService.DomainView add(@PathVariable String key, @RequestBody AddDomainRequest request,
                                        @RequestHeader(value = "X-User-Id", required = false) String actor,
                                        @RequestHeader(value = "X-User-Role", required = false) String role,
                                        @RequestHeader(value = "X-Tenant-Id", required = false) String tenant) {
        requireOperator(role, tenant);
        return domains.add(key, request.host(), request.surface(), actor);
    }

    @PostMapping("/{id}/check")
    public DomainService.DomainView check(@PathVariable String key, @PathVariable Long id,
                                          @RequestHeader(value = "X-User-Role", required = false) String role,
                                          @RequestHeader(value = "X-Tenant-Id", required = false) String tenant) {
        requireOperator(role, tenant);
        return domains.checkNow(key, id);
    }

    @DeleteMapping("/{id}")
    public DomainService.DomainView remove(@PathVariable String key, @PathVariable Long id,
                                           @RequestHeader(value = "X-User-Id", required = false) String actor,
                                           @RequestHeader(value = "X-User-Role", required = false) String role,
                                           @RequestHeader(value = "X-Tenant-Id", required = false) String tenant) throws Exception {
        requireOperator(role, tenant);
        return domains.remove(key, id, actor);
    }

    private static void requireOperator(String role, String tenantId) {
        if (!"platform".equals(tenantId) || !"SUPER_ADMIN".equals(role)) {
            throw new AccessDeniedException("Custom domains are managed by the operator tenant's SUPER_ADMINs");
        }
    }
}
