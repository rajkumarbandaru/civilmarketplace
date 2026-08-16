package com.civileng.marketplace.admin.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request shape for a catalogue item, mirroring booking-service's {@code OfferingRequest}.
 *
 * Validated here as well as there so an obviously bad payload is refused at the admin edge rather
 * than turning into a Feign error that reads like an outage.
 */
public class ServiceOfferingDTO {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OfferingRequest {
        @NotBlank(message = "Title is required")
        private String title;

        @NotBlank(message = "Category is required")
        private String category;

        private String slug;
        private String icon;
        private String price;

        /** Photo, video or animation shown on the card in place of the icon. */
        private String mediaUrl;
        /** IMAGE | VIDEO | ANIMATION. Inferred from the URL when left blank. */
        private String mediaType;

        @DecimalMin(value = "0.0", message = "Rating cannot be negative")
        @DecimalMax(value = "5.0", message = "Rating cannot exceed 5")
        private Double rating;

        @PositiveOrZero(message = "Review count cannot be negative")
        private Integer reviews;

        private String aliases;
        private Integer sortOrder;
        private Boolean active;
    }
}
