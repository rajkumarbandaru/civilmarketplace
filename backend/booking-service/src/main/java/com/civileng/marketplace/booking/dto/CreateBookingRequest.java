package com.civileng.marketplace.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CreateBookingRequest {

    @NotBlank(message = "Service category is required")
    private String serviceCategory;

    @NotBlank(message = "Service name is required")
    private String serviceName;

    @NotNull(message = "Booking type is required")
    private String bookingType;

    private String description;

    /** Optional Project (project-service) this booking is scoped to, with an optional Milestone. */
    private Long projectId;

    private Long milestoneId;

    private Long addressId;

    private Double locationLat;

    private Double locationLng;

    private String addressLine;

    @NotBlank(message = "City is required")
    private String city;

    private LocalDateTime scheduledDate;

    private Integer estimatedDurationMinutes;

    private BigDecimal estimatedCost;

    private Boolean isEmergency;

    private Boolean isRecurring;

    private String recurringFrequency;

    /**
     * PREPAID (pay now, confirmed only once payment succeeds) or POSTPAID (pay after the work).
     * Absent means PREPAID — the entity's default, and the safer reading of a client that has
     * not been updated to offer the choice.
     */
    private String paymentPreference;
}
