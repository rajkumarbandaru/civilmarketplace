package com.civileng.marketplace.admin.content.controller;

import com.civileng.marketplace.admin.content.dto.ContentDTO.*;
import com.civileng.marketplace.admin.content.service.ContentService;
import com.civileng.marketplace.admin.exception.AccessDeniedException;
import com.civileng.marketplace.audit.common.AuditAction;
import com.civileng.marketplace.audit.common.AuditEventMessage;
import com.civileng.marketplace.audit.common.AuditPublisher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Super Admin's control of the public site's copy: the landing page's sections, the footer's
 * columns and links, the shared logo, and the images any of them use.
 *
 * <p>Gated on SUPER_ADMIN alone, matching the theme screen — this changes what every visitor sees
 * before they have even signed in, which is a heavier decision than moderating one booking. Every
 * write is audited, because "who changed the homepage headline" is exactly the question that gets
 * asked after the fact.
 */
@RestController
@RequestMapping("/api/v1/admin/content")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Site Content", description = "Super Admin control of the public site's text, links and images")
public class AdminContentController {

    private static final String SOURCE = "admin-service";
    private static final String SECTION_ENTITY = "SiteContentSection";
    private static final String ITEM_ENTITY = "SiteContentItem";
    private static final String MEDIA_ENTITY = "SiteContentMedia";

    private final ContentService contentService;
    private final AuditPublisher auditPublisher;

    // -------------------------------------------------------------------------------- sections

    @GetMapping("/sections")
    @Operation(summary = "Every content section, including the hidden ones")
    public ResponseEntity<List<Section>> sections(
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireSuperAdmin(role);
        return ResponseEntity.ok(contentService.allSections());
    }

    @PostMapping("/sections")
    @Operation(summary = "Add a content section")
    public ResponseEntity<Section> createSection(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Id", required = false) Long adminId,
            @RequestBody SectionCommand command) {
        requireSuperAdmin(role);
        Section created = contentService.createSection(command);
        audit(adminId, AuditAction.CREATE, SECTION_ENTITY, created.sectionKey(), command.toString());
        return ResponseEntity.ok(created);
    }

    @PutMapping("/sections/{id}")
    @Operation(summary = "Edit a content section's text, image, link or visibility")
    public ResponseEntity<Section> updateSection(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Id", required = false) Long adminId,
            @PathVariable Long id,
            @RequestBody SectionCommand command) {
        requireSuperAdmin(role);
        Section saved = contentService.updateSection(id, command);
        audit(adminId, AuditAction.UPDATE, SECTION_ENTITY, saved.sectionKey(), command.toString());
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/sections/{id}")
    @Operation(summary = "Delete an admin-created section (built-in ones can only be hidden)")
    public ResponseEntity<Void> deleteSection(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Id", required = false) Long adminId,
            @PathVariable Long id) {
        requireSuperAdmin(role);
        contentService.deleteSection(id);
        audit(adminId, AuditAction.DELETE, SECTION_ENTITY, String.valueOf(id), null);
        return ResponseEntity.noContent().build();
    }

    // ----------------------------------------------------------------------------------- items

    @PostMapping("/sections/{sectionId}/items")
    @Operation(summary = "Add an item — a stat, a step, a footer link, a social icon")
    public ResponseEntity<Item> addItem(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Id", required = false) Long adminId,
            @PathVariable Long sectionId,
            @RequestBody ItemCommand command) {
        requireSuperAdmin(role);
        Item created = contentService.addItem(sectionId, command);
        audit(adminId, AuditAction.CREATE, ITEM_ENTITY, String.valueOf(created.id()), command.toString());
        return ResponseEntity.ok(created);
    }

    @PutMapping("/items/{itemId}")
    @Operation(summary = "Edit one item's text, icon, image or link target")
    public ResponseEntity<Item> updateItem(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Id", required = false) Long adminId,
            @PathVariable Long itemId,
            @RequestBody ItemCommand command) {
        requireSuperAdmin(role);
        Item saved = contentService.updateItem(itemId, command);
        audit(adminId, AuditAction.UPDATE, ITEM_ENTITY, String.valueOf(itemId), command.toString());
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/items/{itemId}")
    @Operation(summary = "Delete one item")
    public ResponseEntity<Void> deleteItem(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Id", required = false) Long adminId,
            @PathVariable Long itemId) {
        requireSuperAdmin(role);
        contentService.deleteItem(itemId);
        audit(adminId, AuditAction.DELETE, ITEM_ENTITY, String.valueOf(itemId), null);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/sections/{sectionId}/items/order")
    @Operation(summary = "Re-order a section's items")
    public ResponseEntity<Section> reorderItems(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable Long sectionId,
            @RequestBody ReorderCommand command) {
        requireSuperAdmin(role);
        return ResponseEntity.ok(contentService.reorderItems(sectionId, command.ids()));
    }

    // ----------------------------------------------------------------------------------- media

    @PostMapping(value = "/media", consumes = "multipart/form-data")
    @Operation(summary = "Upload an image for a section, an item, or the logo")
    public ResponseEntity<Media> upload(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Id", required = false) Long adminId,
            @RequestPart("file") MultipartFile file) {
        requireSuperAdmin(role);
        Media uploaded = contentService.upload(file, adminId);
        audit(adminId, AuditAction.CREATE, MEDIA_ENTITY, String.valueOf(uploaded.id()), uploaded.filename());
        return ResponseEntity.ok(uploaded);
    }

    @GetMapping("/media")
    @Operation(summary = "Every uploaded image, newest first")
    public ResponseEntity<List<Media>> media(
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireSuperAdmin(role);
        return ResponseEntity.ok(contentService.listMedia());
    }

    @DeleteMapping("/media/{id}")
    @Operation(summary = "Delete an uploaded image")
    public ResponseEntity<Void> deleteMedia(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Id", required = false) Long adminId,
            @PathVariable Long id) {
        requireSuperAdmin(role);
        contentService.deleteMedia(id);
        audit(adminId, AuditAction.DELETE, MEDIA_ENTITY, String.valueOf(id), null);
        return ResponseEntity.noContent().build();
    }

    private static void requireSuperAdmin(String role) {
        if (!"SUPER_ADMIN".equals(role)) {
            throw new AccessDeniedException("SUPER_ADMIN role required to change the site's content");
        }
    }

    private void audit(Long actorId, AuditAction action, String entityType, String entityId, String after) {
        auditPublisher.publish(AuditEventMessage.builder()
                .sourceService(SOURCE)
                .actorId(actorId)
                .actorRole("SUPER_ADMIN")
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .afterState(after)
                .build());
    }
}
