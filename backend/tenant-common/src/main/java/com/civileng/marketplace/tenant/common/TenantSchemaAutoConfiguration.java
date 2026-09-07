package com.civileng.marketplace.tenant.common;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.boot.autoconfigure.orm.jpa.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Turns a single-schema Spring Boot service into a schema-per-tenant one: every query is routed to
 * {@code <schema-prefix>_<tenantKey>} based on the tenant bound to the calling thread. Services opt
 * in with {@code platform.tenant.enabled=true} plus a {@code schema-prefix}; nothing else in the
 * service changes.
 *
 * <p>Only active where Hibernate and Flyway are on the classpath, which is what lets a service with
 * no relational store share the tenant runtime in {@link TenantAutoConfiguration} without acquiring
 * a JPA stack it has no DataSource for.
 *
 * <p>Boot's own Flyway auto-configuration must be off in a tenanted service
 * ({@code spring.flyway.enabled: false}) — it would migrate the single legacy schema named in the
 * datasource URL, which is not where any tenant's data lives.
 */
@Slf4j
@AutoConfiguration(after = TenantAutoConfiguration.class, before = HibernateJpaAutoConfiguration.class)
@EnableConfigurationProperties({TenantProperties.class, FlywayProperties.class})
@ConditionalOnClass({HibernateJpaAutoConfiguration.class, org.flywaydb.core.Flyway.class})
@ConditionalOnProperty(prefix = "platform.tenant", name = "enabled", havingValue = "true")
public class TenantSchemaAutoConfiguration {

    @Bean
    public TenantSchemas tenantSchemas(TenantProperties properties) {
        if (properties.getSchemaPrefix() == null || properties.getSchemaPrefix().isBlank()) {
            throw new IllegalStateException(
                    "platform.tenant.schema-prefix must be set when multi-tenancy is enabled");
        }
        return new TenantSchemas(properties.getSchemaPrefix());
    }

    @Bean
    public TenantSchemaMigrator tenantSchemaMigrator(DataSource dataSource, TenantSchemas schemas,
                                                     TenantProperties properties,
                                                     ObjectProvider<FlywayProperties>
                                                             flywayProperties) {
        return new TenantSchemaMigrator(dataSource, schemas, properties,
                flywayProperties.getIfAvailable());
    }

    @Bean
    public TenantSchemaBootstrap tenantSchemaBootstrap(TenantRegistry registry,
                                                       TenantSchemaMigrator migrator,
                                                       DataSource dataSource) {
        return new TenantSchemaBootstrap(registry, migrator, dataSource);
    }

    @Bean
    public TenantIdentifierResolver tenantIdentifierResolver(TenantSchemaBootstrap bootstrap) {
        return new TenantIdentifierResolver(bootstrap.bootstrapTenant());
    }

    @Bean
    public HibernatePropertiesCustomizer multiTenantConnectionProviderCustomizer(
            DataSource dataSource, TenantSchemas schemas) {
        SchemaMultiTenantConnectionProvider provider = new SchemaMultiTenantConnectionProvider(
                dataSource, schemas, defaultCatalogOf(dataSource));
        return properties ->
                properties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, provider);
    }

    /**
     * JPA must not initialise until every tenant schema has been migrated — schema validation
     * runs against the bootstrap tenant while the factory is being built.
     */
    @Bean
    public static EntityManagerFactoryDependsOnPostProcessor
            entityManagerFactoryDependsOnTenantSchemas() {
        return new EntityManagerFactoryDependsOnPostProcessor("tenantSchemaBootstrap") {
        };
    }

    @Bean
    public TenantAdminEndpoint tenantAdminEndpoint(TenantSchemaBootstrap bootstrap,
                                                   ObjectProvider<TenantSchemaMigrator> migrator) {
        return new TenantAdminEndpoint(bootstrap, migrator.getObject());
    }

    private String defaultCatalogOf(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getCatalog();
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Could not read the datasource's default catalog", e);
        }
    }
}
