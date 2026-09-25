package com.civileng.marketplace.tenant.placement;

import com.civileng.marketplace.tenant.common.TenantPlacementAck;
import com.civileng.marketplace.tenant.common.TenantTopics;
import com.civileng.marketplace.tenant.model.Tenant;
import com.civileng.marketplace.tenant.model.TenantStatus;
import com.civileng.marketplace.tenant.placement.TenantMove.Step;
import com.civileng.marketplace.tenant.repository.TenantRepository;
import com.civileng.marketplace.tenant.service.TenantLifecycle;
import com.civileng.marketplace.tenant.service.TenantService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Moves a tenant's data to another MySQL cluster (architecture 02 §6.2, "tenant move procedure"):
 *
 * <ol>
 *   <li><b>COPY</b> every schema to the target while the tenant keeps working;</li>
 *   <li><b>FREEZE</b>: the tenant goes MAINTENANCE — reads go on, writes are refused — and the move
 *       waits until every data service has said it is refusing them, plus a short drain;</li>
 *   <li><b>SYNC</b>: re-copy the tables whose checksum changed since the bulk copy;</li>
 *   <li><b>VERIFY</b>: every table's checksum is equal on both clusters;</li>
 *   <li><b>FLIP</b> the placement (new epoch) and <b>AWAIT_ACKS</b> until every data service routes
 *       the tenant to the new cluster;</li>
 *   <li><b>RESUME</b> writes. The source copy is kept until an operator drops it.</li>
 * </ol>
 *
 * A failure before the flip resumes the tenant where it was. After the flip it stays paused and
 * FAILED for an operator to retry or roll back — resuming writes while services disagree about
 * where the data is would split it.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MoveService {

    private static final ObjectMapper JSON = new ObjectMapper();
    static final Set<Step> RUNNING = EnumSet.of(Step.COPY, Step.FREEZE, Step.SYNC, Step.VERIFY, Step.FLIP,
            Step.AWAIT_ACKS, Step.RESUME);
    private static final String SYSTEM = "placement";

    private final TenantMoveRepository moves;
    private final TenantMoveAckRepository acks;
    private final DbClusterRepository clusters;
    private final TenantRepository tenants;
    private final TenantLifecycle lifecycle;
    private final TenantService tenantService;
    private final SchemaCopier copier;
    private final PlacementProperties props;
    private final TransactionTemplate tx;
    private final Clock clock;

    public record ClusterView(String clusterId, String cell, String host, int port, String kind, String status,
                              int capacity, long tenants) { }

    public record MoveView(Long id, String tenantKey, String sourceClusterId, String targetClusterId, Step step,
                           String lastError, int schemasCopied, int tablesCopied, long rowsCopied, int tablesResynced,
                           List<String> acknowledged, List<String> waitingFor, Long freezeMillis, Map<String, Object> report,
                           String requestedBy, LocalDateTime startedAt, LocalDateTime finishedAt,
                           LocalDateTime sourceDroppedAt) { }

    public record PlacementView(String tenantKey, String clusterId, String cell, String tier, long epoch, String status,
                                MoveView currentMove) { }

    public List<ClusterView> clusters() {
        Map<String, Long> counts = new HashMap<>();
        tenants.findAll().stream().filter(t -> t.getStatus() != TenantStatus.DRAFT)
                .forEach(t -> counts.merge(t.getDbClusterId(), 1L, Long::sum));
        return clusters.findAll().stream().sorted(Comparator.comparing(DbCluster::getClusterId))
                .map(c -> new ClusterView(c.getClusterId(), c.getCell(), c.getHost(), c.getPort(), c.getKind(),
                        c.getStatus(), c.getCapacity(), counts.getOrDefault(c.getClusterId(), 0L))).toList();
    }

    public PlacementView placement(String tenantKey) {
        Tenant t = tenant(tenantKey);
        DbCluster c = clusters.findById(t.getDbClusterId()).orElseThrow();
        MoveView current = moves.findByTenantKeyOrderByIdDesc(tenantKey).stream().findFirst().map(this::view).orElse(null);
        return new PlacementView(tenantKey, c.getClusterId(), c.getCell(), t.getTier(), t.getPlacementEpoch(),
                t.getStatus().name(), current);
    }

    public List<MoveView> history(String tenantKey) {
        return moves.findByTenantKeyOrderByIdDesc(tenantKey).stream().map(this::view).toList();
    }

    public MoveView start(String tenantKey, String targetClusterId, String actor) {
        return tx.execute(s -> {
            Tenant t = tenant(tenantKey);
            if ("platform".equals(tenantKey)) {
                throw new IllegalArgumentException("The operator tenant stays on the control-plane cluster");
            }
            if (t.getStatus() != TenantStatus.ACTIVE) {
                throw new IllegalStateException("Only an ACTIVE tenant can be moved; this one is " + t.getStatus());
            }
            DbCluster target = clusters.findById(targetClusterId)
                    .orElseThrow(() -> new NoSuchElementException("No such cluster: " + targetClusterId));
            if (target.getClusterId().equals(t.getDbClusterId())) {
                throw new IllegalArgumentException(tenantKey + " is already on " + targetClusterId);
            }
            if (!"ACTIVE".equals(target.getStatus())) {
                throw new IllegalStateException(targetClusterId + " is " + target.getStatus() + " and takes no tenants");
            }
            long there = tenants.findAll().stream().filter(x -> target.getClusterId().equals(x.getDbClusterId())
                    && x.getStatus() != TenantStatus.DRAFT).count();
            if (target.dedicated() && there > 0) {
                throw new IllegalStateException(targetClusterId + " is dedicated and already holds a tenant");
            }
            if (there >= target.getCapacity()) {
                throw new IllegalStateException(targetClusterId + " is full (" + target.getCapacity() + " tenants)");
            }
            if (moves.findByTenantKeyOrderByIdDesc(tenantKey).stream().anyMatch(m -> RUNNING.contains(m.getStep())
                    || m.getStep() == Step.FAILED && TenantStatus.MAINTENANCE == t.getStatus())) {
                throw new IllegalStateException(tenantKey + " is already being moved");
            }
            TenantMove m = new TenantMove();
            m.setTenantKey(tenantKey);
            m.setSourceClusterId(t.getDbClusterId());
            m.setTargetClusterId(targetClusterId);
            m.setStep(Step.COPY);
            m.setRequestedBy(actor);
            m.setStartedAt(LocalDateTime.now(clock));
            moves.save(m);
            log.info("Move of '{}' from {} to {} requested by {}", tenantKey, m.getSourceClusterId(), targetClusterId, actor);
            return view(m);
        });
    }

    /** A service reports how it now treats a tenant. Kept only while a move of that tenant waits on it. */
    @KafkaListener(topics = TenantTopics.TENANT_PLACEMENT_ACKS, groupId = "tenant-service-placement",
            containerFactory = "provisioningAckContainerFactory")
    public void onAck(String payload) {
        try {
            TenantPlacementAck ack = JSON.readValue(payload, TenantPlacementAck.class);
            tx.executeWithoutResult(s -> moves.findByTenantKeyOrderByIdDesc(ack.tenantKey()).stream()
                    .filter(m -> m.getStep() == Step.FREEZE || m.getStep() == Step.AWAIT_ACKS)
                    .findFirst()
                    .ifPresent(m -> {
                        boolean frozen = m.getStep() == Step.FREEZE && "MAINTENANCE".equals(ack.status());
                        boolean routed = m.getStep() == Step.AWAIT_ACKS && m.getTargetEpoch() != null
                                && ack.epoch() == m.getTargetEpoch() && m.getTargetClusterId().equals(ack.clusterId());
                        if (frozen || routed) {
                            acks.save(new TenantMoveAck(new TenantMoveAck.Key(m.getId(), ack.service()), ack.epoch(),
                                    LocalDateTime.now(clock)));
                        }
                    }));
        } catch (Exception e) {
            log.warn("Unreadable placement ack: {}", payload, e);
        }
    }

    @Scheduled(fixedDelayString = "${platform.placement.tick-ms:2000}", initialDelay = 10_000)
    public void tick() {
        for (TenantMove m : moves.findByStepIn(RUNNING)) {
            try {
                advance(m.getId());
            } catch (RuntimeException e) {
                log.error("Move {} of '{}' failed unexpectedly", m.getId(), m.getTenantKey(), e);
            }
        }
    }

    void advance(Long moveId) {
        TenantMove m = moves.findById(moveId).orElseThrow();
        try {
            switch (m.getStep()) {
                case COPY -> copy(m);
                case FREEZE -> freeze(m);
                case SYNC -> sync(m);
                case VERIFY -> verify(m);
                case FLIP -> flip(m);
                case AWAIT_ACKS -> awaitAcks(m);
                case RESUME -> resume(m);
                default -> { }
            }
        } catch (Exception e) {
            fail(m, m.getStep() + ": " + e.getMessage());
        }
    }

    /** Bulk copy while the tenant keeps working. Long: runs outside any transaction. */
    private void copy(TenantMove m) throws Exception {
        SchemaCopier.Endpoint source = endpoint(m.getSourceClusterId());
        SchemaCopier.Endpoint target = endpoint(m.getTargetClusterId());
        List<String> schemas = copier.schemasOf(source, m.getTenantKey());
        if (schemas.isEmpty()) {
            throw new IllegalStateException("No schemas for " + m.getTenantKey() + " on " + m.getSourceClusterId());
        }
        SchemaCopier.CopyStats total = new SchemaCopier.CopyStats(0, 0);
        for (String schema : schemas) {
            total = total.plus(copier.copySchema(source, target, schema));
        }
        m.setSchemasCopied(schemas.size());
        m.setTablesCopied(total.tables());
        m.setRowsCopied(total.rows());
        log.info("Move {}: copied {} schemas, {} tables, {} rows of '{}' to {}", m.getId(), schemas.size(),
                total.tables(), total.rows(), m.getTenantKey(), m.getTargetClusterId());
        tx.executeWithoutResult(s -> {
            Tenant t = tenant(m.getTenantKey());
            lifecycle.transition(t, TenantStatus.MAINTENANCE, SYSTEM, "Moving to " + m.getTargetClusterId());
            tenantService.announce(t, false);
            m.setFrozenAt(LocalDateTime.now(clock));
            m.setStep(Step.FREEZE);
            moves.save(m);
        });
    }

    /** Writes are paused once every data service says so, and in-flight requests have drained. */
    private void freeze(TenantMove m) {
        Set<String> waiting = waitingFor(m);
        if (!waiting.isEmpty()) {
            if (elapsed(m.getFrozenAt()).compareTo(props.ackTimeout()) > 0) {
                fail(m, "Writes not confirmed paused by: " + String.join(", ", waiting));
            }
            return;
        }
        if (elapsed(m.getFrozenAt()).compareTo(props.drain()) < 0) {
            return;
        }
        tx.executeWithoutResult(s -> {
            acks.deleteAll(acks.findByIdMoveId(m.getId()));
            m.setStep(Step.SYNC);
            moves.save(m);
        });
    }

    /** Re-copies what changed between the bulk copy and the pause. */
    private void sync(TenantMove m) throws Exception {
        SchemaCopier.Endpoint source = endpoint(m.getSourceClusterId());
        SchemaCopier.Endpoint target = endpoint(m.getTargetClusterId());
        int resynced = 0;
        for (String schema : copier.schemasOf(source, m.getTenantKey())) {
            Map<String, Long> before = copier.checksums(source, schema);
            Map<String, Long> after = copier.checksums(target, schema);
            List<String> changed = before.keySet().stream().filter(t -> !Objects.equals(before.get(t), after.get(t))).toList();
            if (!changed.isEmpty()) {
                copier.copyTables(source, target, schema, changed);
                resynced += changed.size();
            }
        }
        m.setTablesResynced(resynced);
        m.setStep(Step.VERIFY);
        moves.save(m);
    }

    /** Every table on both sides, equal checksums; nothing extra on the target. */
    private void verify(TenantMove m) throws Exception {
        SchemaCopier.Endpoint source = endpoint(m.getSourceClusterId());
        SchemaCopier.Endpoint target = endpoint(m.getTargetClusterId());
        Map<String, Object> report = new LinkedHashMap<>();
        List<String> mismatches = new ArrayList<>();
        int tables = 0;
        for (String schema : copier.schemasOf(source, m.getTenantKey())) {
            Map<String, Long> a = copier.checksums(source, schema);
            Map<String, Long> b = copier.checksums(target, schema);
            if (!a.keySet().equals(b.keySet())) {
                mismatches.add(schema + ": tables differ");
            }
            a.forEach((table, sum) -> {
                if (!Objects.equals(sum, b.get(table))) {
                    mismatches.add(schema + "." + table);
                }
            });
            tables += a.size();
        }
        report.put("tablesVerified", tables);
        report.put("mismatches", mismatches);
        m.setReport(JSON.writeValueAsString(report));
        if (!mismatches.isEmpty()) {
            throw new IllegalStateException("Checksums differ: " + String.join(", ", mismatches));
        }
        m.setStep(Step.FLIP);
        moves.save(m);
    }

    private void flip(TenantMove m) {
        tx.executeWithoutResult(s -> {
            Tenant t = tenant(m.getTenantKey());
            DbCluster target = clusters.findById(m.getTargetClusterId()).orElseThrow();
            t.setDbClusterId(target.getClusterId());
            t.setTier(target.dedicated() ? "DEDICATED_DB" : "STANDARD");
            t.setPlacementEpoch(t.getPlacementEpoch() + 1);
            tenants.save(t);
            tenantService.announcePlacement(t);
            m.setTargetEpoch(t.getPlacementEpoch());
            m.setStep(Step.AWAIT_ACKS);
            moves.save(m);
            log.info("Move {}: '{}' placed on {} (epoch {})", m.getId(), t.getTenantKey(), target.getClusterId(),
                    t.getPlacementEpoch());
        });
    }

    private void awaitAcks(TenantMove m) {
        Set<String> waiting = waitingFor(m);
        if (waiting.isEmpty()) {
            m.setStep(Step.RESUME);
            moves.save(m);
            return;
        }
        LocalDateTime since = m.getFrozenAt() == null ? m.getStartedAt() : m.getFrozenAt();
        if (elapsed(since).compareTo(props.ackTimeout().multipliedBy(2)) > 0) {
            fail(m, "Not routing to " + m.getTargetClusterId() + " yet: " + String.join(", ", waiting));
        }
    }

    private void resume(TenantMove m) {
        tx.executeWithoutResult(s -> {
            Tenant t = tenant(m.getTenantKey());
            lifecycle.transition(t, TenantStatus.ACTIVE, SYSTEM, "Moved to " + m.getTargetClusterId());
            tenantService.announce(t, false);
            m.setResumedAt(LocalDateTime.now(clock));
            m.setFinishedAt(m.getResumedAt());
            m.setStep(Step.DONE);
            moves.save(m);
            log.info("Move {} of '{}' done; writes paused for {} ms", m.getId(), t.getTenantKey(),
                    Duration.between(m.getFrozenAt(), m.getResumedAt()).toMillis());
        });
    }

    /**
     * Before the flip nothing has changed where the data is: resume on the source. After it,
     * services may be on either cluster, so the tenant stays paused for an operator.
     */
    private void fail(TenantMove m, String error) {
        tx.executeWithoutResult(s -> {
            TenantMove fresh = moves.findById(m.getId()).orElseThrow();
            boolean flipped = fresh.getTargetEpoch() != null;
            fresh.setLastError(error.length() > 1000 ? error.substring(0, 1000) : error);
            fresh.setStep(Step.FAILED);
            fresh.setFinishedAt(LocalDateTime.now(clock));
            if (!flipped) {
                Tenant t = tenant(fresh.getTenantKey());
                if (t.getStatus() == TenantStatus.MAINTENANCE) {
                    lifecycle.transition(t, TenantStatus.ACTIVE, SYSTEM, "Move failed; staying on " + fresh.getSourceClusterId());
                    tenantService.announce(t, false);
                }
            }
            moves.save(fresh);
            log.error("Move {} of '{}' failed: {}", fresh.getId(), fresh.getTenantKey(), error);
        });
    }

    /**
     * After a failure once the placement flipped: point the tenant back at the source cluster
     * (its data there is exactly as it was when writes were paused) and resume.
     */
    public MoveView rollback(String tenantKey, Long moveId, String actor) {
        return tx.execute(s -> {
            TenantMove m = moves.findById(moveId).filter(x -> x.getTenantKey().equals(tenantKey))
                    .orElseThrow(() -> new NoSuchElementException("No such move"));
            if (m.getStep() != Step.FAILED || m.getTargetEpoch() == null) {
                throw new IllegalStateException("Only a move that failed after its flip needs rolling back");
            }
            Tenant t = tenant(tenantKey);
            t.setDbClusterId(m.getSourceClusterId());
            t.setTier(clusters.findById(m.getSourceClusterId()).map(DbCluster::dedicated).orElse(false) ? "DEDICATED_DB" : "STANDARD");
            t.setPlacementEpoch(t.getPlacementEpoch() + 1);
            tenants.save(t);
            if (t.getStatus() == TenantStatus.MAINTENANCE) {
                lifecycle.transition(t, TenantStatus.ACTIVE, actor, "Move rolled back to " + m.getSourceClusterId());
            }
            tenantService.announcePlacement(t);
            m.setStep(Step.ROLLED_BACK);
            moves.save(m);
            return view(m);
        });
    }

    /**
     * Pauses (or resumes) a tenant's writes by hand, for an operator restoring its data (the
     * restore runbook): the same MAINTENANCE a move uses, without a move.
     */
    public PlacementView maintenance(String tenantKey, boolean on, String reason, String actor) {
        return tx.execute(s -> {
            Tenant t = tenant(tenantKey);
            if (moves.findByTenantKeyOrderByIdDesc(tenantKey).stream().anyMatch(m -> RUNNING.contains(m.getStep()))) {
                throw new IllegalStateException(tenantKey + " is being moved; its writes are managed by the move");
            }
            TenantStatus to = on ? TenantStatus.MAINTENANCE : TenantStatus.ACTIVE;
            if (t.getStatus() != to) {
                if (t.getStatus() != (on ? TenantStatus.ACTIVE : TenantStatus.MAINTENANCE)) {
                    throw new IllegalStateException("A " + t.getStatus() + " tenant cannot be " + (on ? "paused" : "resumed"));
                }
                lifecycle.transition(t, to, actor, reason == null || reason.isBlank() ? "Operator maintenance" : reason);
                tenantService.announce(t, false);
            }
            return placement(tenantKey);
        });
    }

    /** After a successful move, the old copy is only a backup; an operator drops it when satisfied. */
    public MoveView dropSource(String tenantKey, Long moveId, String actor) throws Exception {
        TenantMove m = moves.findById(moveId).filter(x -> x.getTenantKey().equals(tenantKey))
                .orElseThrow(() -> new NoSuchElementException("No such move"));
        Tenant t = tenant(tenantKey);
        if (m.getStep() != Step.DONE || m.getSourceDroppedAt() != null || m.getSourceClusterId().equals(t.getDbClusterId())) {
            throw new IllegalStateException("Only the source of a finished move the tenant has not returned to can be dropped");
        }
        SchemaCopier.Endpoint source = endpoint(m.getSourceClusterId());
        for (String schema : copier.schemasOf(source, tenantKey)) {
            copier.dropSchema(source, schema);
        }
        m.setSourceDroppedAt(LocalDateTime.now(clock));
        moves.save(m);
        log.info("Move {}: source schemas of '{}' on {} dropped by {}", m.getId(), tenantKey, m.getSourceClusterId(), actor);
        return view(m);
    }

    private Set<String> waitingFor(TenantMove m) {
        Set<String> waiting = new TreeSet<>(props.schemaPrefixes().values());
        acks.findByIdMoveId(m.getId()).forEach(a -> waiting.remove(a.getId().getService()));
        return waiting;
    }

    private Duration elapsed(LocalDateTime since) {
        return Duration.between(since, LocalDateTime.now(clock));
    }

    private SchemaCopier.Endpoint endpoint(String clusterId) {
        DbCluster c = clusters.findById(clusterId).orElseThrow(() -> new NoSuchElementException("No such cluster " + clusterId));
        return new SchemaCopier.Endpoint(c.getHost(), c.getPort());
    }

    private Tenant tenant(String key) {
        return tenants.findByTenantKey(key).orElseThrow(() -> new NoSuchElementException("No tenant " + key));
    }

    @SuppressWarnings("unchecked")
    private MoveView view(TenantMove m) {
        List<String> acked = acks.findByIdMoveId(m.getId()).stream().map(a -> a.getId().getService()).sorted().toList();
        List<String> waiting = (m.getStep() == Step.FREEZE || m.getStep() == Step.AWAIT_ACKS) ? List.copyOf(waitingFor(m)) : List.of();
        Map<String, Object> report = null;
        try {
            report = m.getReport() == null ? null : JSON.readValue(m.getReport(), Map.class);
        } catch (Exception ignored) {
            // an unreadable report is shown as none
        }
        Long freeze = m.getFrozenAt() == null ? null
                : Duration.between(m.getFrozenAt(), m.getResumedAt() != null ? m.getResumedAt() : LocalDateTime.now(clock)).toMillis();
        return new MoveView(m.getId(), m.getTenantKey(), m.getSourceClusterId(), m.getTargetClusterId(), m.getStep(),
                m.getLastError(), m.getSchemasCopied(), m.getTablesCopied(), m.getRowsCopied(), m.getTablesResynced(),
                acked, waiting, freeze, report, m.getRequestedBy(), m.getStartedAt(), m.getFinishedAt(), m.getSourceDroppedAt());
    }
}
