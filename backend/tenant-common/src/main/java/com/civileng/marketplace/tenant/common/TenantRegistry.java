package com.civileng.marketplace.tenant.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
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
                     "SELECT tenant_key FROM tenants WHERE status = 'ACTIVE' ORDER BY tenant_key");
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
}
