package com.civileng.marketplace.booking.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings", indexes = {
        @Index(name = "idx_booking_customer", columnList = "customer_id"),
        @Index(name = "idx_booking_worker", columnList = "worker_id"),
        @Index(name = "idx_booking_status", columnList = "status"),
        @Index(name = "idx_booking_scheduled", columnList = "scheduled_date"),
        @Index(name = "idx_booking_code", columnList = "booking_code", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_code", nullable = false, unique = true, length = 30)
    private String bookingCode;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "worker_id")
    private Long workerId;

    /**
     * The Project this booking belongs to, owned by project-service. Nullable — a direct booking
     * with no project is normal, and every pre-Projects booking has none.
     */
    @Column(name = "project_id")
    private Long projectId;

    /** Optional Milestone within {@link #projectId} that this booking delivers. */
    @Column(name = "milestone_id")
    private Long milestoneId;

    @Column(name = "service_category", length = 100)
    private String serviceCategory;

    @Column(name = "service_name", length = 255)
    private String serviceName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "booking_type", nullable = false, length = 30)
    private BookingType bookingType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private BookingStatus status = BookingStatus.PENDING;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "address_id")
    private Long addressId;

    @Column(name = "location_lat", columnDefinition = "DECIMAL(10,8)")
    private Double locationLat;

    @Column(name = "location_lng", columnDefinition = "DECIMAL(11,8)")
    private Double locationLng;

    @Column(name = "address_line", length = 500)
    private String addressLine;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "scheduled_date")
    private LocalDateTime scheduledDate;

    @Column(name = "estimated_duration_minutes")
    private Integer estimatedDurationMinutes;

    @Column(name = "estimated_cost")
    private BigDecimal estimatedCost;

    @Column(name = "final_cost")
    private BigDecimal finalCost;

    @Column(name = "platform_fee")
    private BigDecimal platformFee;

    @Column(name = "gst_amount")
    private BigDecimal gstAmount;

    @Column(name = "total_amount")
    private BigDecimal totalAmount;

    @Column(name = "payment_status", length = 30)
    @Builder.Default
    private String paymentStatus = "PENDING";

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "is_emergency", nullable = false)
    @Builder.Default
    private Boolean isEmergency = false;

    @Column(name = "is_recurring", nullable = false)
    @Builder.Default
    private Boolean isRecurring = false;

    @Column(name = "recurring_frequency", length = 20)
    private String recurringFrequency;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
