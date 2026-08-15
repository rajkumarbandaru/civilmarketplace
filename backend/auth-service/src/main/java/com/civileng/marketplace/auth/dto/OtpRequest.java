package com.civileng.marketplace.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Request an OTP. Exactly one of {@code email} or {@code phone} identifies the account.
 *
 * <p>{@code channel} picks the delivery route and defaults to the one implied by the
 * identifier (email address → EMAIL, phone number → SMS). Supplying a phone number with
 * {@code channel: "WHATSAPP"} is what routes the code over WhatsApp instead of SMS.
 */
@Data
public class OtpRequest {

    @Email(message = "Invalid email format")
    private String email;

    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$",
            message = "Invalid phone number format")
    private String phone;

    /** EMAIL, SMS or WHATSAPP; defaults to whichever the identifier implies. */
    private String channel;

    @AssertTrue(message = "Provide either an email address or a mobile number")
    public boolean isIdentifierPresent() {
        boolean hasEmail = email != null && !email.isBlank();
        boolean hasPhone = phone != null && !phone.isBlank();
        return hasEmail ^ hasPhone;
    }
}
