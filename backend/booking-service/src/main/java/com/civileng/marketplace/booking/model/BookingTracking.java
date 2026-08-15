package com.civileng.marketplace.booking.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * The assigned worker's most recent position for one booking.
 *
 * The id is the booking id rather than a generated key: there is exactly one current position per
 * booking, and making that a primary key means the database enforces it instead of the service
 * hoping for it. Writes are upserts, so a worker pinging every ten seconds never grows the table.
 */
@Entity
@Table(name = "booking_tracking")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingTracking {

    @Id
    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "worker_id")
    private Long workerId;

    // columnDefinition pins the SQL type for Hibernate's schema validation, exactly as Booking's
    // own location_lat/lng do. Without it a Double is validated against float(53) while the table
    // holds DECIMAL, and the service refuses to start — coordinates want fixed decimal precision,
    // so the column is right and the mapping has to say so.
    @Column(name = "worker_lat", nullable = false, columnDefinition = "DECIMAL(10,8)")
    private Double workerLat;

    @Column(name = "worker_lng", nullable = false, columnDefinition = "DECIMAL(11,8)")
    private Double workerLng;

    /** Degrees clockwise from north. Null when the device could not determine heading. */
    @Column(name = "heading_deg")
    private Integer headingDeg;

    @Column(name = "speed_kph", columnDefinition = "DECIMAL(5,1)")
    private Double speedKph;

    @Column(name = "note", length = 120)
    private String note;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
