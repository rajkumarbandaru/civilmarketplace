package com.civileng.marketplace.gateway.config;

import com.civileng.marketplace.gateway.filter.JwtAuthGatewayFilterFactory;
import com.civileng.marketplace.gateway.handler.FallbackController;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class GatewayConfig {

    /**
     * Browser origins allowed to call the API, as patterns.
     *
     * <p>Patterns rather than a fixed list because every tenant is served on its own hostname:
     * {@code acme.localhost:3000} and {@code hostelfee.localhost:3000} are distinct origins to a
     * browser, and enumerating them would mean a gateway redeploy every time a tenant is onboarded.
     * The failure this causes is badly disguised — Spring rejects the unlisted origin with a bare
     * 403 and an empty body, which the console can only render as "you do not have permission",
     * on the login call, before anyone has been authenticated at all.
     *
     * <p>Must be {@code setAllowedOriginPatterns}, not {@code setAllowedOrigins}: a wildcard in the
     * latter is rejected outright when {@code allowCredentials} is true. Exact origins are still
     * valid patterns, so the previously listed hosts keep working unchanged.
     *
     * <p>The frontend's host port is configurable (HOST_PORT_FRONTEND), so this stays overridable.
     * Both localhost and 127.0.0.1 forms are needed: they are distinct origins to a browser, and
     * 127.0.0.1 takes no subdomains, which is why only the localhost forms carry a wildcard.
     */
    @Value("${cors.allowed-origin-patterns:http://localhost:3000,http://127.0.0.1:3000,"
            + "http://*.localhost:3000,"
            + "http://localhost:3007,http://127.0.0.1:3007,http://*.localhost:3007,"
            + "http://localhost:5173,http://127.0.0.1:5173,http://*.localhost:5173,"
            + "https://app.civilengineer.com,https://*.civilengineer.com}")
    private List<String> allowedOriginPatterns;

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder,
                                           JwtAuthGatewayFilterFactory jwtAuthFilter) {
        return builder.routes()
                .route("auth-service", r -> r
                        .path("/api/v1/auth/**", "/oauth2/**")
                        .filters(f -> f.stripPrefix(0))
                        .uri("lb://auth-service"))
                // Country/state/city reference data for the address pickers. Public and
                // unfiltered: it is a static list with no user data in it, and the register form
                // needs it before anyone has a token.
                .route("geo-public", r -> r
                        .path("/api/v1/geo/**")
                        .filters(f -> f.stripPrefix(0))
                        .uri("lb://user-service"))
                .route("user-service", r -> r
                        .path("/api/v1/users/**")
                        .filters(f -> f.stripPrefix(0)
                                .filter(jwtAuthFilter.apply(new JwtAuthGatewayFilterFactory.Config())))
                        .uri("lb://user-service"))
                // must precede booking-service — /api/v1/bookings/** would otherwise swallow it
                .route("booking-reviews", r -> r
                        .path("/api/v1/bookings/*/reviews")
                        .filters(f -> f.stripPrefix(0)
                                .filter(jwtAuthFilter.apply(new JwtAuthGatewayFilterFactory.Config())))
                        .uri("lb://review-service"))
                // must precede booking-service for the same reason
                .route("booking-messages", r -> r
                        .path("/api/v1/bookings/*/messages")
                        .filters(f -> f.stripPrefix(0)
                                .filter(jwtAuthFilter.apply(new JwtAuthGatewayFilterFactory.Config())))
                        .uri("lb://messaging-service"))
                .route("threads", r -> r
                        .path("/api/v1/threads/**")
                        .filters(f -> f.stripPrefix(0)
                                .filter(jwtAuthFilter.apply(new JwtAuthGatewayFilterFactory.Config())))
                        .uri("lb://messaging-service"))
                // The public service catalogue. No JwtAuth filter and no rate limiter: it is what a
                // signed-out visitor lands on, and behind the auth filter the home and services
                // pages render nothing until someone logs in. Read-only, and exposes nothing beyond
                // what the page itself prints.
                .route("catalogue-public", r -> r
                        .path("/api/v1/catalogue/**")
                        .filters(f -> f.stripPrefix(0))
                        .uri("lb://booking-service"))
                // The site's editable copy and its images, on the same reasoning as the catalogue:
                // the landing page and footer are rendered from it before anyone signs in. The
                // matching writes live under /api/v1/admin/content/**, which stays behind the
                // filter with the rest of the admin surface.
                .route("content-public", r -> r
                        .path("/api/v1/content/**")
                        .filters(f -> f.stripPrefix(0))
                        .uri("lb://admin-service"))
                .route("booking-service", r -> r
                        .path("/api/v1/bookings/**")
                        .filters(f -> f.stripPrefix(0)
                                .filter(jwtAuthFilter.apply(new JwtAuthGatewayFilterFactory.Config())))
                        .uri("lb://booking-service"))
                .route("payment-service", r -> r
                        .path("/api/v1/payments/**", "/api/v1/wallets/**", "/api/v1/razorpay/**",
                                "/api/v1/escrow/**", "/api/v1/admin/escrow/**")
                        .filters(f -> f.stripPrefix(0)
                                .filter(jwtAuthFilter.apply(new JwtAuthGatewayFilterFactory.Config())))
                        .uri("lb://payment-service"))
                .route("payment-webhooks", r -> r
                        .path("/webhooks/**")
                        .filters(f -> f.stripPrefix(0))
                        .uri("lb://payment-service"))
                .route("audit-service", r -> r
                        .path("/api/v1/admin/audit/**", "/api/v1/privacy/**")
                        .filters(f -> f.stripPrefix(0)
                                .filter(jwtAuthFilter.apply(new JwtAuthGatewayFilterFactory.Config())))
                        .uri("lb://audit-service"))
                .route("search-service", r -> r
                        .path("/api/v1/search/**", "/api/v1/admin/search/**")
                        .filters(f -> f.stripPrefix(0)
                                .filter(jwtAuthFilter.apply(new JwtAuthGatewayFilterFactory.Config())))
                        .uri("lb://search-service"))
                .route("project-service", r -> r
                        .path("/api/v1/projects/**", "/api/v1/admin/projects/**")
                        .filters(f -> f.stripPrefix(0)
                                .filter(jwtAuthFilter.apply(new JwtAuthGatewayFilterFactory.Config())))
                        .uri("lb://project-service"))
                .route("review-service", r -> r
                        .path("/api/v1/reviews/**", "/api/v1/profiles/**", "/api/v1/admin/reviews/**")
                        .filters(f -> f.stripPrefix(0)
                                .filter(jwtAuthFilter.apply(new JwtAuthGatewayFilterFactory.Config())))
                        .uri("lb://review-service"))
                // Provider delivery callbacks. Unauthenticated because Brevo carries no JWT; the
                // endpoint checks a shared secret in the query string instead. Must precede the
                // notification-service route below, whose /api/v1/notifications/** would otherwise
                // apply the JWT filter and reject every callback with a 401.
                .route("notification-webhooks", r -> r
                        .path("/api/v1/notifications/webhooks/**")
                        .filters(f -> f.stripPrefix(0))
                        .uri("lb://notification-service"))
                // must precede admin-service — /api/v1/admin/** would otherwise swallow
                // /api/v1/admin/announcements/**, which belongs to notification-service
                .route("notification-service", r -> r
                        .path("/api/v1/notifications/**", "/api/v1/admin/announcements/**",
                                "/api/v1/admin/notifications/**")
                        .filters(f -> f.stripPrefix(0)
                                .filter(jwtAuthFilter.apply(new JwtAuthGatewayFilterFactory.Config())))
                        .uri("lb://notification-service"))
                .route("support-service", r -> r
                        .path("/api/v1/support/**", "/api/v1/admin/support/**")
                        .filters(f -> f.stripPrefix(0)
                                .filter(jwtAuthFilter.apply(new JwtAuthGatewayFilterFactory.Config())))
                        .uri("lb://support-service"))
                // must follow review-service, notification-service and support-service —
                // /api/v1/admin/** would otherwise swallow /api/v1/admin/reviews/**,
                // /api/v1/admin/announcements/**, /api/v1/admin/notifications/** and
                // /api/v1/admin/support/**
                .route("admin-service", r -> r
                        .path("/api/v1/admin/**", "/api/v1/ui-config/**")
                        .filters(f -> f.stripPrefix(0)
                                .filter(jwtAuthFilter.apply(new JwtAuthGatewayFilterFactory.Config())))
                        .uri("lb://admin-service"))
                // Host → tenant lookup. Public and unfiltered: the login screen needs the
                // workspace's name and branding before any token exists, and it discloses
                // nothing beyond the URL the visitor already typed.
                .route("tenant-resolution", r -> r
                        .path("/api/v1/tenant-resolution/**")
                        .filters(f -> f.stripPrefix(0))
                        .uri("lb://tenant-service"))
                // Tenant administration. Behind the JWT filter; tenant-service additionally
                // requires the caller be a SUPER_ADMIN of the operator tenant.
                .route("tenant-service", r -> r
                        .path("/api/v1/tenants/**")
                        .filters(f -> f.stripPrefix(0)
                                .filter(jwtAuthFilter.apply(new JwtAuthGatewayFilterFactory.Config())))
                        .uri("lb://tenant-service"))
                .build();
    }

    /**
     * Load-balanced so {@link com.civileng.marketplace.gateway.tenant.TenantDirectory} can reach
     * tenant-service through Eureka by name rather than a pinned host.
     */
    @Bean
    @org.springframework.cloud.client.loadbalancer.LoadBalanced
    public org.springframework.web.reactive.function.client.WebClient.Builder
            loadBalancedWebClientBuilder() {
        return org.springframework.web.reactive.function.client.WebClient.builder();
    }

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(allowedOriginPatterns);
        config.setAllowedMethods(List.of(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.PUT.name(),
                HttpMethod.PATCH.name(),
                HttpMethod.DELETE.name(),
                HttpMethod.OPTIONS.name()
        ));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        config.setExposedHeaders(List.of("Authorization", "Content-Disposition"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }

    @Bean
    public RedisRateLimiter redisRateLimiter() {
        return new RedisRateLimiter(100, 200, 1);
    }
}
