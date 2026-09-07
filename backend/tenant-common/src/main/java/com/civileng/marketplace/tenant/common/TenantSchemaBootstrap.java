package com.civileng.marketplace.tenant.common;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Brings every active tenant's schema up to date before JPA starts.
 *
 * <p>Ordering matters here: {@code ddl-auto: validate} inspects the bootstrap tenant's schema
 * while the EntityManagerFactory is built, so the migrations have to have run by then. The
 * EMF is made to depend on this bean in {@link TenantAutoConfiguration} for exactly that reason.
 */
@Slf4j
@RequiredArgsConstructor
public class TenantSchemaBootstrap {

    private final TenantRegistry registry;
    private final TenantSchemaMigrator migrator;
    private final DataSource dataSource;

    private volatile List<String> tenantKeys = List.of();

    @PostConstruct
    public void migrateAll() {
        migrateLegacySchema();
        tenantKeys = registry.activeTenantKeys();
        if (tenantKeys.isEmpty()) {
            throw new IllegalStateException(
                    "The tenant registry has no ACTIVE tenants. At least one must exist — "
                            + "Hibernate validates its mappings against a real tenant schema at startup.");
        }
        tenantKeys.forEach(migrator::migrate);
    }

    /**
     * Keeps the schema named in the datasource URL structurally current.
     *
     * <p>No tenant's data lives there any more, but {@code ddl-auto: validate} still inspects it
     * while the EntityManagerFactory is built. Once Boot's own Flyway was switched off for
     * multi-tenancy, nothing migrated it — so it froze at whatever version was current that day
     * and the next migration to add a column would refuse to start the service.
     *
     * <p>Best-effort: a service whose tenant schemas are all correct should not fail to start
     * because a legacy schema could not be brought along.
     */
    private void migrateLegacySchema() {
        try (Connection connection = dataSource.getConnection()) {
            String schema = connection.getCatalog();
            if (schema == null || schema.isBlank()) {
                return;
            }
            migrator.migrateSchema(schema);
            log.info("Legacy schema {} kept current for schema validation", schema);
        } catch (SQLException | RuntimeException e) {
            log.warn("Could not migrate the legacy schema; ddl-auto validation may fail", e);
        }
    }

    public List<String> tenantKeys() {
        return tenantKeys;
    }

    /**
     * The tenant Hibernate uses when no request is in flight — schema validation at startup, and
     * anything else that reaches JPA off a request thread.
     */
    public String bootstrapTenant() {
        return tenantKeys.get(0);
    }
}
