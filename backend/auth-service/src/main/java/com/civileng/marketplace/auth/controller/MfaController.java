package com.civileng.marketplace.auth.controller;

import com.civileng.marketplace.auth.dto.AuthResponse;
import com.civileng.marketplace.auth.dto.MfaSetupResponse;
import com.civileng.marketplace.auth.service.MfaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** The second sign-in step. Authorised by the MFA token sign-in returned, not by an access token. */
@RestController
@RequestMapping("/api/v1/auth/mfa")
@RequiredArgsConstructor
@Tag(name = "Two-step sign-in", description = "Authenticator app (TOTP) second factor")
public class MfaController {

    private final MfaService mfaService;

    public record MfaTokenRequest(@NotBlank String mfaToken) { }

    public record MfaCodeRequest(@NotBlank String mfaToken, @NotBlank String code) { }

    @PostMapping("/setup")
    @Operation(summary = "Start enrolling an authenticator app (returns the secret and QR URI)")
    public ResponseEntity<MfaSetupResponse> setup(@Valid @RequestBody MfaTokenRequest request) {
        return ResponseEntity.ok(mfaService.setup(request.mfaToken()));
    }

    @PostMapping("/enable")
    @Operation(summary = "Confirm enrolment with a first code; completes sign-in and returns recovery codes")
    public ResponseEntity<AuthResponse> enable(@Valid @RequestBody MfaCodeRequest request) {
        return ResponseEntity.ok(mfaService.enable(request.mfaToken(), request.code()));
    }

    @PostMapping("/verify")
    @Operation(summary = "Finish sign-in with an authenticator or recovery code")
    public ResponseEntity<AuthResponse> verify(@Valid @RequestBody MfaCodeRequest request) {
        return ResponseEntity.ok(mfaService.verify(request.mfaToken(), request.code()));
    }
}
