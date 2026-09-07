package com.civileng.marketplace.project.controller;

import com.civileng.marketplace.web.common.StaffRoles;
import com.civileng.marketplace.project.dto.ProjectSummary;
import com.civileng.marketplace.web.common.AccessDeniedException;
import com.civileng.marketplace.project.model.Project;
import com.civileng.marketplace.project.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


/**
 * Super Admin's read-only window for dispute investigation and platform reporting (ENT·01
 * actors). Deliberately read-only — no admin endpoint here mutates someone else's project.
 */
@RestController
@RequestMapping("/api/v1/admin/projects")
@RequiredArgsConstructor
@Tag(name = "Admin Projects", description = "Platform-wide project oversight")
public class AdminProjectController {


    private final ProjectService projectService;

    @GetMapping
    @Operation(summary = "List every project on the platform")
    public ResponseEntity<Page<Project>> list(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAdmin(role);
        return ResponseEntity.ok(projectService.listAllProjects(status, PageRequest.of(page, size)));
    }

    @GetMapping("/{projectId}/summary")
    @Operation(summary = "Budget-vs-actual rollup for any project")
    public ResponseEntity<ProjectSummary> summary(
            @RequestHeader(value = "X-User-Id", required = false) Long actorId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable Long projectId) {
        requireAdmin(role);
        return ResponseEntity.ok(projectService.getSummary(projectId, actorId, role));
    }

    /**
     * The role header is optional so a missing one produces a 403 here rather than a generic 500
     * from the framework before this method ever runs.
     */
    private void requireAdmin(String role) {
        if (!StaffRoles.isStaff(role)) {
            throw new AccessDeniedException("Admin role required");
        }
    }
}
