package com.civileng.marketplace.auth.dto;

/** A secret to add to an authenticator app: {@code otpauthUri} for the QR code, {@code secret} to type in. */
public record MfaSetupResponse(String secret, String otpauthUri) { }
