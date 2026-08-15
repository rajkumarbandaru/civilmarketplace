package com.civileng.marketplace.notification.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Normalises the phone numbers held by auth-service into the E.164 form Twilio requires.
 *
 * <p>Accounts are registered with bare national numbers (the seeded users carry ten-digit
 * Indian numbers), so a default calling code has to be applied before dispatch.
 */
@Component
public class PhoneNumbers {

    @Value("${app.sms.default-country-code:+91}")
    private String defaultCountryCode;

    /**
     * @return the number in E.164 form, or {@code null} if it cannot plausibly be one.
     */
    public String toE164(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("[^0-9+]", "");
        if (digits.startsWith("+")) {
            // Strip any stray '+' beyond the leading one before length-checking.
            digits = "+" + digits.substring(1).replace("+", "");
            return digits.length() >= 8 ? digits : null;
        }
        if (digits.startsWith("00")) {
            digits = digits.substring(2);
            return digits.length() >= 7 ? "+" + digits : null;
        }
        if (digits.length() < 6) {
            return null;
        }
        return defaultCountryCode + digits;
    }

    /** Keeps full numbers out of the logs. */
    public static String mask(String phone) {
        if (phone == null || phone.length() < 4) {
            return "****";
        }
        return "*".repeat(phone.length() - 4) + phone.substring(phone.length() - 4);
    }
}
