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
 * The last menu overlay: one person's menu differing from their workspace's. Visibility only —
 * ordering and labels stay a workspace-level decision so a role's console stays recognisable
 * from one user to the next.
 * <p>
 * {@code userId} is a plain column with no foreign key: users live in auth-service's schema,
 * which this service cannot reference.
 */
@Entity
@Table(name = "ui_user_menu_override", uniqueConstraints =
        @UniqueConstraint(name = "uq_ui_user_menu_override", columnNames = {"user_id", "item_key"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserMenuOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "item_key", nullable = false, length = 64)
    private String itemKey;

    @Column(nullable = false)
    private boolean visible;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public UserMenuOverride(Long userId, String itemKey, boolean visible) {
        this.userId = userId;
        this.itemKey = itemKey;
        this.visible = visible;
    }
}
