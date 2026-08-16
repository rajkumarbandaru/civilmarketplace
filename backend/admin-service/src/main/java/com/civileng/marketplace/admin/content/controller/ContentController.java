package com.civileng.marketplace.admin.content.controller;

import com.civileng.marketplace.admin.content.dto.ContentDTO.SiteContent;
import com.civileng.marketplace.admin.content.model.MediaAsset;
import com.civileng.marketplace.admin.content.service.ContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

/**
 * The public read of the site's editable copy.
 *
 * <p>Unauthenticated, like the service catalogue it sits beside: this is what a signed-out visitor
 * lands on, and behind the gateway's auth filter the home page would render nothing until someone
 * logged in. Read-only, and returns only the enabled rows — nothing beyond what the page prints.
 */
@RestController
@RequestMapping("/api/v1/content")
@RequiredArgsConstructor
@Tag(name = "Site Content", description = "The public site's editable text, links and images")
public class ContentController {

    private final ContentService contentService;

    @GetMapping("/site")
    @Operation(summary = "Every enabled content section for the public pages")
    public ResponseEntity<SiteContent> site() {
        return ResponseEntity.ok(contentService.siteContent());
    }

    /**
     * An uploaded image. Cached for a day and immutable in practice — an edited image gets a new
     * id rather than replacing the bytes at this one, so a long cache cannot serve stale art.
     */
    @GetMapping("/media/{id}")
    @Operation(summary = "Fetch an uploaded image by id")
    public ResponseEntity<byte[]> media(@PathVariable Long id) {
        MediaAsset asset = contentService.mediaBytes(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .body(asset.getData());
    }
}
