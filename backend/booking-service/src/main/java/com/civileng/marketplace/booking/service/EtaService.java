package com.civileng.marketplace.booking.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * How long the worker still needs to reach the customer.
 *
 * <p>Asks Google's Distance Matrix for a duration <em>in current traffic</em>, which is the only
 * way to say "5 minutes away" and be right — straight-line distance over an assumed speed is wrong
 * by whole minutes in city traffic, and it is wrong in the direction that makes a customer walk
 * outside too early.
 *
 * <p>When no server key is configured, or the call fails, it falls back to the same rough estimate
 * the tracking endpoint has always used, and says so: {@link Eta#trafficAware()} is what lets the
 * email word itself honestly instead of dressing a guess up as a routed arrival time.
 */
@Service
@Slf4j
public class EtaService {

    private static final String ENDPOINT = "https://maps.googleapis.com/maps/api/distancematrix/json";

    /** Assumed city speed when nothing better is known, matching BookingTrackingController. */
    private static final double FALLBACK_SPEED_KPH = 20.0;

    private final RestClient restClient = RestClient.create();

    /**
     * A key permitted to call the Distance Matrix API from a server.
     *
     * Deliberately separate from the browser key the frontend is built with: that one is normally
     * restricted by HTTP referrer, so server-side calls with it are rejected. Empty disables the
     * lookup entirely and everything falls back — the feature degrades, it does not break.
     */
    @Value("${google.maps.server-api-key:${GOOGLE_MAPS_SERVER_API_KEY:}}")
    private String apiKey;

    /** Minutes to arrival, and whether traffic was actually taken into account. */
    public record Eta(int minutes, boolean trafficAware) {
    }

    public Eta estimate(double fromLat, double fromLng, double toLat, double toLng,
                        Double distanceKm, Double speedKph) {
        Integer routed = routedMinutes(fromLat, fromLng, toLat, toLng);
        if (routed != null) {
            return new Eta(routed, true);
        }
        return new Eta(fallbackMinutes(distanceKm, speedKph), false);
    }

    /**
     * Duration in traffic, in minutes, or null when unavailable.
     *
     * <p>{@code departure_time=now} is what makes the API return {@code duration_in_traffic} at
     * all; without it the answer is a free-flow duration, which is the number that makes a
     * rush-hour ETA read like a Sunday morning one.
     */
    private Integer routedMinutes(double fromLat, double fromLng, double toLat, double toLng) {
        if (apiKey == null || apiKey.isBlank()) return null;

        try {
            String url = ENDPOINT
                    + "?origins=" + fromLat + "," + fromLng
                    + "&destinations=" + toLat + "," + toLng
                    + "&mode=driving&departure_time=now&traffic_model=best_guess"
                    + "&key=" + apiKey;

            Map<?, ?> response = restClient.get().uri(url).retrieve().body(Map.class);
            if (response == null || !"OK".equals(response.get("status"))) {
                log.warn("Distance Matrix returned status {}", response == null ? "null" : response.get("status"));
                return null;
            }

            List<?> rows = (List<?>) response.get("rows");
            if (rows == null || rows.isEmpty()) return null;
            List<?> elements = (List<?>) ((Map<?, ?>) rows.get(0)).get("elements");
            if (elements == null || elements.isEmpty()) return null;

            Map<?, ?> element = (Map<?, ?>) elements.get(0);
            if (!"OK".equals(element.get("status"))) return null;

            // duration_in_traffic is absent on routes Google has no traffic model for; the plain
            // duration is still a routed road time, which beats a straight line.
            Object durationNode = element.get("duration_in_traffic") != null
                    ? element.get("duration_in_traffic")
                    : element.get("duration");
            Map<?, ?> duration = (Map<?, ?>) durationNode;
            if (duration == null) return null;

            Number seconds = (Number) duration.get("value");
            if (seconds == null) return null;
            return Math.max(1, (int) Math.round(seconds.doubleValue() / 60.0));
        } catch (Exception e) {
            log.warn("Distance Matrix lookup failed, falling back to estimate: {}", e.getMessage());
            return null;
        }
    }

    /** Straight-line distance over the reported speed — the pre-existing rough estimate. */
    private int fallbackMinutes(Double distanceKm, Double speedKph) {
        if (distanceKm == null) return 0;
        double speed = speedKph != null && speedKph > 1 ? speedKph : FALLBACK_SPEED_KPH;
        return Math.max(1, (int) Math.round((distanceKm / speed) * 60));
    }
}
