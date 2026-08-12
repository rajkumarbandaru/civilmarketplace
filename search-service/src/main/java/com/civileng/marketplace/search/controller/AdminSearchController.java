package com.civileng.marketplace.search.controller;

import com.civileng.marketplace.search.exception.AccessDeniedException;
import com.civileng.marketplace.search.service.ReindexService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin/search")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Search", description = "Admin endpoints for the search index")
public class AdminSearchController {

    private static final Set<String> ADMIN_ROLES =
            Set.of("SUPER_ADMIN", "ADMIN", "SUB_ADMIN", "REGIONAL_ADMIN");

    private final ReindexService reindexService;

    @PostMapping("/reindex")
    @Operation(summary = "Trigger a full rebuild of the search index")
    public ResponseEntity<Map<String, Object>> reindex(
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (role == null || !ADMIN_ROLES.contains(role)) {
            throw new AccessDeniedException("Admin role required");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.putAll(reindexService.reindexAll());
        return ResponseEntity.ok(result);
    }
}
