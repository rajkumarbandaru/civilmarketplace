package com.civileng.marketplace.user.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One supplier's published rate for one material — the supply side of a material estimate, and the
 * only place on this platform where a material unit price is a fact rather than an assumption.
 *
 * <p>A supplier holds at most one active price per material per city. The city is part of that key
 * because a cement rate in one city says nothing about the rate two states away, and an estimate
 * that mixes them is worse than one that admits it has no local rate.
 *
 * <p>{@code validUntil} exists because a stale rate is the dangerous kind: an estimate quoting a
 * two-year-old price looks exactly like one quoting today's. Expired rows are excluded from the
 * ranges the estimator reads.
 */
@Entity
@Table(name = "supplier_material_prices",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_supplier_material_city",
                columnNames = {"supplier_user_id", "material_item_id", "city"}),
        indexes = {
                @Index(name = "idx_smp_supplier", columnList = "supplier_user_id"),
                @Index(name = "idx_smp_material", columnList = "material_item_id"),
                @Index(name = "idx_smp_city", columnList = "city")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierMaterialPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The supplier's user ID, not a foreign key to a profile. It is the reference the estimator
     * cites back to the customer, and it is what auth-service and every other service call a user.
     */
    @NotNull
    @Column(name = "supplier_user_id", nullable = false)
    private Long supplierUserId;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "material_item_id", nullable = false)
    private MaterialItem materialItem;

    /**
     * Rate per the material's unit. {@code BigDecimal} rather than {@code double}: this is money,
     * and it is summed into totals a customer reads.
     */
    @NotNull
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than zero")
    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    /** Where this rate applies. Required — a rate with no place attached cannot be compared. */
    @NotNull
    @Column(name = "city", nullable = false, length = 100)
    private String city;

    /** Optional brand or make, shown alongside the rate: "UltraTech", "Tata Tiscon". */
    @Column(name = "brand", length = 150)
    private String brand;

    /** Below this quantity the rate does not hold — an estimate should not apply it to a smaller lot. */
    @Column(name = "min_order_quantity", precision = 12, scale = 2)
    private BigDecimal minOrderQuantity;

    /** Whether delivery is in the rate, which is most of the gap between two otherwise equal quotes. */
    @Column(name = "delivery_included", nullable = false)
    @Builder.Default
    private Boolean deliveryIncluded = false;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /** Null means the supplier has not committed to an expiry; the rate is then treated as current. */
    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
