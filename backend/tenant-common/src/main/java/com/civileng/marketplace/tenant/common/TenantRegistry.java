package com.civileng.marketplace.tenant.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

/**
 * Reads the platform tenant list straight out of tenant-service's database over plain JDBC.
 *
 * <p>Deliberately not a Feign call: every tenanted service needs this list before its
 * EntityManagerFactory can be built, and an HTTP dependency would mean nothing in the platform
 * starts until tenant-service is up and registered in Eureka. A short-lived JDBC connection at
 * boot has no such ordering problem.
 */
@Slf4j
@RequiredArgsConstructor
public class TenantRegistry {

    private final TenantProperties.Registry config;

    public List<String> activeTenantKeys() {
        List<String> keys = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(
                config.getUrl(), config.getUsername(), config.getPassword());
             PreparedStatement statement = connection.prepareStatement(
                     // MAINTENANCE is a live tenant whose writes are paused for a move or a
                     // restore: its schemas must stay migrated and routed like any other.
                     "SELECT tenant_key FROM tenants WHERE status IN ('ACTIVE', 'MAINTENANCE') ORDER BY tenant_key");
             ResultSet rows = statement.executeQuery()) {

            while (rows.next()) {
                keys.add(rows.getString("tenant_key"));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Could not read the tenant registry at " + config.getUrl()
                            + " — a tenanted service cannot start without knowing its tenants", e);
        }
        log.info("Tenant registry lists {} active tenant(s): {}", keys.size(), keys);
        return keys;
    }

    /**
     * Every tenant's placement, from the control plane's placement map. Empty when the map does
     * not exist yet (tenant-service not migrated): everything is then on the default cluster,
     * which is exactly what it was before placements existed.
     */
    public Map<String, TenantPlacement> placements() {
        Map<String, TenantPlacement> placements = new HashMap<>();
        try (Connection connection = DriverManager.getConnection(
                config.getUrl(), config.getUsername(), config.getPassword());
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT t.tenant_key, t.db_cluster_id, t.placement_epoch, t.status, c.host, c.port "
                             + "FROM tenants t JOIN db_clusters c ON c.cluster_id = t.db_cluster_id");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                String key = rows.getString("tenant_key");
                placements.put(key, new TenantPlacement(key, rows.getString("db_cluster_id"), rows.getString("host"),
                        rows.getInt("port"), rows.getLong("placement_epoch"), rows.getString("status")));
            }
        } catch (SQLException e) {
            log.warn("Tenant placement map unavailable ({}); routing every tenant to the default cluster", e.getMessage());
            return Map.of();
        }
        return placements;
    }
}
