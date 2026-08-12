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
 * One member's own appearance preference — the last layer of the overlay, applied over the
 * workspace theme Super Admin set.
 * <p>
 * Deliberately only two fields. Everything about how a workspace <em>looks and is positioned</em>
 * (colours, font, radius, UI style, and the navigation's position) belongs to Super Admin so it
 * stays consistent for everyone in that workspace; a member gets the two settings that are about
 * their own eyes and screen. There is no column for a layout or a colour here, so a member cannot
 * set one even by calling the API directly.
 * <p>
 * {@code userId} is the primary key, which makes "at most one preference per member" a database
 * guarantee. A null field means "follow the workspace" rather than "no value".
 */
@Entity
@Table(name = "ui_user_appearance")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAppearance {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** light | dark | system, or null to follow the workspace. */
    @Column(name = "color_mode", length = 10)
    private String colorMode;

    /** compact | comfortable | spacious, or null to follow the workspace. */
    @Column(length = 20)
    private String density;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public UserAppearance(Long userId) {
        this.userId = userId;
    }

    /** True once both fields are back to "follow the workspace", so the row can be deleted. */
    public boolean isEmpty() {
        return colorMode == null && density == null;
    }
}
