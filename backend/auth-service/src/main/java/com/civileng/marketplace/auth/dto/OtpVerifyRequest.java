package com.civileng.marketplace.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Verify an OTP. The identifier must be the same one the code was requested with.
 */
@Data
public class OtpVerifyRequest {

    @Email(message = "Invalid email format")
    private String email;

    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$",
            message = "Invalid phone number format")
    private String phone;

    @NotBlank(message = "OTP is required")
    private String otp;

    @AssertTrue(message = "Provide either an email address or a mobile number")
    public boolean isIdentifierPresent() {
        boolean hasEmail = email != null && !email.isBlank();
        boolean hasPhone = phone != null && !phone.isBlank();
        return hasEmail ^ hasPhone;
    }
}
