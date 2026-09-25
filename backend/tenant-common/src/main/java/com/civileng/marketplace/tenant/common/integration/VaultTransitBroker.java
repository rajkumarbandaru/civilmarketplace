package com.civileng.marketplace.tenant.common.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * {@link SecretsBroker} on HashiCorp Vault's Transit engine: encryption as a service. Each
 * (tenant, capability) has its own named key, created on first seal; ciphertexts
 * ({@code vault:v1:...}) are stored by the caller, keys never leave Vault. This service's token
 * carries its policy — payment-service may open only {@code payment-tenant-*}, tenant-service may
 * seal and destroy but open nothing — and Vault's audit device records every open.
 */
@Slf4j
public class VaultTransitBroker implements SecretsBroker {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String address;
    private final String token;
    private final String mount;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public VaultTransitBroker(String address, String token, String mount) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("platform.integrations.vault.token is not set for " + address);
        }
        this.address = address.endsWith("/") ? address.substring(0, address.length() - 1) : address;
        this.token = token;
        this.mount = mount == null || mount.isBlank() ? "transit" : mount;
    }

    @Override
    public String seal(String plaintext, String tenantKey, IntegrationCapability capability) {
        String b64 = Base64.getEncoder().encodeToString(plaintext.getBytes(StandardCharsets.UTF_8));
        // Encrypting under a key that does not exist yet creates it (the writer's policy allows it).
        JsonNode r = call("POST", "/encrypt/" + SecretsBroker.keyName(tenantKey, capability), Map.of("plaintext", b64),
                tenantKey, capability);
        return r.path("data").path("ciphertext").asText();
    }

    @Override
    public String open(String sealed, String tenantKey, IntegrationCapability capability) {
        JsonNode r = call("POST", "/decrypt/" + SecretsBroker.keyName(tenantKey, capability), Map.of("ciphertext", sealed),
                tenantKey, capability);
        return new String(Base64.getDecoder().decode(r.path("data").path("plaintext").asText()), StandardCharsets.UTF_8);
    }

    @Override
    public int shred(String tenantKey) {
        JsonNode keys = call("LIST", "/keys", null, tenantKey, null).path("data").path("keys");
        int destroyed = 0;
        for (JsonNode k : keys) {
            String name = k.asText();
            if (name.endsWith("-tenant-" + tenantKey)) {
                call("POST", "/keys/" + name + "/config", Map.of("deletion_allowed", true), tenantKey, null);
                call("DELETE", "/keys/" + name, null, tenantKey, null);
                destroyed++;
            }
        }
        log.warn("Destroyed {} encryption key(s) of tenant '{}'", destroyed, tenantKey);
        return destroyed;
    }

    @Override
    public boolean owns(String sealed) {
        return sealed != null && sealed.startsWith("vault:");
    }

    private JsonNode call(String method, String path, Object body, String tenantKey, IntegrationCapability capability) {
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(address + "/v1/" + mount + path))
                    .timeout(Duration.ofSeconds(10))
                    .header("X-Vault-Token", token)
                    .header("Content-Type", "application/json");
            HttpRequest.BodyPublisher pub = body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body));
            HttpResponse<String> res = http.send(req.method(method, pub).build(), HttpResponse.BodyHandlers.ofString());
            int s = res.statusCode();
            if (s == 204 || res.body().isBlank()) {
                return JSON.createObjectNode();
            }
            JsonNode node = JSON.readTree(res.body());
            if (s >= 200 && s < 300) {
                return node;
            }
            String errors = node.path("errors").toString();
            if (s == 403) {
                throw new IllegalStateException("This service may not use the " + path.split("/")[1] + " of '" + tenantKey
                        + "' (secrets broker policy)");
            }
            if (capability != null && s == 400 && errors.contains("encryption key not found")) {
                throw new SecretsDestroyedException(tenantKey, capability);
            }
            if ("LIST".equals(method) && s == 404) {
                return JSON.createObjectNode();
            }
            throw new IllegalStateException("Secrets broker refused " + method + " " + path + " (" + s + "): " + errors);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted talking to the secrets broker", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("The secrets broker is unreachable at " + address, e);
        }
    }
}
