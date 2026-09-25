package com.civileng.marketplace.tenant.common.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.civileng.marketplace.tenant.common.TenantProperties;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads {@code tenant_integrations} straight out of tenant-service's database, the same way
 * {@code TenantRegistry} reads the tenant list — no HTTP hop, so secrets never cross the network
 * between services, and tenant-service being down does not stop a payment from being taken.
 *
 * <p>Rows are cached for {@code cacheTtl}: a send path hits this on every message, and a credential
 * change taking half a minute to reach every instance is an acceptable price for not opening a JDBC
 * connection per SMS.
 */
@Slf4j
public class JdbcTenantIntegrationStore implements TenantIntegrationStore {

    private static final String COLUMNS = "tenant_key, capability, mode, provider, enabled, "
            + "settings_json, secrets_ciphertext, webhook_token";
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };

    private final TenantProperties.Registry registry;
    private final IntegrationSecrets secrets;
    private final Duration cacheTtl;
    private final Clock clock;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public JdbcTenantIntegrationStore(TenantProperties.Registry registry, IntegrationSecrets secrets,
                                      Duration cacheTtl, Clock clock) {
        this.registry = registry;
        this.secrets = secrets;
        this.cacheTtl = cacheTtl;
        this.clock = clock;
    }

    @Override
    public Optional<TenantIntegration> find(String tenantKey, IntegrationCapability capability) {
        String cacheKey = tenantKey + "|" + capability.name();
        Cached hit = cache.get(cacheKey);
        Instant now = clock.instant();
        if (hit != null && hit.expiresAt.isAfter(now)) {
            return hit.value;
        }
        Optional<TenantIntegration> loaded = query(
                "SELECT " + COLUMNS + " FROM tenant_integrations WHERE tenant_key = ? AND capability = ?",
                tenantKey, capability.name());
        cache.put(cacheKey, new Cached(loaded, now.plus(cacheTtl)));
        return loaded;
    }

    @Override
    public Optional<TenantIntegration> findByWebhookToken(IntegrationCapability capability, String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        // Not cached: webhooks are rare next to sends, and a revoked token must stop working at once.
        return query("SELECT " + COLUMNS
                        + " FROM tenant_integrations WHERE webhook_token = ? AND capability = ?",
                token, capability.name());
    }

    @Override
    public void evict(String tenantKey) {
        cache.keySet().removeIf(key -> key.startsWith(tenantKey + "|"));
    }

    private Optional<TenantIntegration> query(String sql, String first, String second) {
        try (Connection connection = DriverManager.getConnection(
                registry.getUrl(), registry.getUsername(), registry.getPassword());
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, first);
            statement.setString(2, second);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(map(row)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read tenant integrations", e);
        }
    }

    private TenantIntegration map(ResultSet row) throws SQLException {
        String tenantKey = row.getString("tenant_key");
        IntegrationCapability capability = IntegrationCapability.valueOf(row.getString("capability"));
        String ciphertext = row.getString("secrets_ciphertext");
        Map<String, String> opened = secrets.open(ciphertext, tenantKey, capability);
        return new TenantIntegration(
                tenantKey,
                capability,
                IntegrationMode.valueOf(row.getString("mode")),
                row.getString("provider"),
                row.getBoolean("enabled"),
                parse(row.getString("settings_json")),
                opened,
                row.getString("webhook_token"));
    }

    private Map<String, String> parse(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return json.readValue(value, STRING_MAP);
        } catch (Exception e) {
            throw new IllegalStateException("Malformed integration JSON", e);
        }
    }

    private record Cached(Optional<TenantIntegration> value, Instant expiresAt) {
    }
}
