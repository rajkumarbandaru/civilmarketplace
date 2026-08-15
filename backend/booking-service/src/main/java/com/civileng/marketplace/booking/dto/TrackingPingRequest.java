package com.civileng.marketplace.booking.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * A position report from the worker's device.
 *
 * The bounds are the real limits of latitude and longitude, not arbitrary validation: a client
 * that swaps the two fields — the single most common mistake with coordinate pairs — produces an
 * out-of-range latitude for most of the world and is rejected here rather than silently drawing
 * the worker into the sea.
 */
@Data
public class TrackingPingRequest {

    @NotNull
    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private Double lat;

    @NotNull
    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private Double lng;

    @DecimalMin("0.0")
    @DecimalMax("360.0")
    private Integer headingDeg;

    @DecimalMin("0.0")
    private Double speedKph;

    @Size(max = 120)
    private String note;
}
