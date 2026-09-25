package com.civileng.marketplace.analytics.cdc;

import com.civileng.marketplace.analytics.cdc.TrackedTables.Match;
import com.civileng.marketplace.analytics.warehouse.Projector;
import com.github.shyiko.mysql.binlog.BinaryLogClient;
import com.github.shyiko.mysql.binlog.event.*;
import com.github.shyiko.mysql.binlog.event.deserialization.EventDeserializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.Serializable;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Change-data capture from one MySQL cluster (architecture 02 §8, "CDC to warehouse"): follows its
 * binlog as a replica would, turns each committed change to a tracked table into a warehouse fact,
 * and saves where it got to after every transaction — a restart resumes exactly there. The first
 * time, it snapshots the tracked tables first (from a recorded position, so nothing between the
 * snapshot and the binlog is missed; replays are idempotent upserts).
 */
@Slf4j
public class ClusterCapture {

    private final String clusterId;
    private final String host;
    private final int port;
    private final long serverId;
    private final CdcProperties props;
    private final Projector projector;
    private final JdbcTemplate warehouse;
    private final Map<Long, Match> tables = new ConcurrentHashMap<>();
    private final Map<String, List<String>> columns = new ConcurrentHashMap<>();
    private final AtomicLong events = new AtomicLong();
    private volatile Instant lastEventAt;
    private volatile BinaryLogClient client;

    public ClusterCapture(String clusterId, String host, int port, long serverId, CdcProperties props, Projector projector,
                          JdbcTemplate warehouse) {
        this.clusterId = clusterId;
        this.host = host;
        this.port = port;
        this.serverId = serverId;
        this.props = props;
        this.projector = projector;
        this.warehouse = warehouse;
    }

    public record Status(String clusterId, String host, boolean connected, String binlogFile, long binlogPosition,
                         long events, Instant lastEventAt) { }

    public Status status() {
        BinaryLogClient c = client;
        return new Status(clusterId, host, c != null && c.isConnected(), c == null ? null : c.getBinlogFilename(),
                c == null ? 0 : c.getBinlogPosition(), events.get(), lastEventAt);
    }

    public void start() throws Exception {
        List<Map<String, Object>> saved = warehouse.queryForList(
                "SELECT binlog_file, binlog_pos FROM cdc_offsets WHERE cluster_id = ?", clusterId);
        String file;
        long pos;
        if (saved.isEmpty()) {
            String[] at = currentPosition();
            file = at[0];
            pos = Long.parseLong(at[1]);
            long rows = snapshot();
            saveOffset(file, pos);
            log.info("CDC {}: snapshot of {} rows at {}:{}", clusterId, rows, file, pos);
        } else {
            file = (String) saved.get(0).get("binlog_file");
            pos = ((Number) saved.get(0).get("binlog_pos")).longValue();
            log.info("CDC {}: resuming at {}:{}", clusterId, file, pos);
        }
        BinaryLogClient c = new BinaryLogClient(host, port, props.username(), props.password());
        c.setServerId(serverId);
        c.setBinlogFilename(file);
        c.setBinlogPosition(pos);
        c.setKeepAlive(true);
        EventDeserializer d = new EventDeserializer();
        d.setCompatibilityMode(EventDeserializer.CompatibilityMode.DATE_AND_TIME_AS_LONG,
                EventDeserializer.CompatibilityMode.CHAR_AND_BINARY_AS_BYTE_ARRAY);
        c.setEventDeserializer(d);
        c.registerEventListener(this::onEvent);
        c.registerLifecycleListener(new BinaryLogClient.AbstractLifecycleListener() {
            @Override
            public void onCommunicationFailure(BinaryLogClient client, Exception ex) {
                log.warn("CDC {}: {}", clusterId, ex.getMessage());
            }
        });
        client = c;
        c.connect(TimeUnit.SECONDS.toMillis(10));   // then follows on its own thread, reconnecting as needed
    }

    public void stop() throws Exception {
        if (client != null) {
            client.disconnect();
        }
    }

    public void onEvent(Event event) {
        EventType type = event.getHeader().getEventType();
        EventData data = event.getData();
        try {
            if (data instanceof TableMapEventData t) {
                TrackedTables.match(t.getDatabase(), t.getTable()).ifPresentOrElse(
                        m -> {
                            tables.put(t.getTableId(), m);
                            columns.computeIfAbsent(t.getDatabase() + "." + t.getTable(), k -> columnNames(t.getDatabase(), t.getTable()));
                            schemaOf.put(t.getTableId(), t.getDatabase() + "." + t.getTable());
                        },
                        () -> tables.remove(t.getTableId()));
            } else if (data instanceof WriteRowsEventData w) {
                apply(w.getTableId(), w.getRows(), false);
            } else if (data instanceof UpdateRowsEventData u) {
                apply(u.getTableId(), u.getRows().stream().map(Map.Entry::getValue).toList(), false);
            } else if (data instanceof DeleteRowsEventData del) {
                apply(del.getTableId(), del.getRows(), true);
            } else if (data instanceof QueryEventData q && q.getSql() != null
                    && q.getSql().toUpperCase().matches("(?s)\\s*(ALTER|CREATE|DROP|RENAME)\\s+TABLE.*")) {
                columns.clear();   // a table's shape changed: re-read column names on next use
            } else if (type == EventType.XID && client != null) {
                saveOffset(client.getBinlogFilename(), client.getBinlogPosition());
            }
        } catch (RuntimeException e) {
            log.error("CDC {}: could not apply {} event", clusterId, type, e);
        }
    }

    private final Map<Long, String> schemaOf = new ConcurrentHashMap<>();

    private void apply(long tableId, List<Serializable[]> rows, boolean deleted) {
        Match m = tables.get(tableId);
        if (m == null) {
            return;
        }
        List<String> names = columns.computeIfAbsent(schemaOf.get(tableId), k -> {
            String[] parts = k.split("\\.");
            return columnNames(parts[0], parts[1]);
        });
        for (Serializable[] values : rows) {
            Map<String, Object> row = new HashMap<>();
            for (int i = 0; i < values.length && i < names.size(); i++) {
                row.put(names.get(i), values[i]);
            }
            if (deleted) {
                projector.delete(m.fact(), m.tenantKey(), row);
            } else {
                projector.upsert(m.fact(), m.tenantKey(), row, clusterId);
            }
            events.incrementAndGet();
            lastEventAt = Instant.now();
        }
    }

    /** Every tracked table of every tenant on the cluster, read in full. */
    long snapshot() throws SQLException {
        long n = 0;
        try (Connection c = open()) {
            List<String> schemas = new ArrayList<>();
            try (ResultSet rs = c.getMetaData().getCatalogs()) {
                while (rs.next()) schemas.add(rs.getString(1));
            }
            for (String schema : schemas) {
                for (TrackedTables.Source s : TrackedTables.BY_PREFIX.values()) {
                    Optional<Match> m = TrackedTables.match(schema, s.table());
                    if (m.isEmpty()) continue;
                    try (Statement st = c.createStatement();
                         ResultSet rs = st.executeQuery("SELECT * FROM `" + schema + "`.`" + s.table() + "`")) {
                        ResultSetMetaData md = rs.getMetaData();
                        while (rs.next()) {
                            Map<String, Object> row = new HashMap<>();
                            for (int i = 1; i <= md.getColumnCount(); i++) {
                                row.put(md.getColumnLabel(i), rs.getObject(i));
                            }
                            projector.upsert(m.get().fact(), m.get().tenantKey(), row, clusterId);
                            n++;
                        }
                    } catch (SQLSyntaxErrorException missing) {
                        // schema exists without the table yet (a tenant being provisioned)
                    }
                }
            }
        }
        return n;
    }

    private String[] currentPosition() throws SQLException {
        try (Connection c = open(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SHOW MASTER STATUS")) {
            if (!rs.next()) {
                throw new IllegalStateException("Binary logging is off on " + clusterId + ": nothing to capture");
            }
            return new String[]{rs.getString(1), rs.getString(2)};
        }
    }

    /** Column names in ordinal order: binlog rows carry values by position only. Overridable in tests. */
    protected List<String> columnNames(String schema, String table) {
        List<String> names = new ArrayList<>();
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement("SELECT column_name FROM information_schema.columns "
                     + "WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position")) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) names.add(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot read the columns of " + schema + "." + table, e);
        }
        return names;
    }

    private void saveOffset(String file, long pos) {
        warehouse.update("INSERT INTO cdc_offsets (cluster_id, binlog_file, binlog_pos, events, last_event_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, NOW(3)) ON DUPLICATE KEY UPDATE binlog_file = VALUES(binlog_file), "
                        + "binlog_pos = VALUES(binlog_pos), events = VALUES(events), last_event_at = VALUES(last_event_at), "
                        + "updated_at = NOW(3)",
                clusterId, file, pos, events.get(), lastEventAt == null ? null : Timestamp.from(lastEventAt));
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection("jdbc:mysql://" + host + ":" + port
                + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", props.username(), props.password());
    }
}
