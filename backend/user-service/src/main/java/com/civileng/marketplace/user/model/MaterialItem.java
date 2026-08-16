package com.civileng.marketplace.user.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * A material the platform knows how to price — one row per thing a supplier can quote against.
 *
 * <p>Suppliers quote against this shared catalogue rather than typing their own material names.
 * That is what makes a low and a high rate comparable: "OPC 53 Grade Cement" from one supplier and
 * "cement (53)" from another are the same material to a reader and two different materials to a
 * grouping query, and the estimator ranges rates by grouping.
 */
@Entity
@Table(name = "material_items", indexes = {
        @Index(name = "idx_material_slug", columnList = "slug", unique = true),
        @Index(name = "idx_material_category", columnList = "category")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** Stable handle for the catalogue row, so a renamed material keeps its identity. */
    @NotBlank
    @Column(name = "slug", nullable = false, unique = true, length = 200)
    private String slug;

    /** BOQ section this material usually belongs to — "Concrete", "Masonry", "Finishes". */
    @Column(name = "category", length = 100)
    private String category;

    /**
     * The unit every supplier must quote this material in.
     *
     * <p>The explicit VARCHAR type code matters: without it Hibernate 6 expects a native MySQL
     * {@code ENUM} column and schema validation fails against the {@code VARCHAR} the migration
     * creates.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "unit", nullable = false, length = 20)
    private MaterialUnit unit;

    @Column(name = "specification", length = 500)
    private String specification;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
