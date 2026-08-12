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
 * The per-workspace overlay: how one role's side menu differs from the catalogue default.
 * {@code sortOrder} and {@code labelOverride} are nullable — null means "inherit", which is what
 * lets a workspace pick up a later catalogue re-order for the items it never touched.
 */
@Entity
@Table(name = "ui_workspace_menu", uniqueConstraints =
        @UniqueConstraint(name = "uq_ui_workspace_menu", columnNames = {"role", "item_key"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceMenuEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String role;

    @Column(name = "item_key", nullable = false, length = 64)
    private String itemKey;

    @Column(nullable = false)
    @Builder.Default
    private boolean visible = true;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "label_override", length = 120)
    private String labelOverride;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public WorkspaceMenuEntry(String role, String itemKey) {
        this.role = role;
        this.itemKey = itemKey;
        this.visible = true;
    }
}
