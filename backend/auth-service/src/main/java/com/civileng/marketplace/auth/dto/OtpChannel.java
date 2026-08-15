package com.civileng.marketplace.auth.dto;

/**
 * Delivery channel for a one-time code.
 *
 * <p>The channel is carried on the {@code otp.sent} Kafka event and notification-service
 * dispatches on it, so these names must stay in step with the ones its consumer switches on.
 */
public enum OtpChannel {

    EMAIL,
    SMS,
    WHATSAPP;

    /**
     * Codes are stored under a per-identifier key so a code sent to one identifier cannot be
     * replayed against the other. SMS and WhatsApp share a key: both are delivered to the same
     * phone number and prove exactly the same thing, so a code requested over one and verified
     * over the other is not an escalation.
     */
    public String keyPrefix() {
        return this == EMAIL ? "email:" : "phone:";
    }

    public boolean usesPhone() {
        return this != EMAIL;
    }

    /** Parses a caller-supplied channel, falling back to the given default when absent. */
    public static OtpChannel parse(String value, OtpChannel fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return OtpChannel.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid channel: " + value + ". Expected EMAIL, SMS or WHATSAPP");
        }
    }
}
