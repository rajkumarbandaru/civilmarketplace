package com.civileng.marketplace.web.common;

/**
 * The caller is authenticated but not allowed to do this.
 *
 * <p>Mapped to 403 by {@link PlatformExceptionHandler}. Deliberately not Spring Security's
 * {@code org.springframework.security.access.AccessDeniedException}: no service on the platform
 * runs a Spring Security filter chain — authentication happens at the gateway and arrives as
 * {@code X-User-*} headers — so importing the Security exception would drag in a framework whose
 * semantics none of this uses.
 */
public class AccessDeniedException extends RuntimeException {

    public AccessDeniedException(String message) {
        super(message);
    }
}
