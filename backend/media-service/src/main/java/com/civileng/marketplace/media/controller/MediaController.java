package com.civileng.marketplace.media.controller;

import com.civileng.marketplace.media.dto.MediaDTO;
import com.civileng.marketplace.media.dto.UploadRequest;
import com.civileng.marketplace.media.dto.UploadTicket;
import com.civileng.marketplace.media.service.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * The browser-facing API. Identity comes from the gateway's {@code X-User-Id} / {@code X-User-Role}
 * headers, which it sets from a verified JWT; the whole prefix sits behind the JWT filter.
 */
@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
@Tag(name = "Media", description = "Uploads and signed access to stored files")
public class MediaController {

    private final MediaService mediaService;

    @PostMapping("/uploads")
    @Operation(summary = "Start an upload: returns a signed form to POST the file to storage")
    public ResponseEntity<UploadTicket> requestUpload(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody UploadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mediaService.requestUpload(userId, role, request));
    }

    @PostMapping("/{mediaId}/complete")
    @Operation(summary = "Finish an upload: verifies the stored file and makes it available")
    public ResponseEntity<MediaDTO> complete(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable String mediaId) {
        return ResponseEntity.ok(mediaService.complete(userId, mediaId));
    }

    @GetMapping("/{mediaId}")
    @Operation(summary = "A file's details and a URL to open it (signed and short-lived if private)")
    public ResponseEntity<MediaDTO> get(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String mediaId) {
        return ResponseEntity.ok(mediaService.get(userId, role, mediaId));
    }

    @DeleteMapping("/{mediaId}")
    @Operation(summary = "Delete a file (its owner or staff)")
    public ResponseEntity<Void> delete(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String mediaId) {
        mediaService.delete(userId, role, mediaId);
        return ResponseEntity.noContent().build();
    }
}
