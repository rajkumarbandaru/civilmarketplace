package com.civileng.marketplace.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Canonical form for the two identifiers that must be unique across the platform.
 *
 * <p>Uniqueness checks only work if the same person always produces the same string. Before
 * this existed, {@code 9493564235}, {@code +91 94935 64235} and {@code 09493564235} were three
 * distinct values that each passed the "phone already registered?" check, and
 * {@code Ravi@x.com} could slip past a lookup for {@code ravi@x.com}. Every write path
 * normalises through here, so format variants collide instead of creating duplicate accounts.
 */
@Component
public class AccountIdentifiers {

    @Value("${app.phone.default-country-code:+91}")
    private String defaultCountryCode;

    /** Lowercased and trimmed; email addresses are not case-sensitive in practice. */
    public String normaliseEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    /**
     * Converts to E.164, which is what the frontend submits and what the SMS/WhatsApp
     * providers require.
     *
     * @return the normalised number, or the trimmed input when it cannot plausibly be a
     *         phone number — rejecting it here would turn a validation error into a
     *         confusing "already registered" one.
     */
    public String normalisePhone(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("[^0-9+]", "");
        if (digits.isBlank()) {
            return phone.trim();
        }
        if (digits.startsWith("+")) {
            // Collapse any stray '+' beyond the leading one.
            return "+" + digits.substring(1).replace("+", "");
        }
        if (digits.startsWith("00")) {
            return "+" + digits.substring(2);
        }
        // A leading trunk '0' is national-dialling notation and is not part of E.164.
        if (digits.startsWith("0")) {
            digits = digits.replaceFirst("^0+", "");
        }
        return defaultCountryCode + digits;
    }
}
