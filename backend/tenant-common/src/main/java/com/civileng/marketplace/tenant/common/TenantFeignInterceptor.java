package com.civileng.marketplace.tenant.common;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.util.Collection;
import java.util.Map;

import feign.RequestInterceptor;
import feign.RequestTemplate;

/**
 * Carries the caller's tenant and identity on outbound Feign calls, signed.
 *
 * <p>Service-to-service calls go direct through Eureka, not back through the gateway, so nothing
 * else would set {@code X-Tenant-Id} on them. Without this a Feign call from one tenanted service
 * to another is rejected by the callee's tenant filter — or worse, if it were defaulted, served
 * from the wrong schema.
 *
 * <p>It also signs what it sends ({@link InternalContextSignature}), since the callee refuses
 * identity headers without a signature. The user headers are copied from the inbound request here
 * rather than left to web-common's identity interceptor: Feign runs interceptors in no fixed order,
 * and a header added after signing would not match the signature. That interceptor only fills
 * absent headers, so running after this one it changes nothing.
 */
public class TenantFeignInterceptor implements RequestInterceptor {

    private final byte[] key;
    private final Clock clock;

    public TenantFeignInterceptor() {
        this(null, Clock.systemUTC());
    }

    /** {@code key} null: propagate the tenant only, unsigned (signature checking switched off). */
    public TenantFeignInterceptor(byte[] key, Clock clock) {
        this.key = key;
        this.clock = clock;
    }

    @Override
    public void apply(RequestTemplate template) {
        String tenantId = TenantContext.get();
        if (tenantId != null) {
            template.removeHeader(TenantHeaderFilter.TENANT_HEADER);
            template.header(TenantHeaderFilter.TENANT_HEADER, tenantId);
        }
        if (key == null) {
            return;
        }
        copyInboundIdentity(template);
        template.removeHeader(InternalContextSignature.HEADER);
        java.util.function.Function<String, String> header = name -> first(template, name);
        if (InternalContextSignature.carriesIdentity(header)) {
            template.header(InternalContextSignature.HEADER,
                    InternalContextSignature.sign(key, header, clock.instant().getEpochSecond()));
        }
    }

    private static void copyInboundIdentity(RequestTemplate template) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return;
        }
        for (String name : InternalContextSignature.SIGNED_HEADERS) {
            if (name.equals(TenantHeaderFilter.TENANT_HEADER)) continue;
            String value = attrs.getRequest().getHeader(name);
            // A client that named the header explicitly (@RequestHeader) made a deliberate choice.
            if (value != null && first(template, name) == null) {
                template.header(name, value);
            }
        }
    }

    private static String first(RequestTemplate template, String name) {
        for (Map.Entry<String, Collection<String>> e : template.headers().entrySet()) {
            if (e.getKey().equalsIgnoreCase(name) && !e.getValue().isEmpty()) {
                return e.getValue().iterator().next();
            }
        }
        return null;
    }
}
