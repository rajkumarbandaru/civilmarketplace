package com.civileng.marketplace.tenant.common;

import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Which cluster each tenant's queries go to, read from the control plane's placement map and
 * kept current: re-read when tenant-service announces a placement change, and every
 * {@code refreshSeconds} as a backstop for a missed event.
 *
 * <p>When a tenant's epoch changes the service says so ({@link TenantPlacementAck}); a tenant move
 * does not resume writes until every service storing that tenant has.
 */
@Slf4j
public class TenantPlacements implements AutoCloseable {

    private final TenantRegistry registry;
    private final ClusterDataSources clusters;
    private final Consumer<TenantPlacement> onMoved;
    private final Map<String, TenantPlacement> current = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timer;

    public TenantPlacements(TenantRegistry registry, ClusterDataSources clusters, Consumer<TenantPlacement> onMoved,
                            int refreshSeconds) {
        this.registry = registry;
        this.clusters = clusters;
        this.onMoved = onMoved;
        current.putAll(registry.placements());
        if (refreshSeconds > 0) {
            timer = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "tenant-placements");
                t.setDaemon(true);
                return t;
            });
            timer.scheduleWithFixedDelay(this::refreshQuietly, refreshSeconds, refreshSeconds, TimeUnit.SECONDS);
        } else {
            timer = null;
        }
    }

    /** The pool for this tenant's cluster; the default cluster for a tenant with no placement. */
    public DataSource dataSourceFor(String tenantKey) {
        TenantPlacement p = current.get(tenantKey);
        return p == null ? clusters.primary() : clusters.forCluster(p.host(), p.port());
    }

    public TenantPlacement placementOf(String tenantKey) {
        return current.get(tenantKey);
    }

    /** True while the tenant's writes are paused for a move or a restore. */
    public boolean inMaintenance(String tenantKey) {
        TenantPlacement p = current.get(tenantKey);
        return p != null && p.inMaintenance();
    }

    /** Re-reads the map; reports every tenant whose placement or write state changed. */
    public synchronized void refresh() {
        Map<String, TenantPlacement> latest = registry.placements();
        if (latest.isEmpty()) {
            return;   // map unreadable: keep routing as we were rather than send everyone home
        }
        for (TenantPlacement p : latest.values()) {
            TenantPlacement before = current.put(p.tenantKey(), p);
            if (before != null && !before.sameAs(p)) {
                log.info("Tenant '{}' now on {} ({}:{}), placement epoch {}, {}", p.tenantKey(), p.clusterId(), p.host(),
                        p.port(), p.epoch(), p.status());
                clusters.forCluster(p.host(), p.port());   // open the pool before saying we are ready
                if (onMoved != null) {
                    onMoved.accept(p);
                }
            }
        }
    }

    private void refreshQuietly() {
        try {
            refresh();
        } catch (RuntimeException e) {
            log.warn("Placement refresh failed", e);
        }
    }

    @Override
    public void close() {
        if (timer != null) {
            timer.shutdownNow();
        }
    }
}
