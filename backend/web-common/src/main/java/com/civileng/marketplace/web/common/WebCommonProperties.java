package com.civileng.marketplace.web.common;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "platform.web")
public class WebCommonProperties {

    /**
     * Whether this service serves the shared error shape. Off for auth-, booking- and
     * admin-service, whose responses are a different shape their callers already depend on.
     */
    private boolean errorHandler = true;

    private final AdminGuard adminGuard = new AdminGuard();

    @Getter
    @Setter
    public static class AdminGuard {

        /**
         * Off by default. A service that mounts no staff surface should not have to say so, and a
         * guard that switched itself on by guessing at path shapes would eventually guess wrong on
         * a member endpoint that happens to sit under an admin-looking prefix.
         */
        private boolean enabled = false;

        /**
         * Which request paths require a staff role. Configurable because the prefix is not uniform
         * — most services mount staff endpoints under {@code /api/v1/admin/**}, but booking-service
         * puts its own under {@code /api/v1/bookings/admin/**}.
         */
        private List<String> pathPatterns = List.of("/api/v1/admin/**");

        /**
         * Paths under {@link #pathPatterns} that the gate must not cover.
         *
         * <p>For the bulk-read endpoints background jobs call. search-service's reindex sweep and
         * notification-service's announcement job run on a scheduler, so there is no inbound
         * request and {@code IdentityFeignInterceptor} has no caller to propagate — by design,
         * since a job must not borrow a passer-by's identity. They therefore arrive with no role
         * and the gate refuses them, which silently empties the search index.
         *
         * <p>Exempting these is safe because {@code InternalOnlyPathFilter} already makes the whole
         * prefix unreachable from the gateway: they are service-to-service reads with no external
         * door, and the externally reachable equivalents on admin-service stay gated. Keep the list
         * to specific read paths — never a wildcard that would take the mutating endpoints with it.
         */
        private List<String> excludePathPatterns = List.of();
    }
}
