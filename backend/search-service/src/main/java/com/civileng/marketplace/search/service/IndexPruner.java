package com.civileng.marketplace.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cat.indices.IndicesRecord;
import com.civileng.marketplace.search.config.TenantIndex;
import com.civileng.marketplace.tenant.common.TenantRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Deletes search indices that no active tenant owns.
 *
 * <p>The reindex sweep only ever writes to the tenants the registry lists, so anything else in
 * Elasticsearch is left behind rather than maintained: the pre-tenancy platform-wide
 * {@code profiles}/{@code services} indices, and the indices of a tenant that has since been
 * suspended or deleted. None of it is readable — the gateway refuses a non-ACTIVE tenant and every
 * query names a tenant's own index — but it is a stale copy of that tenant's data sitting in the
 * cluster with nothing to ever refresh or remove it.
 *
 * <p>Deleting is safe because these indices are a read replica: a tenant coming back to ACTIVE is
 * rebuilt from the owning services by the provisioning callback and the next sweep.
 *
 * <p>Two things it will not do: run when the registry lists no active tenants at all (that is far
 * more likely to be a bad registry read than a platform with zero tenants, and it would delete
 * every index), and touch an index whose name is not one this service creates.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IndexPruner {

    private final ElasticsearchClient elasticsearchClient;
    private final TenantRegistry tenantRegistry;

    @Value("${search.prune-orphan-indices:true}")
    private boolean enabled;

    public void prune() {
        if (!enabled) {
            return;
        }

        List<String> activeTenants;
        try {
            activeTenants = tenantRegistry.activeTenantKeys();
        } catch (RuntimeException e) {
            log.error("Index prune skipped: could not read the tenant registry", e);
            return;
        }
        if (activeTenants.isEmpty()) {
            log.warn("Index prune skipped: the registry lists no active tenants, which is more "
                    + "likely a bad read than a platform with none");
            return;
        }

        Set<String> keep = new LinkedHashSet<>();
        for (String tenantKey : activeTenants) {
            keep.add(TenantIndex.PROFILES + "_" + tenantKey);
            keep.add(TenantIndex.SERVICES + "_" + tenantKey);
        }

        List<String> orphans = new ArrayList<>();
        try {
            for (IndicesRecord record : elasticsearchClient.cat().indices().valueBody()) {
                String name = record.index();
                if (name != null && ownedName(name) && !keep.contains(name)) {
                    orphans.add(name);
                }
            }
        } catch (IOException | RuntimeException e) {
            log.error("Index prune skipped: could not list indices", e);
            return;
        }

        for (String orphan : orphans) {
            try {
                elasticsearchClient.indices().delete(d -> d.index(orphan));
                log.info("Pruned orphan search index '{}' — no active tenant owns it", orphan);
            } catch (IOException | RuntimeException e) {
                log.error("Could not prune orphan search index '{}'", orphan, e);
            }
        }
    }

    /**
     * Only names this service creates are candidates. {@code profiles}/{@code services} with no
     * suffix are the pre-tenancy indices; the prefixed forms are per-tenant. Anything else in the
     * cluster belongs to something that is not search-service.
     */
    private boolean ownedName(String name) {
        for (String base : List.of(TenantIndex.PROFILES, TenantIndex.SERVICES)) {
            if (name.equals(base) || name.startsWith(base + "_")) {
                return true;
            }
        }
        return false;
    }
}
