package com.civileng.marketplace.admin.content.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/** One row inside a {@link ContentSection}: a stat, a step, a footer link, a social icon. */
@Entity
@Table(name = "site_content_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Excluded from equals/hashCode/toString: the parent holds the child list, so including the
    // back-reference recurses.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private ContentSection section;

    @Column(length = 300)
    private String title;

    @Column(length = 600)
    private String subtitle;

    @Column(columnDefinition = "TEXT")
    private String body;

    /** A Material-UI icon name, resolved client-side by DynamicIcon. */
    @Column(length = 60)
    private String icon;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /** In-app path, absolute URL, or empty for an item that is not a link. */
    @Column(name = "link_url", length = 500)
    private String linkUrl;

    /** Small leading label — the step number on How It Works, a tag anywhere else. */
    @Column(length = 60)
    private String badge;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
