package com.civileng.marketplace.tenant.common;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One connection pool per MySQL cluster (architecture 02 §6.2, "the routing step"). The service's
 * own datasource is the pool for the cluster its URL names; a tenant placed on any other cluster
 * gets a pool built on first use from the same URL with that cluster's host and port, and the
 * same credentials — a cluster is provisioned with the same service accounts.
 *
 * <p>Pools for other clusters are kept small: every service × cluster is a pool, and the total
 * is what MySQL's {@code max_connections} has to carry (09 §5).
 */
@Slf4j
public class ClusterDataSources implements AutoCloseable {

    private static final Pattern HOST_PORT = Pattern.compile("^(jdbc:mysql://)([^/:?]+)(?::(\\d+))?(.*)$");

    private final DataSource primary;
    private final String url;
    private final String username;
    private final String password;
    private final int poolSize;
    private final String primaryHost;
    private final int primaryPort;
    private final Map<String, HikariDataSource> others = new ConcurrentHashMap<>();

    public ClusterDataSources(DataSource primary, String url, String username, String password, int poolSize) {
        this.primary = primary;
        this.url = url;
        this.username = username;
        this.password = password;
        this.poolSize = poolSize;
        Matcher m = HOST_PORT.matcher(url == null ? "" : url);
        this.primaryHost = m.matches() ? m.group(2) : null;
        this.primaryPort = m.matches() && m.group(3) != null ? Integer.parseInt(m.group(3)) : 3306;
    }

    public DataSource primary() {
        return primary;
    }

    /** The pool for a cluster, the service's own when it is the one its URL names. */
    public DataSource forCluster(String host, int port) {
        if (host == null || (host.equalsIgnoreCase(primaryHost) && port == primaryPort)) {
            return primary;
        }
        return others.computeIfAbsent(host + ":" + port, k -> create(host, port));
    }

    /** The service's URL, pointed at another cluster. */
    static String urlFor(String url, String host, int port) {
        Matcher m = HOST_PORT.matcher(url);
        if (!m.matches()) {
            throw new IllegalStateException("Cannot route a non-MySQL datasource URL to another cluster: " + url);
        }
        return m.group(1) + host + ":" + port + m.group(4);
    }

    private HikariDataSource create(String host, int port) {
        // The no-argument form starts the pool on first use, so a cluster that is briefly
        // unreachable fails the query that needed it rather than the placement refresh.
        HikariDataSource pool = new HikariDataSource();
        pool.setJdbcUrl(urlFor(url, host, port));
        pool.setUsername(username);
        pool.setPassword(password);
        pool.setMaximumPoolSize(poolSize);
        pool.setMinimumIdle(1);
        pool.setPoolName("cluster-" + host + "-" + port);
        log.info("Pool for MySQL cluster {}:{} created", host, port);
        return pool;
    }

    @Override
    public void close() {
        others.values().forEach(HikariDataSource::close);
    }
}
