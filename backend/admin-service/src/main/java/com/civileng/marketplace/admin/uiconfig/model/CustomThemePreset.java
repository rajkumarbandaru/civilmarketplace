package com.civileng.marketplace.admin.uiconfig.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * A theme preset a Super Admin saved from the console, offered in the same picker as the shipped
 * ones in {@link com.civileng.marketplace.admin.uiconfig.service.ThemePresets}.
 *
 * <p>It stores style values only — no brand name or logo. A preset is a look, and carrying a
 * wordmark into one would mean applying it to a workspace renamed that workspace too.
 *
 * <p>Like a shipped preset, this row is never read to decide what a scope currently looks like:
 * applying it fills the console's form and the admin still presses save. Deleting one therefore
 * changes nothing that is already painted.
 */
@Entity
@Table(name = "ui_theme_preset")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomThemePreset {

    /** Slug derived from the label at creation, and never rewritten when the label changes. */
    @Id
    @Column(name = "preset_key", nullable = false, length = 60)
    private String presetKey;

    @Column(nullable = false, length = 60)
    private String label;

    @Column(length = 200)
    private String description;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String mode = "system";

    @Column(name = "primary_color", length = 9)
    private String primaryColor;

    @Column(name = "accent_color", length = 9)
    private String accentColor;

    @Column(name = "surface_color", length = 9)
    private String surfaceColor;

    @Column(name = "sidebar_color", length = 9)
    private String sidebarColor;

    @Column(name = "border_radius")
    private Integer borderRadius;

    @Column(name = "font_family", length = 200)
    private String fontFamily;

    @Column(name = "ui_style", length = 40)
    private String uiStyle;

    @Column(name = "button_style", length = 20)
    private String buttonStyle;

    @Column(name = "layout_style", length = 40)
    private String layoutStyle;

    @Column(length = 20)
    private String density;

    /** Who saved it — kept for the audit trail, not shown in the picker. */
    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
