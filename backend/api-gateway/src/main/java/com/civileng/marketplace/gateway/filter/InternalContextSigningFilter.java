package com.civileng.marketplace.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Clock;

/**
 * Signs the identity the gateway forwards: the tenant it resolved and, on authenticated routes, the
 * user the JWT filter verified. Services refuse identity headers without this signature, so a
 * caller that reaches a service some other way than through here cannot claim to be anyone.
 *
 * <p>Runs last before the request is proxied, after every route filter has set its headers.
 * Clients cannot supply the header themselves: IdentityHeaderStripFilter removes any
 * {@code X-Internal-*} they send.
 */
@Component
@Slf4j
public class InternalContextSigningFilter implements GlobalFilter, Ordered {

    private final byte[] key;
    private final Clock clock;

    @Autowired
    public InternalContextSigningFilter(
            @Value("${platform.internal.signature.enabled:true}") boolean enabled,
            @Value("${platform.internal.signing-key:${INTERNAL_SIGNING_KEY:}}") String key) {
        this(enabled ? InternalContextSignature.decodeKey(key) : null, Clock.systemUTC());
        if (!enabled) {
            log.warn("Internal signature is OFF: services will receive unsigned identity headers");
        }
    }

    InternalContextSigningFilter(byte[] key, Clock clock) {
        this.key = key;
        this.clock = clock;
    }

    @Override
    public int getOrder() {
        // After route filters (the JWT filter among them), before NettyRoutingFilter at LOWEST.
        return Ordered.LOWEST_PRECEDENCE - 10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        if (key == null || !InternalContextSignature.carriesIdentity(headers::getFirst)) {
            return chain.filter(exchange);
        }
        String signature = InternalContextSignature.sign(key, headers::getFirst, clock.instant().getEpochSecond());
        return chain.filter(exchange.mutate()
                .request(r -> r.headers(h -> h.set(InternalContextSignature.HEADER, signature)))
                .build());
    }
}
