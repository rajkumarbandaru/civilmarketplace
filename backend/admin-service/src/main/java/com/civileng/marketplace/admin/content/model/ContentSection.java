package com.civileng.marketplace.admin.content.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One editable block of the public site — the hero, the stats strip, a footer column.
 *
 * <p>The text slots are generic on purpose: which of them a section uses is the renderer's
 * business, not the schema's, so a section can gain a subtitle without a migration. See
 * {@code V10__site_content.sql} for what each seeded {@link #sectionKey} does with them.
 */
@Entity
@Table(name = "site_content_sections")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** HOME | FOOTER | GLOBAL. */
    @Column(name = "page_key", nullable = false, length = 40)
    private String pageKey;

    /** What the renderer looks the section up by. Immutable once seeded. */
    @Column(name = "section_key", nullable = false, unique = true, length = 80)
    private String sectionKey;

    @Column(length = 300)
    private String title;

    @Column(length = 600)
    private String subtitle;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "link_label", length = 120)
    private String linkLabel;

    @Column(name = "link_url", length = 500)
    private String linkUrl;

    /** Footer only: which column this group is stacked into. */
    @Column(name = "column_index", nullable = false)
    private int columnIndex;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    /**
     * A seeded section the renderer looks up by key. Deleting one would leave the page falling
     * back to its shipped copy rather than showing nothing, which reads as a bug — so these are
     * hidden with {@link #enabled} instead of deleted.
     */
    @Column(name = "system_owned", nullable = false)
    @Builder.Default
    private boolean systemOwned = false;

    @OneToMany(mappedBy = "section", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    @Builder.Default
    private List<ContentItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
