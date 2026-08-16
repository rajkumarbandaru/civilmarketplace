package com.civileng.marketplace.booking.controller;

import com.civileng.marketplace.booking.dto.TrackingPingRequest;
import com.civileng.marketplace.booking.event.BookingEventPublisher;
import com.civileng.marketplace.booking.model.Booking;
import com.civileng.marketplace.booking.model.BookingTracking;
import com.civileng.marketplace.booking.repository.BookingRepository;
import com.civileng.marketplace.booking.repository.BookingTrackingRepository;
import com.civileng.marketplace.booking.service.EtaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Live position of the worker or vehicle travelling to a booking.
 *
 * Deliberately request/response rather than a socket: a fix every few seconds is all a customer
 * watching someone approach can use, and polling costs one row read where a persistent connection
 * per waiting customer would cost a held thread. If sub-second updates are ever needed, this is
 * the contract to put a socket behind — not a reason to start with one.
 */
@RestController
@RequestMapping("/api/v1/bookings/{bookingId}/tracking")
@RequiredArgsConstructor
@Tag(name = "Booking Tracking", description = "Live worker location for an active booking")
public class BookingTrackingController {

    private static final Set<String> ADMIN_ROLES =
            Set.of("SUPER_ADMIN", "ADMIN", "SUB_ADMIN", "REGIONAL_ADMIN");

    /**
     * How long a fix stays believable. Past this the UI is told the position is stale rather than
     * being shown a worker frozen mid-street, which reads as "not moving" instead of "we have lost
     * contact" — a difference the customer deciding whether to keep waiting depends on.
     */
    private static final Duration FIX_FRESHNESS = Duration.ofMinutes(2);

    /** Close enough to tell the customer, whatever the traffic says. */
    private static final double ARRIVAL_RADIUS_KM = 1.0;

    /** Or far enough away in kilometres but minutes out in practice. */
    private static final int ARRIVAL_ETA_MINUTES = 5;

    /**
     * Only pings inside this radius are worth a routing lookup. Five kilometres is comfortably
     * further than five minutes of city driving, so nothing that could qualify is missed, while a
     * worker still crossing town costs nothing.
     */
    private static final double ARRIVAL_LOOKUP_RADIUS_KM = 5.0;

    private final BookingRepository bookingRepository;
    private final BookingTrackingRepository trackingRepository;
    private final EtaService etaService;
    private final BookingEventPublisher events;

    @PutMapping
    @Operation(summary = "Report the worker's current position (worker or admin only)")
    public ResponseEntity<BookingTracking> ping(
            @RequestHeader("X-User-Id") Long actorId,
            @RequestHeader(value = "X-User-Role", required = false) String actorRole,
            @PathVariable Long bookingId,
            @Valid @RequestBody TrackingPingRequest request) {

        Booking booking = booking(bookingId);

        // Only the person actually travelling may say where they are. Letting the customer write
        // here would let them fabricate an arrival, and letting any signed-in user write would let
        // a stranger move someone else's worker across the map.
        boolean isAssignedWorker = booking.getWorkerId() != null
                && booking.getWorkerId().equals(actorId);
        if (!isAssignedWorker && !isAdmin(actorRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the assigned worker can report position for this booking");
        }

        BookingTracking tracking = trackingRepository.findById(bookingId)
                .orElseGet(() -> BookingTracking.builder().bookingId(bookingId).build());
        tracking.setWorkerId(booking.getWorkerId());
        tracking.setWorkerLat(request.getLat());
        tracking.setWorkerLng(request.getLng());
        tracking.setHeadingDeg(request.getHeadingDeg());
        tracking.setSpeedKph(request.getSpeedKph());
        tracking.setNote(request.getNote());

        BookingTracking saved = trackingRepository.save(tracking);
        maybeAnnounceArrival(booking, saved);
        return ResponseEntity.ok(saved);
    }

    /**
     * Tells the customer once when the worker is nearly there.
     *
     * <p>"Nearly there" is within {@value #ARRIVAL_RADIUS_KM} km <em>or</em> an ETA at or under
     * {@value #ARRIVAL_ETA_MINUTES} minutes — either alone is misleading: a kilometre through
     * traffic can be fifteen minutes, and five minutes on a clear road can be three kilometres.
     *
     * <p>Latched on the tracking row, because the device pings every few seconds and the customer
     * should be told once, not for the whole final approach.
     */
    private void maybeAnnounceArrival(Booking booking, BookingTracking tracking) {
        if (tracking.getArrivalNotifiedAt() != null) return;
        if (booking.getLocationLat() == null || booking.getLocationLng() == null) return;

        Double distanceKm = distanceKm(tracking.getWorkerLat(), tracking.getWorkerLng(),
                booking.getLocationLat(), booking.getLocationLng());
        if (distanceKm == null) return;

        // Only routes worth asking a routing service about: beyond this the answer cannot be
        // "nearly there", and every ping would spend a Distance Matrix call to learn that.
        if (distanceKm > ARRIVAL_LOOKUP_RADIUS_KM) return;

        EtaService.Eta eta = etaService.estimate(
                tracking.getWorkerLat(), tracking.getWorkerLng(),
                booking.getLocationLat(), booking.getLocationLng(),
                distanceKm, tracking.getSpeedKph());

        boolean nearlyThere = distanceKm <= ARRIVAL_RADIUS_KM || eta.minutes() <= ARRIVAL_ETA_MINUTES;
        if (!nearlyThere) return;

        tracking.setArrivalNotifiedAt(LocalDateTime.now());
        trackingRepository.save(tracking);
        events.publishArriving(booking, eta.minutes(), distanceKm, eta.trafficAware());
    }

    @GetMapping
    @Operation(summary = "Current worker position and distance to the destination")
    public ResponseEntity<Map<String, Object>> current(
            @RequestHeader("X-User-Id") Long actorId,
            @RequestHeader(value = "X-User-Role", required = false) String actorRole,
            @PathVariable Long bookingId) {

        Booking booking = booking(bookingId);

        boolean involved = actorId.equals(booking.getCustomerId())
                || (booking.getWorkerId() != null && booking.getWorkerId().equals(actorId));
        if (!involved && !isAdmin(actorRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are not part of this booking");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("bookingId", bookingId);
        body.put("bookingStatus", booking.getStatus());
        // Returned so the client knows which side of this booking the caller is on, and can offer
        // the worker the "share my location" control without a second request to find out. Both
        // ids are already visible to everyone this endpoint admits, so it discloses nothing new.
        body.put("customerId", booking.getCustomerId());
        body.put("workerId", booking.getWorkerId());
        body.put("viewerIsWorker", booking.getWorkerId() != null
                && booking.getWorkerId().equals(actorId));
        body.put("destinationLat", booking.getLocationLat());
        body.put("destinationLng", booking.getLocationLng());
        body.put("addressLine", booking.getAddressLine());

        BookingTracking tracking = trackingRepository.findById(bookingId).orElse(null);
        if (tracking == null) {
            // Not an error: a booking that has been accepted but not started has no position yet,
            // and that is the normal first state of every job.
            body.put("tracking", null);
            body.put("message", "The worker has not started sharing their location yet");
            return ResponseEntity.ok(body);
        }

        boolean stale = tracking.getUpdatedAt() != null
                && tracking.getUpdatedAt().isBefore(LocalDateTime.now().minus(FIX_FRESHNESS));

        Map<String, Object> position = new LinkedHashMap<>();
        position.put("lat", tracking.getWorkerLat());
        position.put("lng", tracking.getWorkerLng());
        position.put("headingDeg", tracking.getHeadingDeg());
        position.put("speedKph", tracking.getSpeedKph());
        position.put("note", tracking.getNote());
        position.put("updatedAt", tracking.getUpdatedAt());
        position.put("stale", stale);

        Double distanceKm = distanceKm(tracking.getWorkerLat(), tracking.getWorkerLng(),
                booking.getLocationLat(), booking.getLocationLng());
        position.put("distanceKm", distanceKm);
        position.put("etaMinutes", etaMinutes(distanceKm, tracking.getSpeedKph()));

        body.put("tracking", position);
        return ResponseEntity.ok(body);
    }

    private Booking booking(Long bookingId) {
        return bookingRepository.findById(bookingId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
    }

    private boolean isAdmin(String role) {
        return role != null && ADMIN_ROLES.contains(role);
    }

    /**
     * Great-circle distance in kilometres.
     *
     * Straight-line, not road distance — this service has no routing engine and pretending
     * otherwise would put a confident wrong number in front of someone deciding whether to wait.
     * The UI labels it as direct distance for the same reason.
     */
    static Double distanceKm(Double lat1, Double lng1, Double lat2, Double lng2) {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) return null;

        final double earthRadiusKm = 6371.0088;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(earthRadiusKm * c * 100.0) / 100.0;
    }

    /**
     * Rough arrival estimate.
     *
     * Uses the reported speed when the worker is actually moving, and otherwise assumes 20 km/h —
     * a stopped vehicle would divide by zero and an infinite ETA is less useful than a stated
     * assumption. City traffic makes anything more precise false confidence.
     */
    static Integer etaMinutes(Double distanceKm, Double speedKph) {
        if (distanceKm == null) return null;
        double effectiveSpeed = (speedKph != null && speedKph > 5) ? speedKph : 20.0;
        return (int) Math.ceil((distanceKm / effectiveSpeed) * 60);
    }
}
