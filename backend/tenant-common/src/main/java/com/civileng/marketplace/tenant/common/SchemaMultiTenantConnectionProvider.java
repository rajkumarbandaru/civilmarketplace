package com.civileng.marketplace.tenant.common;

import lombok.RequiredArgsConstructor;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Routes each Hibernate connection to the calling tenant's schema. One Hikari pool is shared
 * across tenants and the catalog is switched per checkout — on MySQL a catalog <em>is</em> a
 * schema, so {@code setCatalog} is the whole of the switch.
 *
 * <p>The catalog is reset on release. A pooled connection outlives the request that borrowed it,
 * and handing it back still pointed at tenant A's schema is exactly how the next tenant ends up
 * reading the wrong data.
 */
@RequiredArgsConstructor
public class SchemaMultiTenantConnectionProvider
        implements MultiTenantConnectionProvider<String> {

    private final DataSource dataSource;
    private final TenantSchemas schemas;
    private final String bootstrapCatalog;

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection connection = dataSource.getConnection();
        connection.setCatalog(schemas.schemaFor(tenantIdentifier));
        return connection;
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection)
            throws SQLException {
        try {
            connection.setCatalog(bootstrapCatalog);
        } finally {
            connection.close();
        }
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return MultiTenantConnectionProvider.class.equals(unwrapType)
                || SchemaMultiTenantConnectionProvider.class.equals(unwrapType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T unwrap(Class<T> unwrapType) {
        if (isUnwrappableAs(unwrapType)) {
            return (T) this;
        }
        throw new UnsupportedOperationException("Cannot unwrap to " + unwrapType);
    }
}
