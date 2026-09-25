package com.civileng.marketplace.tenant.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Wire shapes for the tenant integration endpoints. None of them can carry a secret outward. */
public final class IntegrationDtos {

    private IntegrationDtos() {
    }

    /**
     * A save. {@code secrets} is write-only: a key left out (or blank) keeps the stored value, so the
     * console can change a from-address without making the operator re-type the API key it never
     * shows them.
     */
    public record SaveIntegrationRequest(
            String mode,
            String provider,
            Boolean enabled,
            Map<String, String> settings,
            Map<String, String> secrets) {
    }

    /**
     * What the console sees: which secrets are set (as masked hints), never their values. The
     * webhook URL path is included for payment so the operator can paste it into Razorpay.
     */
    public record IntegrationView(
            String capability,
            boolean configured,
            String mode,
            String provider,
            boolean enabled,
            Map<String, String> settings,
            Map<String, String> secretHints,
            String webhookPath,
            String updatedBy,
            LocalDateTime updatedAt) {
    }

    /** One capability as the console renders its form. */
    public record CapabilityView(
            String capability,
            Map<String, ProviderView> providers) {
    }

    public record ProviderView(List<String> settings, List<String> secrets, List<String> optionalSettings) {
    }
}
