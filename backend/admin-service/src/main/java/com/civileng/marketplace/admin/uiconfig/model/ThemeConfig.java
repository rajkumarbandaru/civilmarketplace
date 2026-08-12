package com.civileng.marketplace.admin.uiconfig.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Theme and UI-style settings for one scope: either the literal {@code PLATFORM} row or a role
 * name. A workspace row overrides the platform row field by field, and a null field means
 * "inherit" rather than "no colour" — so setting one accent for Architects does not force every
 * other value to be restated.
 * <p>
 * {@code scopeKey} is the primary key rather than a generated id, which is what makes
 * "at most one row per scope" a database guarantee.
 */
@Entity
@Table(name = "ui_theme_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThemeConfig {

    public static final String PLATFORM_SCOPE = "PLATFORM";

    @Id
    @Column(name = "scope_key", nullable = false, length = 50)
    private String scopeKey;

    /** light | dark | system. Never null — every scope has to resolve to something paintable. */
    @Column(nullable = false, length = 10)
    @Builder.Default
    private String mode = "system";

    @Column(name = "primary_color", length = 9)
    private String primaryColor;

    @Column(name = "accent_color", length = 9)
    private String accentColor;

    @Column(name = "surface_color", length = 9)
    private String surfaceColor;

    /** The console/app sidebar. Separate from the surface colour — the shell's navigation is the
     * one area most brands want off-palette from the cards it sits next to. */
    @Column(name = "sidebar_color", length = 9)
    private String sidebarColor;

    @Column(name = "border_radius")
    private Integer borderRadius;

    @Column(name = "font_family", length = 200)
    private String fontFamily;

    /** Wordmark shown in the shell. Null keeps the shipped product name. */
    @Column(name = "brand_name", length = 60)
    private String brandName;

    /** Absolute or app-relative image URL shown beside the wordmark. */
    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "ui_style", length = 40)
    private String uiStyle;

    /** gradient | solid | outlined — how contained buttons are filled. */
    @Column(name = "button_style", length = 20)
    private String buttonStyle;

    @Column(name = "layout_style", length = 40)
    private String layoutStyle;

    @Column(length = 20)
    private String density;

    @Column(nullable = false)
    @Builder.Default
    private int version = 1;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public ThemeConfig(String scopeKey) {
        this.scopeKey = scopeKey;
        this.mode = "system";
        this.version = 1;
    }
}
