package com.civileng.marketplace.tenant.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/**
 * Signature checking on forwarded identity ({@link InternalContextFilter}), in every servlet
 * service that has tenant-common — including tenant-service, which runs with the tenant runtime
 * off because it is the registry itself, yet authorises operators from the very headers this
 * protects. That is why this is not part of {@link TenantAutoConfiguration}.
 */
@AutoConfiguration(before = TenantAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class InternalContextAutoConfiguration {

    /**
     * The key services and the gateway share. Absent only when checking is switched off
     * explicitly — a missing key otherwise stops startup rather than letting the service run
     * trusting unsigned headers.
     */
    @Bean
    public InternalSigningKey internalSigningKey(
            @Value("${platform.internal.signature.enabled:true}") boolean enabled,
            @Value("${platform.internal.signing-key:${INTERNAL_SIGNING_KEY:}}") String key) {
        if (!enabled) {
            org.slf4j.LoggerFactory.getLogger(InternalContextAutoConfiguration.class).warn(
                    "Internal signature checking is OFF: identity headers are trusted unsigned");
            return new InternalSigningKey(null);
        }
        return new InternalSigningKey(InternalContextSignature.decodeKey(key));
    }

    @Bean
    public FilterRegistrationBean<InternalContextFilter> internalContextFilter(
            InternalSigningKey key,
            @Value("${platform.internal.signature.max-skew-seconds:120}") long maxSkew) {
        InternalContextFilter filter = new InternalContextFilter(key.bytes(), maxSkew, Clock.systemUTC());
        FilterRegistrationBean<InternalContextFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(filter.getOrder());
        registration.setEnabled(key.bytes() != null);
        return registration;
    }

    /** The configured key, or null bytes when signature checking is switched off. */
    public record InternalSigningKey(byte[] bytes) { }
}
