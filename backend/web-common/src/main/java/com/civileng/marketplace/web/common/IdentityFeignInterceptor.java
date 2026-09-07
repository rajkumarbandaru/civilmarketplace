package com.civileng.marketplace.web.common;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Copies the gateway-injected caller identity onto outgoing Feign calls.
 *
 * <p>Without this, a service-to-service hop arrives anonymous, and the receiving service has to
 * choose between refusing it and having no role check at all. admin-service's Feign clients took
 * the second option, which is why booking-service's and payment-service's admin controllers could
 * not be gated: gating them would have broken the console that calls them.
 *
 * <p>Modelled on {@code tenant-common}'s {@code TenantFeignInterceptor}, and complementary to it —
 * that one carries which workspace, this one carries who.
 *
 * <p>Nothing is added when there is no inbound request, which is the correct behaviour for
 * scheduled work: a background sweep has no caller, and inventing one would let a job pass a
 * check no human authorised. Those calls stay anonymous and are refused by anything gated, so a
 * job that needs privileged data must be given an explicit path rather than borrowing a
 * passer-by's identity.
 */
public class IdentityFeignInterceptor implements RequestInterceptor {

    private static final String[] IDENTITY_HEADERS = {
            "X-User-Id", "X-User-Email", "X-User-Role", "X-User-Name"
    };

    @Override
    public void apply(RequestTemplate template) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();

        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return;
        }

        for (String header : IDENTITY_HEADERS) {
            String value = servletAttributes.getRequest().getHeader(header);

            // Only set what is absent: a Feign client that names the header as an explicit
            // @RequestHeader parameter has already made a deliberate choice about it, and this
            // must not overwrite it. admin-service's AuthServiceClient does exactly that.
            if (value != null && !template.headers().containsKey(header)) {
                template.header(header, value);
            }
        }
    }
}
