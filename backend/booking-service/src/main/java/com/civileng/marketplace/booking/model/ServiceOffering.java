package com.civileng.marketplace.booking.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * One bookable line in the public catalogue — a service, a material, a machine or a vehicle.
 *
 * This used to be a hard-coded array in the frontend (`constants/serviceCatalogue.ts`), which meant
 * adding a single item was a code change and a redeploy, and admins could see categories in the
 * console that had nothing to do with what the site actually showed. The rows are seeded from that
 * same list on first boot, so the catalogue starts identical to what it replaced and every change
 * from then on is data.
 *
 * The category is held as its plain name rather than a foreign key to {@link ServiceCategory}: the
 * public site groups and filters by that name, bookings already record `serviceCategory` as text,
 * and a rename should not silently repoint historical bookings. {@code CatalogueService} keeps the
 * two in step when a category is renamed.
 */
@Entity
@Table(name = "service_offerings", indexes = {
        @Index(name = "idx_offering_slug", columnList = "slug", unique = true),
        @Index(name = "idx_offering_category", columnList = "category")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceOffering {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** URL-safe id. Booking deep links are built from it, so it has to stay stable. */
    @Column(name = "slug", nullable = false, unique = true, length = 255)
    private String slug;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    /** Plain category name, matching {@link ServiceCategory#getName()}. */
    @Column(name = "category", nullable = false, length = 255)
    private String category;

    /** A Material-UI icon *name*; the frontend resolves it. */
    @Column(name = "icon", length = 120)
    private String icon;

    /** Display price as shown ("₹500/hr", "Quote") — free text because the units differ per trade. */
    @Column(name = "price", length = 60)
    private String price;

    @Column(name = "rating")
    @Builder.Default
    private Double rating = 0.0;

    @Column(name = "reviews")
    @Builder.Default
    private Integer reviews = 0;

    /**
     * Optional artwork for the card: a photo, a video or an animation (GIF/Lottie-style loop).
     *
     * A URL rather than an upload: the media is served from wherever the marketplace already hosts
     * its assets, and holding bytes in the catalogue table would make every list query drag them
     * along. Empty means the card falls back to {@link #icon}, which is what every seeded row does.
     */
    @Column(name = "media_url", length = 1000)
    private String mediaUrl;

    /** IMAGE, VIDEO or ANIMATION — decides whether the card renders an <img> or a <video>. */
    @Column(name = "media_type", length = 20)
    private String mediaType;

    /**
     * Comma-separated trade names people actually type ("rebar, tmt, sariya"), fed into search.
     * Kept as one column rather than a child table: it is edited as a single free-text field in the
     * admin form and never queried on its own.
     */
    @Column(name = "aliases", length = 1000)
    private String aliases;

    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
