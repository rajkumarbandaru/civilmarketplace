package com.civileng.marketplace.user.geo;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The country / state / city reference list behind the address pickers.
 *
 * <p>Served from a bundled resource rather than fetched from a third-party geography API: the
 * address step is on the booking path, and a booking that cannot be completed because somebody
 * else's free API is rate-limiting is a lost job. The list changes about as often as a country
 * does, so a file that ships with the service is the right shape for it.
 *
 * <p>It is also not a database table. Nothing edits this data, nothing joins to it, and a
 * migration per city added is a worse deal than editing one JSON file.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GeoService {

    private static final String RESOURCE = "geo/locations.json";

    private final ObjectMapper objectMapper;

    private List<Country> countries = List.of();

    /** A country and the states within it. */
    public record Country(String code, String name, String dialCode, List<State> states) {
    }

    public record State(String code, String name, List<String> cities) {
    }

    /** Country without its states — what the first dropdown needs, and nothing more. */
    public record CountrySummary(String code, String name, String dialCode) {
    }

    /** State without its cities, for the same reason. */
    public record StateSummary(String code, String name) {
    }

    @PostConstruct
    void load() {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            Map<String, List<Country>> parsed = objectMapper.readValue(in,
                    objectMapper.getTypeFactory().constructMapType(
                            Map.class,
                            objectMapper.getTypeFactory().constructType(String.class),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, Country.class)));
            countries = parsed.getOrDefault("countries", List.of());
            log.info("Loaded {} countries for the address picker", countries.size());
        } catch (Exception e) {
            // An empty list degrades the picker to free text rather than taking the service down:
            // this is reference data, not something the service cannot run without.
            log.error("Could not load {}: {}", RESOURCE, e.getMessage());
        }
    }

    public List<CountrySummary> countries() {
        return countries.stream()
                .map(c -> new CountrySummary(c.code(), c.name(), c.dialCode()))
                .sorted(Comparator.comparing(CountrySummary::name))
                .collect(Collectors.toList());
    }

    /** States of one country, by ISO code. Unknown codes give an empty list, not a 404 — a client
     *  that has not chosen a country yet should render an empty dropdown, not an error. */
    public List<StateSummary> states(String countryCode) {
        return country(countryCode)
                .map(c -> c.states().stream()
                        .map(s -> new StateSummary(s.code(), s.name()))
                        .sorted(Comparator.comparing(StateSummary::name))
                        .toList())
                .orElse(List.of());
    }

    /**
     * Cities of one state. Matched on code or name, because the client holds whichever of the two
     * the previous dropdown gave it and should not have to care which.
     */
    public List<String> cities(String countryCode, String state) {
        return country(countryCode)
                .flatMap(c -> c.states().stream()
                        .filter(s -> s.code().equalsIgnoreCase(state) || s.name().equalsIgnoreCase(state))
                        .findFirst())
                .map(State::cities)
                .orElse(List.of());
    }

    private java.util.Optional<Country> country(String code) {
        if (code == null) return java.util.Optional.empty();
        return countries.stream()
                .filter(c -> c.code().equalsIgnoreCase(code) || c.name().equalsIgnoreCase(code))
                .findFirst();
    }
}
