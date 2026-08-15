package com.civileng.marketplace.admin.uiconfig.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * One navigable destination the shell knows about. Rows are seeded by Flyway and added when a
 * screen is added — admins re-order and hide them, but do not invent them, because a menu entry
 * pointing at a route the client cannot render is a dead link.
 */
@Entity
@Table(name = "ui_menu_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuItemDefinition {

    /** The catalogue's "every role" wildcard, so member-facing rows need not list all 24. */
    public static final String ALL_ROLES = "*";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_key", nullable = false, unique = true, length = 64)
    private String itemKey;

    @Column(nullable = false, length = 120)
    private String label;

    @Column(nullable = false, length = 180)
    private String path;

    /** A Material-UI icon name; the frontend's icon map turns it into a component. */
    @Column(nullable = false, length = 60)
    private String icon;

    @Column(nullable = false, length = 40)
    private String section;

    /**
     * Where the item sits within its section — the sidebar's sub-heading. Null renders ungrouped,
     * which is what the member-facing rows want and what a new catalogue row gets until it is
     * placed. A group's position is the position of its first item, so there is no separate
     * ordering to keep in step with {@link #sortOrder}.
     */
    @Column(name = "menu_group", length = 40)
    private String menuGroup;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** React Router's {@code end} prop — index routes must not stay highlighted on child pages. */
    @Column(name = "exact_match", nullable = false)
    private boolean exactMatch;

    @Column(name = "default_roles", nullable = false, columnDefinition = "TEXT")
    private String defaultRoles;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public List<String> defaultRoleList() {
        if (defaultRoles == null || defaultRoles.isBlank()) return List.of();
        return Arrays.stream(defaultRoles.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public boolean isDefaultVisibleFor(String role) {
        List<String> roles = defaultRoleList();
        return roles.contains(ALL_ROLES) || roles.contains(role);
    }
}
