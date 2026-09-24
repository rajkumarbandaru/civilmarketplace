package com.civileng.marketplace.auth.exception;

/**
 * A refresh token that cannot be exchanged — expired, revoked, replayed or for another workspace.
 * Always a 401 with the same message, so a caller cannot probe which of those it was.
 */
public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException() {
        super("Invalid or expired refresh token");
    }
}
