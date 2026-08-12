package com.civileng.marketplace.admin.exception;

/** Mapped to 403 by {@link GlobalExceptionHandler}. */
public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(String message) {
        super(message);
    }
}
