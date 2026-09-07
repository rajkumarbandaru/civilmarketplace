package com.civileng.marketplace.search.config;

import com.civileng.marketplace.tenant.common.TenantContext;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Component;

/**
 * The one place that turns a tenant into an Elasticsearch index name.
 *
 * <p>This service is the platform's only store that is not MySQL, so it cannot inherit the
 * schema-per-tenant isolation the other eleven services get from {@code tenant-common}. An index
 * per tenant is the equivalent: a query physically cannot reach another tenant's documents, rather
 * than relying on every query remembering to filter on a tenant field. One forgotten filter in one
 * of nine query builders would be a cross-tenant read, and nothing in a test would notice.
 *
 * <p>Every name is derived from {@link TenantContext#require()}, which throws when no tenant is
 * bound. That is deliberate: background work that forgot {@code runAs} must fail loudly instead of
 * quietly resolving to a shared index.
 */
@Component("tenantIndex")
public class TenantIndex {

    public static final String PROFILES = "profiles";
    public static final String SERVICES = "services";

    /** {@code profiles_acme}. Also the value the documents' SpEL index names resolve to. */
    public String profiles() {
        return name(PROFILES);
    }

    public String services() {
        return name(SERVICES);
    }

    public IndexCoordinates profilesIndex() {
        return IndexCoordinates.of(profiles());
    }

    public IndexCoordinates servicesIndex() {
        return IndexCoordinates.of(services());
    }

    private String name(String base) {
        return base + "_" + TenantContext.require();
    }
}
