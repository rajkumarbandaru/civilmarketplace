package com.civileng.marketplace.tenant.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates a tenant's schema if it is missing and brings it up to the service's current Flyway
 * version. Each tenant schema carries its own {@code flyway_schema_history}, so tenants can be
 * onboarded at any time and still land on the same migration set.
 */
@Slf4j
@RequiredArgsConstructor
public class TenantSchemaMigrator {

    private final DataSource dataSource;
    private final TenantSchemas schemas;
    private final TenantProperties properties;
    private final FlywayProperties flywayProperties;

    public void migrate(String tenantKey) {
        migrateSchema(schemas.schemaFor(tenantKey));
        log.info("Tenant '{}' migrated to current version in schema {}",
                tenantKey, schemas.schemaFor(tenantKey));
    }

    /**
     * Migrates one schema by name.
     *
     * <p>Exists for the legacy schema named in the datasource URL, which holds no tenant's data but
     * is still what Hibernate's {@code validate} inspects at startup. Leaving it behind means the
     * first migration that adds a column stops the service from booting — with an error naming a
     * table that is perfectly correct in every schema anyone actually reads.
     */
    public void migrateSchema(String schema) {
        createSchemaIfMissing(schema);

        FluentConfiguration configuration = Flyway.configure()
                .dataSource(dataSource)
                // Flyway opens its own connections from the pool, so the target schema has to be
                // named rather than inherited from whatever catalog a connection happens to hold.
                .schemas(schema)
                .defaultSchema(schema)
                .locations(properties.getMigrationLocations().split(","))
                .baselineOnMigrate(true);

        // The service's own spring.flyway.* settings still describe how its migrations must be
        // read, even though Boot's Flyway is switched off. notification-service is the case that
        // proves it: its seed data contains Thymeleaf ${otp} placeholders and it sets
        // placeholder-replacement: false, without which Flyway refuses to parse the file at all.
        if (flywayProperties != null) {
            configuration.placeholderReplacement(flywayProperties.isPlaceholderReplacement());
            if (flywayProperties.getPlaceholders() != null
                    && !flywayProperties.getPlaceholders().isEmpty()) {
                configuration.placeholders(flywayProperties.getPlaceholders());
            }
            if (flywayProperties.getTable() != null) {
                configuration.table(flywayProperties.getTable());
            }
            if (flywayProperties.getLocations() != null
                    && !flywayProperties.getLocations().isEmpty()) {
                configuration.locations(flywayProperties.getLocations().toArray(new String[0]));
            }
            configuration.outOfOrder(flywayProperties.isOutOfOrder());
        }

        configuration.load().migrate();
    }

    private void createSchemaIfMissing(String schema) {
        // schema is derived from a TenantKey-validated key, so it cannot carry anything but
        // [a-z0-9_] by the time it reaches this DDL.
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + schema + "`"
                    + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create tenant schema " + schema, e);
        }
    }
}
