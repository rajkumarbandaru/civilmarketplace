package com.civileng.marketplace.tenant.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.civileng.marketplace.tenant.common.TenantBranding;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "tenants")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Immutable once created — it names this tenant's schema in all eleven service databases. */
    @Column(name = "tenant_key", nullable = false, unique = true, length = 31, updatable = false)
    private String tenantKey;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, unique = true, length = 63)
    private String subdomain;

    @Column(name = "custom_domain", unique = true, length = 253)
    private String customDomain;

    // Branding chosen at onboarding. Read and written as a whole through the branding() /
    // applyBranding() pair below, so callers deal in TenantBranding rather than nine loose fields.

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "primary_color", length = 9)
    private String primaryColor;

    @Column(name = "accent_color", length = 9)
    private String accentColor;

    @Column(name = "surface_color", length = 9)
    private String surfaceColor;

    @Column(name = "sidebar_color", length = 9)
    private String sidebarColor;

    @Column(name = "color_mode", length = 10)
    private String colorMode;

    @Column(name = "border_radius")
    private Integer borderRadius;

    @Column(name = "font_family", length = 200)
    private String fontFamily;

    @Column(name = "brand_name", length = 60)
    private String brandName;

    /** Which shipped palette this one started from. Console bookkeeping; nothing renders from it. */
    @Column(name = "preset_key", length = 60)
    private String presetKey;

    @Column(name = "ui_style", length = 40)
    private String uiStyle;

    @Column(name = "button_style", length = 20)
    private String buttonStyle;

    @Column(name = "layout_style", length = 40)
    private String layoutStyle;

    @Column(length = 20)
    private String density;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private TenantStatus status;

    @Column(name = "contact_email", nullable = false, length = 150)
    private String contactEmail;

    @Column(nullable = false, length = 30)
    private String plan;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 40)
    private Vertical vertical;

    /** CSV of {@link PlatformModule} keys. Read through {@link #moduleKeys()}. */
    @Column(name = "enabled_modules", nullable = false, columnDefinition = "TEXT")
    private String enabledModules;

    /**
     * Where this tenant's console opens. Null means the shipped dashboard.
     *
     * <p>A path rather than a menu item key, because that is what the shell can act on without a
     * catalogue lookup — and because the operator picks it from the tenant's own resolved menu, so
     * it has already been checked against a real item by the time it lands here.
     */
    @Column(name = "landing_path", length = 200)
    private String landingPath;

    public java.util.Set<String> moduleKeys() {
        if (enabledModules == null || enabledModules.isBlank()) {
            return java.util.Set.of();
        }
        return new java.util.LinkedHashSet<>(
                java.util.Arrays.asList(enabledModules.split("\\s*,\\s*")));
    }

    /**
     * Written by Hibernate rather than left to the column default, so the row returned from a
     * create carries its own timestamp — with {@code insertable = false} the caller got a null
     * createdAt back and had to re-read the tenant to find out when it was made.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    /** The branding fields as one object, for the API response and the tenant.events message. */
    public TenantBranding branding() {
        return TenantBranding.builder()
                .logoUrl(logoUrl)
                .primaryColor(primaryColor)
                .accentColor(accentColor)
                .surfaceColor(surfaceColor)
                .sidebarColor(sidebarColor)
                .borderRadius(borderRadius)
                .fontFamily(fontFamily)
                .brandName(brandName)
                .presetKey(presetKey)
                .colorMode(colorMode)
                .uiStyle(uiStyle)
                .buttonStyle(buttonStyle)
                .layoutStyle(layoutStyle)
                .density(density)
                .build();
    }

    public void applyBranding(TenantBranding branding) {
        if (branding == null) {
            return;
        }
        this.logoUrl = branding.getLogoUrl();
        this.primaryColor = branding.getPrimaryColor();
        this.accentColor = branding.getAccentColor();
        this.surfaceColor = branding.getSurfaceColor();
        this.sidebarColor = branding.getSidebarColor();
        this.borderRadius = branding.getBorderRadius();
        this.fontFamily = branding.getFontFamily();
        this.brandName = branding.getBrandName();
        this.presetKey = branding.getPresetKey();
        this.colorMode = branding.getColorMode();
        this.uiStyle = branding.getUiStyle();
        this.buttonStyle = branding.getButtonStyle();
        this.layoutStyle = branding.getLayoutStyle();
        this.density = branding.getDensity();
    }
}
