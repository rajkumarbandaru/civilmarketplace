package com.civileng.marketplace.project.controller;

import com.civileng.marketplace.project.dto.*;
import com.civileng.marketplace.project.model.Milestone;
import com.civileng.marketplace.project.model.Project;
import com.civileng.marketplace.project.model.ProjectDocument;
import com.civileng.marketplace.project.model.ProjectStatusHistory;
import com.civileng.marketplace.project.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Projects", description = "Project and milestone management (SRS ENT-01)")
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    @Operation(summary = "Create a project")
    public ResponseEntity<Project> create(
            @RequestHeader("X-User-Id") Long ownerId,
            @Valid @RequestBody CreateProjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectService.createProject(ownerId, request));
    }

    @GetMapping
    @Operation(summary = "List the caller's projects")
    public ResponseEntity<Page<Project>> listMine(
            @RequestHeader("X-User-Id") Long ownerId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                projectService.listMyProjects(ownerId, status, PageRequest.of(page, size)));
    }

    /** Two segments, so it never collides with {@code /{projectId}}. */
    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "service", "project-service",
                "status", "UP",
                "timestamp", System.currentTimeMillis()
        ));
    }

    @GetMapping("/{projectId}")
    @Operation(summary = "Get a project")
    public ResponseEntity<Project> get(
            @RequestHeader("X-User-Id") Long actorId,
            @RequestHeader(value = "X-User-Role", required = false) String actorRole,
            @PathVariable Long projectId) {
        return ResponseEntity.ok(projectService.getProject(projectId, actorId, actorRole));
    }

    @PatchMapping("/{projectId}")
    @Operation(summary = "Update a project's scope, budget or dates")
    public ResponseEntity<Project> update(
            @RequestHeader("X-User-Id") Long actorId,
            @PathVariable Long projectId,
            @Valid @RequestBody UpdateProjectRequest request) {
        return ResponseEntity.ok(projectService.updateProject(projectId, actorId, request));
    }

    @PatchMapping("/{projectId}/status")
    @Operation(summary = "Transition a project's status")
    public ResponseEntity<Project> changeStatus(
            @RequestHeader("X-User-Id") Long actorId,
            @PathVariable Long projectId,
            @Valid @RequestBody StatusChangeRequest request) {
        return ResponseEntity.ok(projectService.changeStatus(projectId, actorId, request));
    }

    @DeleteMapping("/{projectId}")
    @Operation(summary = "Delete a project (blocked while bookings are in flight)")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-User-Id") Long actorId,
            @PathVariable Long projectId) {
        projectService.deleteProject(projectId, actorId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{projectId}/summary")
    @Operation(summary = "Budget-vs-actual rollup and percent complete")
    public ResponseEntity<ProjectSummary> summary(
            @RequestHeader("X-User-Id") Long actorId,
            @RequestHeader(value = "X-User-Role", required = false) String actorRole,
            @PathVariable Long projectId) {
        return ResponseEntity.ok(projectService.getSummary(projectId, actorId, actorRole));
    }

    @GetMapping("/{projectId}/history")
    @Operation(summary = "Status transition log")
    public ResponseEntity<List<ProjectStatusHistory>> history(
            @RequestHeader("X-User-Id") Long actorId,
            @RequestHeader(value = "X-User-Role", required = false) String actorRole,
            @PathVariable Long projectId) {
        return ResponseEntity.ok(projectService.getStatusHistory(projectId, actorId, actorRole));
    }

    // ------------------------------------------------------------------ milestones

    @PostMapping("/{projectId}/milestones")
    @Operation(summary = "Add a milestone")
    public ResponseEntity<Milestone> addMilestone(
            @RequestHeader("X-User-Id") Long actorId,
            @PathVariable Long projectId,
            @Valid @RequestBody MilestoneRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectService.addMilestone(projectId, actorId, request));
    }

    @GetMapping("/{projectId}/milestones")
    @Operation(summary = "List a project's milestones")
    public ResponseEntity<List<Milestone>> listMilestones(
            @RequestHeader("X-User-Id") Long actorId,
            @RequestHeader(value = "X-User-Role", required = false) String actorRole,
            @PathVariable Long projectId) {
        return ResponseEntity.ok(projectService.listMilestones(projectId, actorId, actorRole));
    }

    @PatchMapping("/{projectId}/milestones/{milestoneId}")
    @Operation(summary = "Update a milestone")
    public ResponseEntity<Milestone> updateMilestone(
            @RequestHeader("X-User-Id") Long actorId,
            @PathVariable Long projectId,
            @PathVariable Long milestoneId,
            @Valid @RequestBody MilestoneRequest request) {
        return ResponseEntity.ok(
                projectService.updateMilestone(projectId, milestoneId, actorId, request));
    }

    @PatchMapping("/{projectId}/milestones/{milestoneId}/state")
    @Operation(summary = "Change a milestone's completion state")
    public ResponseEntity<Milestone> changeMilestoneState(
            @RequestHeader("X-User-Id") Long actorId,
            @PathVariable Long projectId,
            @PathVariable Long milestoneId,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(projectService.changeMilestoneState(
                projectId, milestoneId, actorId, body.get("completionState")));
    }

    @DeleteMapping("/{projectId}/milestones/{milestoneId}")
    @Operation(summary = "Delete a milestone (soft)")
    public ResponseEntity<Void> deleteMilestone(
            @RequestHeader("X-User-Id") Long actorId,
            @PathVariable Long projectId,
            @PathVariable Long milestoneId) {
        projectService.deleteMilestone(projectId, milestoneId, actorId);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ documents

    @PostMapping("/{projectId}/documents")
    @Operation(summary = "Attach a document reference")
    public ResponseEntity<ProjectDocument> attachDocument(
            @RequestHeader("X-User-Id") Long actorId,
            @PathVariable Long projectId,
            @Valid @RequestBody AttachDocumentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectService.attachDocument(projectId, actorId, request));
    }

    @GetMapping("/{projectId}/documents")
    @Operation(summary = "List a project's documents")
    public ResponseEntity<List<ProjectDocument>> listDocuments(
            @RequestHeader("X-User-Id") Long actorId,
            @RequestHeader(value = "X-User-Role", required = false) String actorRole,
            @PathVariable Long projectId) {
        return ResponseEntity.ok(projectService.listDocuments(projectId, actorId, actorRole));
    }

    @DeleteMapping("/{projectId}/documents/{documentId}")
    @Operation(summary = "Remove a document reference")
    public ResponseEntity<Void> deleteDocument(
            @RequestHeader("X-User-Id") Long actorId,
            @PathVariable Long projectId,
            @PathVariable Long documentId) {
        projectService.deleteDocument(projectId, documentId, actorId);
        return ResponseEntity.noContent().build();
    }
}
