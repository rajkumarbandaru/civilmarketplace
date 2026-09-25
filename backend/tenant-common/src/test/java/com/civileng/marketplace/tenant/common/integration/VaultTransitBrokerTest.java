package com.civileng.marketplace.tenant.common.integration;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Vault Transit client against a stub of the endpoints it uses: per-(tenant, capability) key
 * names, token on every call, a policy refusal and a destroyed key told apart, shredding only the
 * tenant's own keys. The live check exercises the real Vault.
 */
class VaultTransitBrokerTest {

    private HttpServer server;
    private final Map<String, Boolean> keys = new ConcurrentHashMap<>();
    private final List<String> calls = new ArrayList<>();
    private final List<String> tokens = new ArrayList<>();
    private VaultTransitBroker broker;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/transit/", ex -> {
            String path = ex.getRequestURI().getPath().substring("/v1/transit/".length());
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            calls.add(ex.getRequestMethod() + " " + path);
            tokens.add(ex.getRequestHeaders().getFirst("X-Vault-Token"));
            int status = 200;
            String out = "{}";
            if (path.startsWith("encrypt/")) {
                String key = path.substring(8);
                keys.put(key, true);
                String pt = body.replaceAll(".*\"plaintext\":\"([^\"]+)\".*", "$1");
                out = "{\"data\":{\"ciphertext\":\"vault:v1:" + key + ":" + pt + "\"}}";
            } else if (path.startsWith("decrypt/")) {
                String key = path.substring(8);
                if (key.startsWith("ai-")) {
                    status = 403;
                    out = "{\"errors\":[\"permission denied\"]}";
                } else if (!keys.containsKey(key)) {
                    status = 400;
                    out = "{\"errors\":[\"encryption key not found\"]}";
                } else {
                    String ct = body.replaceAll(".*\"ciphertext\":\"([^\"]+)\".*", "$1");
                    out = "{\"data\":{\"plaintext\":\"" + ct.substring(ct.lastIndexOf(':') + 1) + "\"}}";
                }
            } else if (path.equals("keys") && ex.getRequestMethod().equals("LIST")) {
                out = "{\"data\":{\"keys\":[" + String.join(",", keys.keySet().stream().map(k -> "\"" + k + "\"").toList()) + "]}}";
            } else if (path.startsWith("keys/") && ex.getRequestMethod().equals("DELETE")) {
                keys.remove(path.substring(5));
                status = 204;
            } else if (path.endsWith("/config")) {
                status = 204;
            }
            byte[] bytes = out.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
            if (status != 204) ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();
        broker = new VaultTransitBroker("http://127.0.0.1:" + server.getAddress().getPort(), "payment-token", "transit");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void eachTenantAndCapabilityHasItsOwnKey() {
        String sealed = broker.seal("rzp-secret", "acme", IntegrationCapability.PAYMENT);
        assertThat(sealed).startsWith("vault:v1:payment-tenant-acme:");
        assertThat(sealed).doesNotContain("rzp-secret");
        assertThat(broker.open(sealed, "acme", IntegrationCapability.PAYMENT)).isEqualTo("rzp-secret");
        assertThat(calls).containsExactly("POST encrypt/payment-tenant-acme", "POST decrypt/payment-tenant-acme");
        assertThat(tokens).containsOnly("payment-token");
        assertThat(broker.owns(sealed)).isTrue();
        assertThat(broker.owns("v1.abc.def")).isFalse();
    }

    @Test
    void aPolicyRefusalAndADestroyedKeyAreToldApart() {
        assertThatThrownBy(() -> broker.open("vault:v1:x", "acme", IntegrationCapability.AI))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("policy");
        String sealed = broker.seal("s", "acme", IntegrationCapability.EMAIL);
        broker.shred("acme");
        assertThatThrownBy(() -> broker.open(sealed, "acme", IntegrationCapability.EMAIL))
                .isInstanceOf(SecretsDestroyedException.class).hasMessageContaining("destroyed");
    }

    @Test
    void shreddingDestroysOnlyThatTenantsKeys() {
        broker.seal("a", "acme", IntegrationCapability.PAYMENT);
        broker.seal("b", "acme", IntegrationCapability.EMAIL);
        broker.seal("c", "bigacme", IntegrationCapability.PAYMENT);
        assertThat(broker.shred("acme")).isEqualTo(2);
        assertThat(keys.keySet()).containsExactly("payment-tenant-bigacme");
    }

    @Test
    void perFieldSealingKeepsUntouchedFieldsWithoutOpeningThem() {
        IntegrationSecrets secrets = new IntegrationSecrets(broker, null);
        String first = secrets.seal(null, Map.of("keySecret", "k1", "webhookSecret", "w1"), List.of(), "acme",
                IntegrationCapability.PAYMENT);
        calls.clear();
        String second = secrets.seal(first, Map.of("keySecret", "k2"), List.of("webhookSecret"), "acme",
                IntegrationCapability.PAYMENT);
        assertThat(calls).containsExactly("POST encrypt/payment-tenant-acme");   // no decrypt
        assertThat(secrets.open(second, "acme", IntegrationCapability.PAYMENT))
                .containsEntry("keySecret", "k2").containsEntry("webhookSecret", "w1");
    }

    @Test
    void theOldSharedKeyFormatStillReadsAndNeitherConfiguredIsAnError() {
        IntegrationCipher legacy = new IntegrationCipher(Base64.getEncoder().encodeToString(new byte[32]));
        IntegrationSecrets secrets = new IntegrationSecrets(broker, legacy);
        String blob = legacy.encrypt("{\"apiKey\":\"brevo-1\"}", "acme", IntegrationCapability.EMAIL);
        assertThat(IntegrationSecrets.isLegacy(blob)).isTrue();
        assertThat(secrets.open(blob, "acme", IntegrationCapability.EMAIL)).containsEntry("apiKey", "brevo-1");
        assertThatThrownBy(() -> new IntegrationSecrets(null, null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new VaultTransitBroker("http://x", "", null)).isInstanceOf(IllegalStateException.class);
    }
}
