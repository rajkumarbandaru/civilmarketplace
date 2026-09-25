package com.civileng.marketplace.tenant.placement;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.*;

/**
 * Copies a tenant's schemas from one MySQL cluster to another over JDBC: each table's exact
 * definition ({@code SHOW CREATE TABLE}, so indexes, keys and the AUTO_INCREMENT counter come
 * along), then its rows, streamed and inserted in batches. {@code CHECKSUM TABLE} on both sides is
 * what says a copy is faithful.
 *
 * <p>Schema and table names reach SQL only after {@link #safe} — they come from the service
 * prefixes and a validated tenant key, and from information_schema, never from a request.
 */
@Slf4j
@Component
public class SchemaCopier {

    public record Endpoint(String host, int port) { }

    public record CopyStats(int tables, long rows) {
        CopyStats plus(CopyStats o) {
            return new CopyStats(tables + o.tables, rows + o.rows);
        }
    }

    private final PlacementProperties props;
    private final String username;
    private final String password;

    public SchemaCopier(PlacementProperties props,
                        @org.springframework.beans.factory.annotation.Value("${spring.datasource.username:}") String dsUser,
                        @org.springframework.beans.factory.annotation.Value("${spring.datasource.password:}") String dsPassword) {
        this.props = props;
        this.username = props.username() == null || props.username().isBlank() ? dsUser : props.username();
        this.password = props.password() == null || props.password().isBlank() ? dsPassword : props.password();
    }

    public Connection open(Endpoint e) throws SQLException {
        return DriverManager.getConnection("jdbc:mysql://" + e.host() + ":" + e.port() + "/?" + props.jdbcOptions(),
                username, password);
    }

    /** The tenant's schemas that exist on a cluster: {@code <prefix>_<tenantKey>} for every data service. */
    public List<String> schemasOf(Endpoint e, String tenantKey) throws SQLException {
        Set<String> wanted = new LinkedHashSet<>();
        for (String prefix : props.schemaPrefixes().keySet()) {
            wanted.add(safe(prefix + "_" + tenantKey));
        }
        List<String> found = new ArrayList<>();
        try (Connection c = open(e); ResultSet rs = c.getMetaData().getCatalogs()) {
            while (rs.next()) {
                if (wanted.contains(rs.getString(1))) {
                    found.add(rs.getString(1));
                }
            }
        }
        found.sort(Comparator.naturalOrder());
        return found;
    }

    /** Copies the whole schema, replacing whatever the target had under that name. */
    public CopyStats copySchema(Endpoint from, Endpoint to, String schema) throws SQLException {
        String s = safe(schema);
        try (Connection target = open(to); Statement st = target.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS `" + s + "`");
            st.execute("CREATE DATABASE `" + s + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        return copyTables(from, to, s, tables(from, s));
    }

    /** Re-copies only these tables (after writes were paused, the ones that changed since the bulk copy). */
    public CopyStats copyTables(Endpoint from, Endpoint to, String schema, Collection<String> tables) throws SQLException {
        String s = safe(schema);
        CopyStats stats = new CopyStats(0, 0);
        try (Connection source = open(from); Connection target = open(to)) {
            try (Statement st = target.createStatement()) {
                st.execute("CREATE DATABASE IF NOT EXISTS `" + s + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                st.execute("SET FOREIGN_KEY_CHECKS = 0");
                st.execute("SET UNIQUE_CHECKS = 0");
                st.execute("USE `" + s + "`");
            }
            for (String table : tables) {
                stats = stats.plus(new CopyStats(1, copyTable(source, target, s, safe(table))));
            }
            try (Statement st = target.createStatement()) {
                st.execute("SET FOREIGN_KEY_CHECKS = 1");
                st.execute("SET UNIQUE_CHECKS = 1");
            }
        }
        return stats;
    }

    private long copyTable(Connection source, Connection target, String schema, String table) throws SQLException {
        String create;
        try (Statement st = source.createStatement();
             ResultSet rs = st.executeQuery("SHOW CREATE TABLE `" + schema + "`.`" + table + "`")) {
            rs.next();
            create = rs.getString(2);
        }
        try (Statement st = target.createStatement()) {
            st.execute("DROP TABLE IF EXISTS `" + table + "`");
            st.execute(create);
        }
        List<String> columns = insertableColumns(source, schema, table);
        String cols = String.join(", ", columns.stream().map(c -> "`" + c + "`").toList());
        String marks = String.join(", ", Collections.nCopies(columns.size(), "?"));
        long rows = 0;
        boolean autoCommit = target.getAutoCommit();
        target.setAutoCommit(false);
        try (Statement read = source.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
             PreparedStatement write = target.prepareStatement(
                     "INSERT INTO `" + table + "` (" + cols + ") VALUES (" + marks + ")")) {
            read.setFetchSize(Integer.MIN_VALUE);   // Connector/J: stream rows instead of buffering the table
            try (ResultSet rs = read.executeQuery("SELECT " + cols + " FROM `" + schema + "`.`" + table + "`")) {
                int pending = 0;
                while (rs.next()) {
                    for (int i = 1; i <= columns.size(); i++) {
                        write.setObject(i, rs.getObject(i));
                    }
                    write.addBatch();
                    rows++;
                    if (++pending == props.batchSize()) {
                        write.executeBatch();
                        pending = 0;
                    }
                }
                if (pending > 0) {
                    write.executeBatch();
                }
            }
            target.commit();
        } catch (SQLException e) {
            target.rollback();
            throw e;
        } finally {
            target.setAutoCommit(autoCommit);
        }
        return rows;
    }

    /** Base tables of a schema, in name order. Views and routines are not used by any service. */
    public List<String> tables(Endpoint e, String schema) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (Connection c = open(e);
             PreparedStatement ps = c.prepareStatement("SELECT table_name FROM information_schema.tables "
                     + "WHERE table_schema = ? AND table_type = 'BASE TABLE' ORDER BY table_name")) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tables.add(rs.getString(1));
                }
            }
        }
        return tables;
    }

    /** Table → CHECKSUM TABLE value (null for a missing table). Equal on both sides = same rows. */
    public Map<String, Long> checksums(Endpoint e, String schema) throws SQLException {
        String s = safe(schema);
        Map<String, Long> sums = new TreeMap<>();
        List<String> tables = tables(e, s);
        if (tables.isEmpty()) {
            return sums;
        }
        try (Connection c = open(e); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("CHECKSUM TABLE " + String.join(", ",
                     tables.stream().map(t -> "`" + s + "`.`" + safe(t) + "`").toList()))) {
            while (rs.next()) {
                String qualified = rs.getString(1);
                long value = rs.getLong(2);
                sums.put(qualified.substring(qualified.indexOf('.') + 1), rs.wasNull() ? null : value);
            }
        }
        return sums;
    }

    public long rowCount(Endpoint e, String schema, String table) throws SQLException {
        try (Connection c = open(e); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM `" + safe(schema) + "`.`" + safe(table) + "`")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    public void dropSchema(Endpoint e, String schema) throws SQLException {
        try (Connection c = open(e); Statement st = c.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS `" + safe(schema) + "`");
        }
    }

    private static List<String> insertableColumns(Connection c, String schema, String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT column_name, extra FROM information_schema.columns "
                + "WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position")) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (insertable(rs.getString(2))) {
                        columns.add(safe(rs.getString(1)));
                    }
                }
            }
        }
        return columns;
    }

    /**
     * A generated column ({@code VIRTUAL GENERATED} / {@code STORED GENERATED}) is recomputed by the
     * target and cannot be inserted. {@code DEFAULT_GENERATED} is different: it only says the
     * column has an expression default such as {@code CURRENT_TIMESTAMP}, and its stored value must
     * be copied — skipping it re-stamped every such timestamp with the time of the copy.
     */
    static boolean insertable(String extra) {
        String e = extra == null ? "" : extra.toUpperCase();
        return !e.contains("VIRTUAL GENERATED") && !e.contains("STORED GENERATED");
    }

    /** Identifiers only: letters, digits, underscore and hyphen (flyway_schema_history is the widest). */
    static String safe(String identifier) {
        if (identifier == null || !identifier.matches("[A-Za-z0-9_\\-]{1,64}")) {
            throw new IllegalArgumentException("Refusing unsafe SQL identifier: " + identifier);
        }
        return identifier;
    }
}
