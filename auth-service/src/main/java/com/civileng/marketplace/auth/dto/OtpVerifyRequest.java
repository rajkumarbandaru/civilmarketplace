package com.civileng.marketplace.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OtpVerifyRequest {

    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "OTP is required")
    private String otp;

    private String phone;
}
