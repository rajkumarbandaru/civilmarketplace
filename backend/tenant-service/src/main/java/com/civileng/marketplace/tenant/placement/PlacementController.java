package com.civileng.marketplace.tenant.placement;

import com.civileng.marketplace.web.common.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Where tenants' data lives and moving it: the operator tenant's SUPER_ADMINs only, like all of
 * tenant administration.
 */
@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
public class PlacementController {

    private final MoveService moves;

    public record MoveRequest(String targetClusterId) { }

    public record MaintenanceRequest(boolean enabled, String reason) { }

    /** Pause or resume a tenant's writes (reads go on), e.g. to swap in restored data. */
    @PostMapping("/{key}/maintenance")
    public MoveService.PlacementView maintenance(@PathVariable String key, @RequestBody MaintenanceRequest request,
                                                 @RequestHeader(value = "X-User-Id", required = false) String actor,
                                                 @RequestHeader(value = "X-User-Role", required = false) String role,
                                                 @RequestHeader(value = "X-Tenant-Id", required = false) String tenant) {
        requireOperator(role, tenant);
        if ("platform".equals(key)) {
            throw new IllegalArgumentException("The operator tenant cannot be paused: its console is how you would resume it");
        }
        return moves.maintenance(key, request.enabled(), request.reason(), actor);
    }

    @GetMapping("/clusters")
    public List<MoveService.ClusterView> clusters(@RequestHeader(value = "X-User-Role", required = false) String role,
                                                  @RequestHeader(value = "X-Tenant-Id", required = false) String tenant) {
        requireOperator(role, tenant);
        return moves.clusters();
    }

    @GetMapping("/{key}/placement")
    public MoveService.PlacementView placement(@PathVariable String key,
                                               @RequestHeader(value = "X-User-Role", required = false) String role,
                                               @RequestHeader(value = "X-Tenant-Id", required = false) String tenant) {
        requireOperator(role, tenant);
        return moves.placement(key);
    }

    @GetMapping("/{key}/moves")
    public List<MoveService.MoveView> history(@PathVariable String key,
                                              @RequestHeader(value = "X-User-Role", required = false) String role,
                                              @RequestHeader(value = "X-Tenant-Id", required = false) String tenant) {
        requireOperator(role, tenant);
        return moves.history(key);
    }

    @PostMapping("/{key}/moves")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MoveService.MoveView move(@PathVariable String key, @RequestBody MoveRequest request,
                                     @RequestHeader(value = "X-User-Id", required = false) String actor,
                                     @RequestHeader(value = "X-User-Role", required = false) String role,
                                     @RequestHeader(value = "X-Tenant-Id", required = false) String tenant) {
        requireOperator(role, tenant);
        return moves.start(key, request.targetClusterId(), actor);
    }

    @PostMapping("/{key}/moves/{id}/rollback")
    public MoveService.MoveView rollback(@PathVariable String key, @PathVariable Long id,
                                         @RequestHeader(value = "X-User-Id", required = false) String actor,
                                         @RequestHeader(value = "X-User-Role", required = false) String role,
                                         @RequestHeader(value = "X-Tenant-Id", required = false) String tenant) {
        requireOperator(role, tenant);
        return moves.rollback(key, id, actor);
    }

    @DeleteMapping("/{key}/moves/{id}/source")
    public MoveService.MoveView dropSource(@PathVariable String key, @PathVariable Long id,
                                           @RequestHeader(value = "X-User-Id", required = false) String actor,
                                           @RequestHeader(value = "X-User-Role", required = false) String role,
                                           @RequestHeader(value = "X-Tenant-Id", required = false) String tenant) throws Exception {
        requireOperator(role, tenant);
        return moves.dropSource(key, id, actor);
    }

    private static void requireOperator(String role, String tenantId) {
        if (!"platform".equals(tenantId) || !"SUPER_ADMIN".equals(role)) {
            throw new AccessDeniedException("Tenant placement is restricted to the operator tenant's SUPER_ADMINs");
        }
    }
}
