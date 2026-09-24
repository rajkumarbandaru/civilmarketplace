package com.civileng.marketplace.admin.config;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

/**
 * The workspace's published branding for anonymous visitors — the sign-in screen, before there is a
 * user to fetch /ui-config/me for. Only keys the schema marks public: name and logo. The gateway
 * routes this without a JWT and resolves the workspace from the Host.
 */
@RestController
@RequestMapping("/api/v1/ui-config/public")
@RequiredArgsConstructor
public class PublicBrandingController {

    private final ConfigService configService;
    private final com.civileng.marketplace.admin.uiconfig.service.UiConfigService uiConfigService;
    private final ThemePreviewTokens previewTokens;

    public record PublicBranding(String brandName, String logoUrl, long release) { }

    @GetMapping("/branding")
    @Operation(summary = "This workspace's published brand name and logo (no sign-in needed)")
    public ResponseEntity<PublicBranding> branding() {
        Map<String, Object> b = ConfigService.normalise(configService.live(ConfigScope.TENANT)
                .getOrDefault(ConfigDocument.BRANDING, Map.of()));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)).cachePublic())
                .body(new PublicBranding((String) b.get("brandName"), (String) b.get("logoUrl"),
                        configService.liveReleaseId(ConfigScope.TENANT)));
    }

    /**
     * The workspace's published look for anonymous visitors (05 §7.2 public bundle): colours,
     * style pack, layouts, branding — every key of these documents is public by nature. ETag is
     * the live release, so a client re-downloads only after a publish.
     */
    @GetMapping("/theme")
    @Operation(summary = "This workspace's published theme, style and layout (no sign-in needed)")
    public ResponseEntity<com.civileng.marketplace.admin.uiconfig.dto.UiConfigDTO.ResolvedTheme> theme() {
        var theme = uiConfigService.theme("PLATFORM");
        return ResponseEntity.ok()
                .eTag("\"t" + theme.version() + "\"")
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)).cachePublic())
                .body(theme);
    }

    /** An unpublished theme carried by a preview token issued in the theme editor. */
    @GetMapping("/preview/{token}")
    @Operation(summary = "The theme inside a preview token (15 minutes, this workspace only)")
    public ResponseEntity<com.civileng.marketplace.admin.uiconfig.dto.UiConfigDTO.ResolvedTheme> preview(
            @org.springframework.web.bind.annotation.PathVariable String token) {
        var values = previewTokens.read(token, com.civileng.marketplace.tenant.common.TenantContext.require());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(uiConfigService.previewTheme(values));
    }
}
