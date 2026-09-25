package com.civileng.marketplace.analytics.cdc;

import com.civileng.marketplace.analytics.warehouse.Projector;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One capture per MySQL cluster in the placement map (tenant-service's db_clusters): every cluster
 * a tenant can be placed on is followed, so a tenant keeps feeding the warehouse wherever it moves.
 * Clusters added later are picked up within a minute.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CaptureManager {

    private final CdcProperties props;
    private final Projector projector;
    private final JdbcTemplate warehouse;
    private final Map<String, ClusterCapture> captures = new ConcurrentHashMap<>();

    @Value("${platform.tenant.registry.url}")
    private String registryUrl;
    @Value("${platform.tenant.registry.username}")
    private String registryUser;
    @Value("${platform.tenant.registry.password}")
    private String registryPassword;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (props.enabled()) {
            syncClusters();
        }
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void syncClusters() {
        if (!props.enabled()) {
            return;
        }
        try (Connection c = DriverManager.getConnection(registryUrl, registryUser, registryPassword);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT cluster_id, host, port FROM db_clusters WHERE status <> 'RETIRED' ORDER BY cluster_id")) {
            int index = 0;
            while (rs.next()) {
                String id = rs.getString(1);
                long serverId = props.serverIdBase() + index++;
                if (captures.containsKey(id)) continue;
                ClusterCapture capture = new ClusterCapture(id, rs.getString(2), rs.getInt(3), serverId, props, projector, warehouse);
                try {
                    capture.start();
                    captures.put(id, capture);
                    log.info("CDC following {} ({}:{})", id, rs.getString(2), rs.getInt(3));
                } catch (Exception e) {
                    log.error("CDC could not start for {}: {}", id, e.getMessage());
                }
            }
        } catch (SQLException e) {
            log.error("Could not read the placement map: {}", e.getMessage());
        }
    }

    public List<ClusterCapture.Status> status() {
        return captures.values().stream().map(ClusterCapture::status)
                .sorted(Comparator.comparing(ClusterCapture.Status::clusterId)).toList();
    }

    @PreDestroy
    public void stop() {
        captures.values().forEach(c -> {
            try {
                c.stop();
            } catch (Exception ignored) {
                // shutting down
            }
        });
    }
}
