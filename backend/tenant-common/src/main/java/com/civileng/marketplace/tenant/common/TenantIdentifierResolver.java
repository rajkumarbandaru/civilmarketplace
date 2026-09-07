package com.civileng.marketplace.tenant.common;

import lombok.RequiredArgsConstructor;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;

import java.util.Map;

/**
 * Tells Hibernate which tenant a session belongs to. Hibernate also resolves a tenant while
 * bootstrapping the SessionFactory, before any request exists, which is what
 * {@code bootstrapTenant} covers — it must be a schema that really has the full table set, since
 * that is the one schema validation runs against.
 */
@RequiredArgsConstructor
public class TenantIdentifierResolver
        implements CurrentTenantIdentifierResolver<String>, HibernatePropertiesCustomizer {

    private final String bootstrapTenant;

    @Override
    public String resolveCurrentTenantIdentifier() {
        String tenantId = TenantContext.get();
        return tenantId != null ? tenantId : bootstrapTenant;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
