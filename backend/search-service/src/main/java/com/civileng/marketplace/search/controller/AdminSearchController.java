package com.civileng.marketplace.search.controller;

import com.civileng.marketplace.web.common.StaffRoles;
import com.civileng.marketplace.web.common.AccessDeniedException;
import com.civileng.marketplace.search.service.ReindexService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/search")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Search", description = "Admin endpoints for the search index")
public class AdminSearchController {


    private final ReindexService reindexService;

    /**
     * Rebuilds only the caller's own tenant's indices. A tenant's admin must not be able to spend
     * the platform's reindex budget on, or learn the document counts of, anyone else's tenant; the
     * cross-tenant sweep stays on the scheduler.
     */
    @PostMapping("/reindex")
    @Operation(summary = "Rebuild this tenant's search indices")
    public ResponseEntity<Map<String, Object>> reindex(
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!StaffRoles.isStaff(role)) {
            throw new AccessDeniedException("Admin role required");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.putAll(reindexService.reindexCurrentTenant());
        return ResponseEntity.ok(result);
    }
}
