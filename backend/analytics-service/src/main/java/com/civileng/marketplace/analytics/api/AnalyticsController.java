package com.civileng.marketplace.analytics.api;

import com.civileng.marketplace.analytics.cdc.CaptureManager;
import com.civileng.marketplace.analytics.cdc.ClusterCapture;
import com.civileng.marketplace.analytics.warehouse.Kpis;
import com.civileng.marketplace.analytics.warehouse.Projector;
import com.civileng.marketplace.web.common.AccessDeniedException;
import com.civileng.marketplace.web.common.StaffRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Reads from the warehouse (architecture 02 §8): the operator sees platform KPIs across tenants;
 * a tenant's staff see their own tenant's — the tenant comes from the gateway's signed header, never
 * from the request, so no parameter can reach another tenant's partition.
 */
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private static final String OPERATOR = "platform";

    private final Kpis kpis;
    private final CaptureManager captures;
    private final Projector projector;

    /** The caller's own workspace. */
    @GetMapping("/workspace")
    public Kpis.Workspace workspace(@RequestHeader(value = "X-Tenant-Id", required = false) String tenant,
                                    @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (tenant == null || !StaffRoles.isStaff(role)) {
            throw new AccessDeniedException("Workspace analytics are for its staff");
        }
        return kpis.workspace(tenant);
    }

    /** Every tenant: the operator only. */
    @GetMapping("/platform")
    public Kpis.Platform platform(@RequestHeader(value = "X-Tenant-Id", required = false) String tenant,
                                  @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireOperator(tenant, role);
        return kpis.platform();
    }

    /** Capture lag and position per cluster. */
    @GetMapping("/capture")
    public List<ClusterCapture.Status> capture(@RequestHeader(value = "X-Tenant-Id", required = false) String tenant,
                                               @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireOperator(tenant, role);
        return captures.status();
    }

    /** A tenant's warehouse partition is deleted with the tenant. */
    @DeleteMapping("/tenants/{key}")
    public Map<String, Object> purge(@PathVariable String key,
                                     @RequestHeader(value = "X-Tenant-Id", required = false) String tenant,
                                     @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireOperator(tenant, role);
        if (!key.matches("[a-z0-9]{1,31}") || OPERATOR.equals(key)) {
            throw new IllegalArgumentException("Not a tenant whose analytics can be purged: " + key);
        }
        return Map.of("tenantKey", key, "rowsDeleted", projector.purge(key));
    }

    private static void requireOperator(String tenant, String role) {
        if (!OPERATOR.equals(tenant) || !"SUPER_ADMIN".equals(role)) {
            throw new AccessDeniedException("Platform analytics are for the operator's SUPER_ADMINs");
        }
    }
}
