package com.civileng.marketplace.media.controller;

import com.civileng.marketplace.media.dto.InternalMediaView;
import com.civileng.marketplace.media.service.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Service-to-service access, for owners of domain rules this service does not know — project
 * membership, say. The gateway refuses this prefix from outside (InternalOnlyPathFilter).
 */
@RestController
@RequestMapping("/api/v1/media/internal")
@RequiredArgsConstructor
public class InternalMediaController {

    private final MediaService mediaService;

    @GetMapping("/{mediaId}")
    @Operation(summary = "A file with a fresh URL, for a caller the calling service has authorised")
    public ResponseEntity<InternalMediaView> get(
            @PathVariable String mediaId,
            @RequestParam(required = false) Long onBehalfOf) {
        return ResponseEntity.ok(mediaService.getForService(mediaId, onBehalfOf));
    }
}
