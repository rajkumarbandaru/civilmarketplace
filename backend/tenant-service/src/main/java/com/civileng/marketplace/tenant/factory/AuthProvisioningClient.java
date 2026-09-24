package com.civileng.marketplace.tenant.factory;

import com.civileng.marketplace.tenant.common.InternalContextAutoConfiguration.InternalSigningKey;
import com.civileng.marketplace.tenant.common.InternalContextSignature;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/**
 * Calls auth-service inside a new tenant, as the operator who published it: creates the owner's
 * account and sends their invitation. Direct over the service network (never the gateway, which
 * seals {@code /api/v1/auth/admin}), with identity signed like any service-to-service call.
 */
@Component
public class AuthProvisioningClient {

    private final RestClient client;
    private final InternalSigningKey key;
    private final Clock clock;

    public record Owner(Long userId, String email, boolean created) { }

    public AuthProvisioningClient(RestClient.Builder loadBalancedRestClientBuilder, InternalSigningKey key, Clock clock) {
        this.client = loadBalancedRestClientBuilder.baseUrl("http://auth-service").build();
        this.key = key;
        this.clock = clock;
    }

    public Owner ensureOwner(String tenantKey, String actorId, String name, String email) {
        return client.post().uri("/api/v1/auth/admin/tenant-owner")
                .headers(h -> identity(tenantKey, actorId).forEach(h::set))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", name == null ? "" : name, "email", email))
                .retrieve().body(Owner.class);
    }

    public void invite(String tenantKey, String actorId, Long userId, String linkBase, String workspaceName) {
        client.post().uri("/api/v1/auth/admin/users/{id}/invitation", userId)
                .headers(h -> identity(tenantKey, actorId).forEach(h::set))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("linkBase", linkBase, "workspaceName", workspaceName))
                .retrieve().toBodilessEntity();
    }

    Map<String, String> identity(String tenantKey, String actorId) {
        Map<String, String> h = new HashMap<>();
        h.put("X-Tenant-Id", tenantKey);
        h.put("X-User-Role", "SUPER_ADMIN");
        if (actorId != null) h.put("X-User-Id", actorId);
        if (key.bytes() != null) {
            h.put(InternalContextSignature.HEADER, InternalContextSignature.sign(key.bytes(), h::get,
                    clock.instant().getEpochSecond()));
        }
        return h;
    }
}
