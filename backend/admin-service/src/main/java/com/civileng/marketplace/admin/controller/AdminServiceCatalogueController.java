package com.civileng.marketplace.admin.controller;

import com.civileng.marketplace.admin.client.BookingServiceClient;
import com.civileng.marketplace.admin.dto.ServiceOfferingDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Admin console access to the public catalogue items, proxied to booking-service, which owns them.
 *
 * Sits under {@code /api/v1/admin} alongside category management so the console talks to one base
 * path; booking-service does the real work.
 */
@RestController
@RequestMapping("/api/v1/admin/services")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Service Catalogue", description = "CRUD for the services shown on the public site")
public class AdminServiceCatalogueController {

    private final BookingServiceClient bookingServiceClient;
    private final ObjectMapper objectMapper;

    @GetMapping
    @Operation(summary = "Get every catalogue item, active or not")
    public ResponseEntity<Map<String, Object>> getServices() {
        return ResponseEntity.ok(call(bookingServiceClient::getAllServices));
    }

    @PostMapping
    @Operation(summary = "Create a catalogue item")
    public ResponseEntity<Map<String, Object>> createService(
            @Valid @RequestBody ServiceOfferingDTO.OfferingRequest request) {
        return ResponseEntity.ok(call(() -> bookingServiceClient.createService(request)));
    }

    @PutMapping("/{serviceId}")
    @Operation(summary = "Update a catalogue item")
    public ResponseEntity<Map<String, Object>> updateService(
            @PathVariable Long serviceId,
            @Valid @RequestBody ServiceOfferingDTO.OfferingRequest request) {
        return ResponseEntity.ok(call(() -> bookingServiceClient.updateService(serviceId, request)));
    }

    @DeleteMapping("/{serviceId}")
    @Operation(summary = "Delete a catalogue item")
    public ResponseEntity<Map<String, Object>> deleteService(@PathVariable Long serviceId) {
        return ResponseEntity.ok(call(() -> bookingServiceClient.deleteService(serviceId)));
    }

    @PutMapping("/{serviceId}/status")
    @Operation(summary = "Enable or disable a catalogue item")
    public ResponseEntity<Map<String, Object>> toggleServiceStatus(@PathVariable Long serviceId) {
        return ResponseEntity.ok(call(() -> bookingServiceClient.toggleServiceStatus(serviceId)));
    }

    /**
     * Runs a downstream call, turning its 4xx into the same message booking-service wrote.
     *
     * Without this a duplicate slug or an out-of-range rating comes back as an opaque 500, and the
     * admin is told the platform is broken when in fact the form needs one field changed.
     */
    private Map<String, Object> call(Supplier<ResponseEntity<Map<String, Object>>> action) {
        try {
            Map<String, Object> body = action.get().getBody();
            return body != null ? body : Map.of("success", true);
        } catch (feign.FeignException e) {
            if (e.status() >= 400 && e.status() < 500) {
                throw new IllegalArgumentException(feignMessage(e));
            }
            log.error("Catalogue call to booking-service failed: {}", e.getMessage());
            throw e;
        }
    }

    private String feignMessage(feign.FeignException e) {
        String content = e.contentUTF8();
        if (content != null && !content.isBlank()) {
            try {
                Object message = objectMapper.readValue(content, Map.class).get("message");
                if (message != null) return message.toString();
            } catch (Exception ignored) {
                // A non-JSON body is no worse than no body — fall through to the generic message.
            }
        }
        return "The service could not be saved (booking-service returned " + e.status() + ")";
    }
}
