package com.civileng.marketplace.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {

    private boolean success;
    private String message;
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long expiresIn;
    private UserDto user;
    private LocalDateTime timestamp;

    /**
     * Set instead of tokens when the account needs a second factor: the client posts the code with
     * {@link #mfaToken} to /auth/mfa/verify, or first enrols at /auth/mfa/setup when
     * {@link #mfaSetupRequired}.
     */
    private Boolean mfaRequired;
    private Boolean mfaSetupRequired;
    private String mfaToken;

    /** Shown once, when MFA is switched on: each code signs in once if the phone is lost. */
    private List<String> recoveryCodes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UserDto {
        private Long id;
        private String name;
        private String email;
        private String phone;
        private String profilePicture;
        private String role;
        private boolean emailVerified;
        private boolean phoneVerified;
        private String status;
    }
}
